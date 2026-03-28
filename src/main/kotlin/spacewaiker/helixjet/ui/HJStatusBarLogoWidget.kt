package spacewaiker.helixjet.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.naturalSorted
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import com.intellij.vcsUtil.showAbove
import spacewaiker.helixjet.editor.HJState
import java.awt.event.MouseEvent
import javax.swing.Icon

class HJStatusBarLogoWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {
    companion object {
        const val ID = "HelixJetLogoWidget"
        val LOGO = IconLoader.getIcon("/icons/helix.svg", this::class.java)
    }

    private var statusBar: StatusBar? = null

    override fun ID() = ID
    override fun getPresentation() = this
    override fun getTooltipText() = "HelixJet"

    override fun getIcon(): Icon = LOGO

    override fun getClickConsumer() = Consumer<MouseEvent> { event ->
        showMenu(event)
    }

    private fun showMenu(event: MouseEvent) {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("About HelixJet Plugin") {
                override fun actionPerformed(e: AnActionEvent) {
                    Messages.showInfoMessage(
                        project, "HelixJet keybindings plugin v0.1", "About"
                    )
                }
            })
            addSeparator()
            add(object : AnAction("Print All Mappings") {
                override fun actionPerformed(e: AnActionEvent) {
                    val mapping = HJState.config.mapping.map { entry ->
                        Triple(
                            entry.key.first,
                            entry.key.second,
                            entry.value
                        )
                    }
                    mapping.naturalSorted().forEach { triple ->
                        val mode = triple.first.toString()
                        val inputKey = triple.second.toString()
                        val action = triple.third.name
                        println("[$mode] $inputKey -> $action")
                    }
                }
            })
            add(object : AnAction("Print Register Contents") {
                override fun actionPerformed(e: AnActionEvent) {
                    println("Registers contents:")
                    for ((register, contents) in HJState.allRegisterContents) {
                        println("[$register] = $contents")
                    }
                }
            })
            addSeparator()
            add(object : AnAction("Reload Config") {
                override fun actionPerformed(e: AnActionEvent) {
                    HJState.config.reloadConfiguration()
                }
            })
            add(object : AnAction("Disable HelixJet") {
                override fun actionPerformed(e: AnActionEvent) {
                    // todo: implement
                }
            })
        }

        val menu = JBPopupFactory.getInstance().createActionGroupPopup(
            "HelixJet",
            group,
            DataManager.getInstance().getDataContext(event.component),
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false
        )

        menu.showAbove(event.component)
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }
}