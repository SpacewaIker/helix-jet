package spacewaiker.helixjet.core

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.datatransfer.StringSelection
import java.net.URLEncoder


class HJErrorReporter : ErrorReportSubmitter() {
    companion object {
        private const val REPO = "spacewaiker/helix-jet"
        private const val PLUGIN_ID = "spacewaiker.helix-jet"
        private const val MAX_URL_LENGTH = 7000  // GitHub rejects URLs that are too long (~8KB)
    }

    override fun getReportActionText() = "Report to GitHub"

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>,
    ): Boolean {
        val throwable = events.firstOrNull()?.throwable ?: return false
        ApplicationManager.getApplication().invokeLater {
            val trace = sanitize(throwable.stackTraceToString())
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))
            val ide = ApplicationInfo.getInstance().let { "${it.fullApplicationName} (${it.build})" }

            fun buildUrl(traceText: String): String {
                val params = linkedMapOf(
                    "template" to "exception_report.yml",
                    "plugin_version" to (plugin?.version ?: "unknown"),
                    "ide_version" to ide,
                    "stacktrace" to traceText,
                )
                val query = params.entries.joinToString("&") { (k, v) ->
                    "$k=" + URLEncoder.encode(v, Charsets.UTF_8).replace("+", "%20")
                }
                return "https://github.com/$REPO/issues/new?$query"
            }

            // trim the trace until the URL fits
            var shown = trace
            var url = buildUrl(shown)
            while (url.length > MAX_URL_LENGTH && shown.isNotEmpty()) {
                shown = shown.take((shown.length * 0.8).toInt())
                url = buildUrl(shown)
            }
            val truncated = shown.length < trace.length

            if (truncated) {
                // keep the full trace available so the user can paste it into the form
                CopyPasteManager.getInstance().setContents(StringSelection(trace))
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("HelixJet Notifications")
                    .createNotification(
                        "The stack trace was too long for the issue form. The full trace was copied to your clipboard.",
                        NotificationType.INFORMATION
                    )
                    .notify(null)
            }

            BrowserUtil.browse(url)

            consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
        }
        return true
    }

    private fun sanitize(trace: String): String =
        trace.replace(System.getProperty("user.home"), "~")
}