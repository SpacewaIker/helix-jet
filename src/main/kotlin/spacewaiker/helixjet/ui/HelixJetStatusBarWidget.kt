package spacewaiker.helixjet.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import spacewaiker.helixjet.editor.Mode
import spacewaiker.helixjet.core.HelixJetStateService
import java.awt.Component

class HelixJetStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {
        companion object {
            const val ID = "HelixJetModeWidget"
        }

    private var statusBar: StatusBar? = null

    override fun ID() = ID
    override fun getPresentation() = this
    override fun getTooltipText() = "HelixJet editor mode"
    override fun getAlignment() = Component.LEFT_ALIGNMENT

    override fun getText(): String {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return ""
        return when (HelixJetStateService.get(editor)?.mode) {
            Mode.NORMAL -> "NOR"
            Mode.INSERT -> "INS"
            null -> ""
        }
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }
}