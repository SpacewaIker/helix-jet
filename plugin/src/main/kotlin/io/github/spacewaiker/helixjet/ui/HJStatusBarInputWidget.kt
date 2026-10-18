package io.github.spacewaiker.helixjet.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import io.github.spacewaiker.helixjet.editor.HJState
import io.github.spacewaiker.helixjet.input.HJInputHandler
import java.awt.Component

@Service(Service.Level.PROJECT)
class HJStatusBarInputWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    companion object {
        const val ID = "HelixJetInputWidget"

        fun getInstance(project: Project): HJStatusBarInputWidget =
            project.getService(HJStatusBarInputWidget::class.java)
    }

    private var statusBar: StatusBar? = null

    override fun ID() = ID
    override fun getPresentation() = this
    override fun getTooltipText() = "HelixJet input indicator"
    override fun getAlignment() = Component.LEFT_ALIGNMENT

    override fun getText(): String {
        val hjState = HJState.getInstance(project)
        val inputHandler = HJInputHandler.getInstance(project)
        return inputHandler.actionCount + (inputHandler.pendingActionKey ?: "") +
                (hjState.selectedRegister?.let { "\treg=$it" } ?: "")
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    fun updateInputWidget() {
        project.let {
            WindowManager.getInstance().getStatusBar(it)?.updateWidget(ID)
        }
    }
}