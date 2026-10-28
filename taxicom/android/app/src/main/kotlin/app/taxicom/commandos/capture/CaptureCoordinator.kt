package app.taxicom.commandos.capture

import app.taxicom.commandos.decision.OfferPipeline
import app.taxicom.core.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Bridges notification triggers and Accessibility card dumps into the decision pipeline. */
class CaptureCoordinator(
    private val scope: CoroutineScope,
    private val pipeline: OfferPipeline,
) {
    private val _poll = MutableSharedFlow<Platform>(extraBufferCapacity = 8)
    val pollRequests: SharedFlow<Platform> = _poll
    private val lastHash = mutableMapOf<Platform, Int>()
    private var pollJob: Job? = null

    fun onNewOrderNotification(platform: Platform) {
        pollJob?.cancel()
        pollJob = scope.launch {
            repeat(30) {
                _poll.emit(platform)
                delay(100)
            }
        }
    }

    fun onTreeLines(platform: Platform, lines: List<String>, isCard: Boolean) {
        if (!isCard) {
            if (lastHash.remove(platform) != null) pipeline.onCardGone(platform)
            return
        }
        val hash = lines.joinToString("\n").hashCode()
        if (lastHash[platform] == hash) return
        lastHash[platform] = hash
        pipeline.onCard(platform, lines)
    }

    fun clear(platform: Platform) {
        if (lastHash.remove(platform) != null) pipeline.onCardGone(platform)
    }
}
