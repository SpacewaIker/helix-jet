package spacewaiker.helixjet.ui

import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import spacewaiker.helixjet.input.HJInputHandler
import java.awt.Component

class HJStatusBarInputWidget : StatusBarWidget, StatusBarWidget.TextPresentation {
    companion object {
        const val ID = "HelixJetInputWidget"
    }

    private var statusBar: StatusBar? = null

    override fun ID() = ID
    override fun getPresentation() = this
    override fun getTooltipText() = "HelixJet input indicator"
    override fun getAlignment() = Component.LEFT_ALIGNMENT

    override fun getText(): String {
        return (HJInputHandler.minorModeName ?: "") + "   " +
                HJInputHandler.actionCount +
                (HJInputHandler.pendingActionKey ?: "")
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }
}