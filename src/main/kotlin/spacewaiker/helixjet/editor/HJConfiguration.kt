package spacewaiker.helixjet.editor

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import spacewaiker.helixjet.input.HJInputKey
import com.akuleshov7.ktoml.tree.nodes.TomlFile
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import com.akuleshov7.ktoml.tree.nodes.TomlNode
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import kotlin.collections.get

class HJConfiguration {
    companion object {
        private val logger = Logger.getInstance(this::class.java)
    }

    constructor() {
        applyConfigKeymap()
    }

    var mapping: HashMap<Pair<HJEditorMode, HJInputKey>, HJAction> = defaultMappingParsed()

    private fun loadConfiguration(): TomlFile? {
        val userHome = System.getProperty("user.home")
        val configFile = "$userHome/.helixjet.toml"

        return try {
            val file = File(configFile)
            if (!file.exists()) {
                logger.info("Configuration file not found: $configFile")
                return null
            }
            if (!file.canRead()) {
                logger.warn("Configuration file is not readable: $configFile")
                return null
            }
            val content = file.readText()

            val toml = Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true)).tomlParser.parseString(content)
            logger.info("Successfully parsed toml configuration")
            toml
        } catch (e: Exception) {
            logger.error("Failed to load configuration file: $configFile")
            logger.error(e.message)
            null
        }
    }

    /**
     * Overload default keymap configuration with user defined config file, if one exists.
     */
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

private fun defaultMappingParsed(): HashMap<Pair<HJEditorMode, HJInputKey>, HJAction> {
    val map = HashMap<Pair<HJEditorMode, HJInputKey>, HJAction>()

    for ((key, value) in defaultMapping) {
        HJActions.actions[value]?.let { action ->
            val mode = key.first
            val keys = key.second
            for (key in keys) {
                HJInputKey.fromString(key)?.let { map.put(Pair(mode, it), action) }
            }
        }
    }

    return map
}

private val defaultMapping: Map<Pair<HJEditorMode, Array<String>>, String> = mapOf(
    Pair(HJEditorMode.NORMAL, arrayOf("h")) to "move_char_left",
    Pair(HJEditorMode.NORMAL, arrayOf("j")) to "move_visual_line_down",
    Pair(HJEditorMode.NORMAL, arrayOf("k")) to "move_visual_line_up",
    Pair(HJEditorMode.NORMAL, arrayOf("l")) to "move_char_right",

    Pair(HJEditorMode.NORMAL, arrayOf("v")) to "select_mode",
    Pair(HJEditorMode.NORMAL, arrayOf("i")) to "insert_mode",

    Pair(HJEditorMode.NORMAL, arrayOf("d")) to "delete_selection",
    Pair(HJEditorMode.NORMAL, arrayOf(";")) to "collapse_selection",

    Pair(HJEditorMode.INSERT, arrayOf("esc")) to "normal_mode",
    Pair(HJEditorMode.SELECT, arrayOf("esc")) to "normal_mode",
)
