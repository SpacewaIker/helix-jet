package io.github.spacewaiker.helixjet.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class HJStatusBarFileTypeWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = HJStatusBarFileTypeWidget.ID
    override fun getDisplayName() = "HelixJet File Type"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project) = HJStatusBarFileTypeWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar) = true
}