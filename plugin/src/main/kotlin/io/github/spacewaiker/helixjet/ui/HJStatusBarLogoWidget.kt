package io.github.spacewaiker.helixjet.ui

import com.intellij.ide.DataManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.naturalSorted
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import com.intellij.vcsUtil.showAbove
import spacewaiker.helix.BuildConfig
import io.github.spacewaiker.helixjet.actions.HJActionContext
import io.github.spacewaiker.helixjet.actions.openFile
import io.github.spacewaiker.helixjet.config.HJConfiguration
import io.github.spacewaiker.helixjet.editor.HJState
import java.awt.event.MouseEvent
import javax.swing.Icon

@Service(Service.Level.PROJECT)
class HJStatusBarLogoWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {
    companion object {
        const val ID = "HelixJetLogoWidget"
        val LOGO = IconLoader.getIcon("/META-INF/pluginIcon.svg", this::class.java)
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
        val hjState = HJState.getInstance(project)
        val group = DefaultActionGroup().apply {
            add(object : AnAction("About HelixJet Plugin") {
                override fun actionPerformed(e: AnActionEvent) {
                    val version = PluginManagerCore.getPlugin(PluginId.getId("io.github.spacewaiker.helix-jet"))?.version ?: "?"

                    val message = "HelixJet keybindings plugin v$version\n" +
                            "\n" +
                            "<a href=\"https://github.com/spacewaiker/helix-jet/\">See Source on GitHub</a>\n" +
                            "<a href=\"https://plugins.jetbrains.com/plugin/35261-helix-jet/\">View Plugin in JetBrains Marketplace</a>"

                    Messages.showInfoMessage(project, message, "About")
                }
            })
            add(object : ToggleAction("Enabled") {
                override fun isSelected(e: AnActionEvent) = hjState.keymappingEnabled

                override fun setSelected(e: AnActionEvent, selected: Boolean) {
                    hjState.keymappingEnabled = selected
                    hjState.forAllEditors { it.reloadCaretShapes() }
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })
            if (BuildConfig.DEBUG) {
                addSeparator()
                add(object : AnAction("Print All Mappings") {
                    override fun actionPerformed(e: AnActionEvent) {
                        val mapping = hjState.config.mapping.flatMap { (mode, map) ->
                            map.map { (key, action) -> Triple(mode, key, action) }
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
                        for ((register, contents) in hjState.allRegisterContents) {
                            println("[$register] = $contents")
                        }
                    }
                })
            }
            addSeparator()
            add(object : AnAction("Open Config") {
                override fun actionPerformed(e: AnActionEvent) {
                    val hjState = HJState.getInstance(project)
                    val cx = HJActionContext(hjState, hjState.currentEditor ?: return)
                    openFile(cx, HJConfiguration.userConfigPath)
                }
            })
            add(object : AnAction("Reload Config") {
                override fun actionPerformed(e: AnActionEvent) {
                    hjState.config = HJConfiguration(project)
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