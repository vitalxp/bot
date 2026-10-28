package app.taxicom.commandos.capture

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.taxicom.commandos.TaxiCommandosApp
import app.taxicom.core.model.Platform

/**
 * Reacts to "new order" notifications ~0.5–1 s before the card appears, so the Accessibility
 * service can poll aggressively for a few seconds instead of waiting for a window event.
 */
class OrderNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val platform = Platform.fromPackage(sbn.packageName) ?: return
        (application as? TaxiCommandosApp)?.graph?.capture?.onNewOrderNotification(platform)
    }
}
