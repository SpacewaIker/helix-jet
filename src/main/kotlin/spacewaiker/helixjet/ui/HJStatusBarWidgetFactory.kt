package spacewaiker.helixjet.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class HJStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = HJStatusBarWidget.ID
    override fun getDisplayName() = "HelixJet Mode"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project) = HJStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar) = true
}