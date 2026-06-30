package spacewaiker.helixjet.ui

import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import spacewaiker.helixjet.actions.focusedHJEditor
import java.awt.Component
import java.util.concurrent.TimeUnit

class HJStatusBarFileTypeWidget(proj: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    init {
        project = proj
    }

    companion object {
        const val ID = "HelixJetFileTypeWidget"

        private var project: Project? = null

        fun updateFileTypeWidget() {
            project?.let {
                WindowManager.getInstance().getStatusBar(it)?.updateWidget(ID)
            }
        }
    }

    private var statusBar: StatusBar? = null

    override fun ID() = ID
    override fun getPresentation() = this

    override fun getTooltipText() = "HelixJet file type" +
            (project?.focusedHJEditor?.let { if (it.psiFile == null) " (UNSUPPORTED BY IDE)" else null } ?: "")

    override fun getAlignment() = Component.LEFT_ALIGNMENT
    override fun getText() = project?.focusedHJEditor?.let { hjEditor ->
        (hjEditor.language?.name ?: "text") + if (hjEditor.psiFile == null) "*" else ""
    } ?: ""

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar

        project!!.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    AppExecutorUtil.getAppScheduledExecutorService()
                        .schedule(::updateFileTypeWidget, 100, TimeUnit.MILLISECONDS)
                }
            }
        )

        AppExecutorUtil.getAppScheduledExecutorService().schedule(::updateFileTypeWidget, 5, TimeUnit.SECONDS)
    }

    override fun dispose() {
        statusBar = null
    }
}