package spacewaiker.helixjet.ui

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.editor.HJState
import spacewaiker.helixjet.input.HJInputHandler
import java.awt.Component

class HJStatusBarInputWidget : StatusBarWidget, StatusBarWidget.TextPresentation {
    companion object {
        const val ID = "HelixJetInputWidget"

        fun updateInputWidget() {
            EditorFactory.getInstance().editorList.first().project?.let {
                WindowManager.getInstance().getStatusBar(it)?.updateWidget(ID)
            }
        }
    }

    private var statusBar: StatusBar? = null

    override fun ID() = ID
    override fun getPresentation() = this
    override fun getTooltipText() = "HelixJet input indicator"
    override fun getAlignment() = Component.LEFT_ALIGNMENT

    override fun getText(): String {
        return HJInputHandler.actionCount +
                (HJInputHandler.pendingActionKey ?: "") +
                (HJState.selectedRegister?.let { "\treg=$it" } ?: "")
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }
}