package spacewaiker.helixjet.editor

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import spacewaiker.helixjet.input.HJInputKey
import com.akuleshov7.ktoml.tree.nodes.TomlFile
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import com.akuleshov7.ktoml.tree.nodes.TomlNode
import java.io.File
import kotlin.collections.get

class HJConfiguration {
    constructor() {
        applyConfigKeymap()
    }

    var mapping: HashMap<Pair<HJEditorMode, HJInputKey>, HJAction> = defaultMapping

    private fun loadConfiguration(): TomlFile? {
        val userHome = System.getProperty("user.home")
        val configFile = "$userHome/.helixjet.toml"

        return try {
            val file = File(configFile)
            if (!file.exists()) {
                println("Configuration file not found: $configFile")
                return null
            }
            if (!file.canRead()) {
                println("Configuration file is not readable: $configFile")
                return null
            }
            val content = file.readText()

            Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true)).tomlParser.parseString(content)
        } catch (e: Exception) {
            println("Failed to load configuration file: $configFile")
            println(e.message)
            null
        }
    }

    private fun applyConfigKeymap() {
        val keys = loadConfiguration()?.children?.find { node -> node.name == "keys" } ?: return

        keys.children.find { node -> node.name == "normal" }?.let {
            applyConfigKeymapForMode(HJEditorMode.NORMAL, it)
        }
        keys.children.find { node -> node.name == "select" }?.let {
            applyConfigKeymapForMode(HJEditorMode.SELECT, it)
        }
    }

    private fun applyConfigKeymapForMode(mode: HJEditorMode, keys: TomlNode) {
        for (child in keys.children) {
            when (child) {
                is TomlKeyValuePrimitive -> {
                    val key = child.key.toString()[0]
                    val action = HJActions.actions[child.value.content as? String]
                    if (key.isLetter() && action != null) {
                        mapping[Pair(mode, HJInputKey.Character(key))] = action
                    }
                }

                else -> {}
            }
        }
    }
}

private val defaultMapping: HashMap<Pair<HJEditorMode, HJInputKey>, HJAction> = hashMapOf(
    Pair(HJEditorMode.NORMAL, HJInputKey.Character('h')) to HJActions.actions["move_char_left"]!!,
    Pair(HJEditorMode.NORMAL, HJInputKey.Character('j')) to HJActions.actions["move_visual_line_down"]!!,
    Pair(HJEditorMode.NORMAL, HJInputKey.Character('k')) to HJActions.actions["move_visual_line_up"]!!,
    Pair(HJEditorMode.NORMAL, HJInputKey.Character('l')) to HJActions.actions["move_char_right"]!!,

    Pair(HJEditorMode.NORMAL, HJInputKey.Character('v')) to HJActions.actions["select_mode"]!!,
    Pair(HJEditorMode.NORMAL, HJInputKey.Character('i')) to HJActions.actions["insert_mode"]!!,
    Pair(HJEditorMode.NORMAL, HJInputKey.Character(';')) to HJActions.actions["collapse_selection"]!!,

    Pair(HJEditorMode.INSERT, HJInputKey.Escape) to HJActions.actions["normal_mode"]!!,
    Pair(HJEditorMode.SELECT, HJInputKey.Escape) to HJActions.actions["normal_mode"]!!,
)
