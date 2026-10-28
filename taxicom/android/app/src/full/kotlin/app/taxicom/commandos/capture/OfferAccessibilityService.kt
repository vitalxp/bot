package app.taxicom.commandos.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.taxicom.commandos.TaxiCommandosApp
import app.taxicom.commandos.decision.Action
import app.taxicom.commandos.decision.TapPerformer
import app.taxicom.core.model.Platform
import app.taxicom.core.parser.Dictionaries
import app.taxicom.core.parser.OfferParser
import app.taxicom.core.parser.normalize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Primary capture path: walks the Bolt/Uber/FREENOW window tree, finds the order card by its
 * Accept-button + price anchors, and hands the ordered text lines to the parser.
 */
class OfferAccessibilityService : AccessibilityService(), TapPerformer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val parser = OfferParser()
    private val dict = Dictionaries.DEFAULT
    private var lastScanMs = 0L
    private var acceptNode: AccessibilityNodeInfo? = null
    private var declineNode: AccessibilityNodeInfo? = null
    private var activePlatform: Platform? = null

    override fun onServiceConnected() {
        val graph = (application as TaxiCommandosApp).graph
        graph.pipeline.tapPerformer = this
        scope.launch {
            graph.capture.pollRequests.collectLatest { platform ->
                scan(platform)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val platform = Platform.fromPackage(pkg) ?: return
        val now = System.currentTimeMillis()
        if (now - lastScanMs < 100) return
        lastScanMs = now
        scan(platform)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        (application as? TaxiCommandosApp)?.graph?.pipeline?.tapPerformer = null
        super.onDestroy()
    }

    private fun scan(platform: Platform) {
        val roots = buildList {
            rootInActiveWindow?.let(::add)
            windows?.forEach { w -> w.root?.let(::add) }
        }
        if (roots.isEmpty()) {
            (application as TaxiCommandosApp).graph.capture.clear(platform)
            return
        }
        var found = false
        for (root in roots) {
            val nodes = mutableListOf<Node>()
            walk(root, 0, nodes)
            val texts = nodes.mapNotNull { it.text }.filter { it.isNotBlank() }
            if (!parser.isOrderCard(texts)) continue
            val acceptIdx = nodes.indexOfFirst { n -> n.text != null && normalize(n.text) in dict.acceptLabels && n.clickable }
            val priceIdx = nodes.indexOfFirst { n -> n.text != null && PRICE.containsMatchIn(n.text) }
            if (acceptIdx < 0 || priceIdx < 0) continue
            // Card subtree: lines between the first recognised field and the accept button, inclusive.
            val from = minOf(acceptIdx, priceIdx).coerceAtLeast(0)
            val to = maxOf(acceptIdx, priceIdx).coerceAtMost(nodes.lastIndex)
            val cardLines = nodes.subList(from, to + 1).mapNotNull { it.text }.filter { it.isNotBlank() }
            acceptNode?.recycle()
            declineNode?.recycle()
            acceptNode = nodes.getOrNull(acceptIdx)?.node?.also { it.refresh() }
            declineNode = nodes.firstOrNull { n ->
                n.text != null && (normalize(n.text) in dict.declineLabels || n.contentDesc?.let { normalize(it) } in CLOSE)
            }?.node?.also { it.refresh() }
            activePlatform = platform
            (application as TaxiCommandosApp).graph.capture.onTreeLines(platform, cardLines, isCard = true)
            found = true
            break
        }
        roots.forEach { it.recycle() }
        if (!found && activePlatform == platform) {
            (application as TaxiCommandosApp).graph.capture.clear(platform)
            activePlatform = null
        }
    }

    override fun tap(platform: Platform, action: Action): Boolean {
        if (activePlatform != platform) return false
        val node = when (action) {
            Action.ACCEPT -> acceptNode
            Action.DECLINE -> declineNode
            Action.IGNORE -> return true
        } ?: return false
        if (!node.refresh()) return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, out: MutableList<Node>) {
        if (depth > 25 || out.size > 400) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable) {
            out += Node(text ?: desc, desc, node.isClickable, AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, depth + 1, out)
            child.recycle()
        }
    }

    private data class Node(
        val text: String?,
        val contentDesc: String?,
        val clickable: Boolean,
        val node: AccessibilityNodeInfo,
    )

    private companion object {
        val PRICE = Regex("\\d{1,4}[,.]\\d{2}\\s*(zł|PLN|€|EUR)")
        val CLOSE = setOf("close", "zamknij", "закрыть")
    }
}
