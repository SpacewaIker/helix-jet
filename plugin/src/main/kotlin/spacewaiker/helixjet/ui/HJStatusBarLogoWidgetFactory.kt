package spacewaiker.helixjet.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class HJStatusBarLogoWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = HJStatusBarLogoWidget.ID
    override fun getDisplayName() = "HelixJet"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project) = HJStatusBarLogoWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar) = true
}