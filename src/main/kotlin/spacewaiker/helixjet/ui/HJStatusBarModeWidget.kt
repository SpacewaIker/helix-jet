package spacewaiker.helixjet.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJState
import java.awt.Component

class HJStatusBarModeWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    companion object {
        const val ID = "HelixJetModeWidget"
    }

    private var statusBar: StatusBar? = null

    override fun ID() = ID
    override fun getPresentation() = this
    override fun getTooltipText() = "HelixJet editor mode"
    override fun getAlignment() = Component.LEFT_ALIGNMENT

    override fun getText(): String {
        return when (HJState.mode) {
            HJEditorMode.NORMAL -> "NOR"
            HJEditorMode.INSERT -> "INS"
            HJEditorMode.SELECT -> "SEL"
        }
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }
}