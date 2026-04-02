package spacewaiker.helixjet.editor

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.tree.nodes.TomlFile
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import com.akuleshov7.ktoml.tree.nodes.TomlNode
import com.akuleshov7.ktoml.tree.nodes.TomlTable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.JBColor
import spacewaiker.helixjet.input.HJInputKey
import java.awt.Color
import java.io.File

class HJConfiguration {
    val mapping: Map<HJEditorMode, MutableMap<HJInputKey, HJKeyAction>> = defaultMappingParsed()

    val primaryCursorColor: Color
    val otherCursorsColor: Color
    var scrolloff: Int = 5
        private set

    constructor() {
        val colorScheme = EditorColorsManager.getInstance().globalScheme
        primaryCursorColor = colorScheme.getColor(EditorColors.CARET_COLOR) ?: JBColor.LIGHT_GRAY
        otherCursorsColor = colorScheme.getColor(EditorColors.SELECTED_TEARLINE_COLOR) ?: JBColor.GRAY

        applyConfig()
    }

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
    private fun applyConfig() {
        val config = loadConfiguration() ?: return

        val editor = config.children.find { it.name == "editor" }
        if (editor != null) {
            val scrolloff = editor.children.find { it.name == "scrolloff" }
            if (scrolloff != null && scrolloff is TomlKeyValuePrimitive) (scrolloff.value.content as? Int)?.let { scrolloff ->
                this.scrolloff = scrolloff
                HJState.forAllEditors { it.editor.settings.verticalScrollOffset = scrolloff }
            }
        }

        // keymap
        val keys = config.children.find { it.name == "keys" }
        if (keys != null) {
            val normalKeyOverrides = keys.children.find { it.name == "normal" }?.let {
                getConfigKeymapOverrides(it)
            }.orEmpty()
            val selectKeyOverrides = keys.children.find { it.name == "select" }?.let {
                getConfigKeymapOverrides(it)
            }.orEmpty()
            val insertKeyOverrides = keys.children.find { it.name == "insert" }?.let {
                getConfigKeymapOverrides(it)
            }.orEmpty()

            val overrides = listOf(
                Pair(HJEditorMode.NORMAL, normalKeyOverrides),
                Pair(HJEditorMode.SELECT, selectKeyOverrides),
                Pair(HJEditorMode.INSERT, insertKeyOverrides)
            )
            for ((mode, keyOverrides) in overrides) {
                for ((keys, action) in keyOverrides) {
                    overrideMapping(mapping[mode]!!, keys, action)
                }
            }
        }
    }

    private fun getConfigKeymapOverrides(keys: TomlNode): List<Pair<List<HJInputKey>, HJKeyAction.HJAction>> {
        val keymaps = mutableListOf<Pair<List<HJInputKey>, HJKeyAction.HJAction>>()
        val inputKeyStack = mutableListOf<Pair<List<HJInputKey>, TomlNode>>()

        inputKeyStack.addAll(keys.children.map { Pair(listOf(), it) })

        while (inputKeyStack.isNotEmpty()) {
            val (previousKeys, tomlNode) = inputKeyStack.removeLast()

            val inputKey = HJInputKey.fromString(tomlNode.name)
            if (inputKey == null) {
                logger.warn("Unknown key ${tomlNode.name} in mapping configuration. Skipping")
                continue
            }

            val keys = previousKeys + inputKey

            when (tomlNode) {
                is TomlKeyValuePrimitive -> {
                    var action = (tomlNode.value.content as? String)?.let { HJActions.getAction(it) }
                    if (action == null) {
                        logger.warn("Missing action ${tomlNode.value} for key $inputKey defined in user config. Mapping to no_op.")
                        action = HJActions.getAction("no_op")!!
                    } else {
                        logger.debug("Remapping $inputKey to ${action.name} as defined in user config")
                    }
                    keymaps.add(Pair(keys, action))
                }

                is TomlTable -> {
                    inputKeyStack.addAll(tomlNode.children.map { Pair(keys, it) })
                }

                else -> {}
            }
        }

        return keymaps
    }

    private fun overrideMapping(
        map: MutableMap<HJInputKey, HJKeyAction>,
        keys: List<HJInputKey>,
        action: HJKeyAction.HJAction,
    ) {
        val key = keys.first()
        when (val keyAction = map[key]) {
            // need to check for size. If size is 1, then there are no children and we are replacing this minor mode for a simple action
            is HJKeyAction.HJMinorMode if keys.size > 1 -> {
                overrideMapping(keyAction.mapping, keys.drop(1), action)
            }

            else -> map[key] = action
        }
    }

    private fun parseKeyActionDefinition(node: TomlNode): Pair<HJInputKey, HJKeyAction>? {
        val inputKey = HJInputKey.fromString(node.name)
        if (inputKey == null) {
            logger.warn("Unknown key ${node.name} in mapping configuration. Skipping")
            return null
        }

        return when (node) {
            is TomlKeyValuePrimitive -> {
                var action = (node.value.content as? String)?.let { HJActions.getAction(it) }
                if (action == null) {
                    logger.warn("Missing action ${node.value.content} for key $inputKey defined in user config. Mapping to no_op.")
                    action = HJActions.getAction("no_op")!!
                } else {
                    logger.debug("Remapping $inputKey to ${action.name} as defined in user config")
                }
                Pair(inputKey, action)
            }

            is TomlTable -> {
                val mapping =
                    node.children.mapNotNull(::parseKeyActionDefinition).toMap().toMutableMap()
                logger.debug("Defining minor mode $inputKey as defined in user config")
                Pair(inputKey, HJKeyAction.HJMinorMode(inputKey.toString(), mapping))
            }

            else -> null
        }
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)

        private fun defaultMappingParsed(): Map<HJEditorMode, MutableMap<HJInputKey, HJKeyAction>> {
            val map: Map<HJEditorMode, MutableMap<HJInputKey, HJKeyAction>> = mapOf(
                HJEditorMode.NORMAL to mutableMapOf(),
                HJEditorMode.SELECT to mutableMapOf(),
                HJEditorMode.INSERT to mutableMapOf(),
            )

            for ((key, value) in defaultMappingNormal) {
                parseKeyActionDefinition(key, value)?.let { (inputKey, keyAction) ->
                    map[HJEditorMode.NORMAL]!![inputKey] = keyAction
                }
            }
            for ((key, value) in defaultMappingSelect) {
                parseKeyActionDefinition(key, value)?.let { (inputKey, keyAction) ->
                    map[HJEditorMode.SELECT]!![inputKey] = keyAction
                }
            }
            for ((key, value) in defaultMappingInsert) {
                parseKeyActionDefinition(key, value)?.let { (inputKey, keyAction) ->
                    map[HJEditorMode.INSERT]!![inputKey] = keyAction
                }
            }

            return map
        }

        private fun parseKeyActionDefinition(key: String, value: Any): Pair<HJInputKey, HJKeyAction>? {
            val inputKey = HJInputKey.fromString(key)
            if (inputKey == null) {
                logger.warn("Unknown key $key in default keymap. Skipping")
                return null
            }

            return when (value) {
                is String -> {
                    var action = HJActions.getAction(value)
                    if (action == null) {
                        logger.error("Missing action $value for key $inputKey in default keymap. Mapping to no_op.")
                        action = HJActions.getAction("no_op")!!
                    }
                    Pair(inputKey, action)
                }

                is Pair<*, *> if value.second is Map<*, *> -> {
                    val name = value.first as? String ?: return null
                    val map = value.second as? Map<*, *> ?: return null

                    val mapping = map.mapNotNull { (key, value) ->
                        if (key is String && value != null) parseKeyActionDefinition(key, value)
                        else null
                    }.toMap().toMutableMap()

                    Pair(inputKey, HJKeyAction.HJMinorMode(name, mapping))
                }

                else -> null
            }
        }
    }
}

private val defaultMappingNormal: Map<String, Any> = mapOf(
    "h" to "move_char_left",
    "j" to "move_visual_line_down",
    "k" to "move_visual_line_up",
    "l" to "move_char_right",
    "left" to "move_char_left",
    "down" to "move_visual_line_down",
    "up" to "move_visual_line_up",
    "right" to "move_char_right",
    "t" to "find_till_char",
    "f" to "find_next_char",
    "T" to "till_prev_char",
    "F" to "find_prev_char",
    "r" to "replace",
    "R" to "replace_with_yanked",
    "A-." to "repeat_last_motion",
    "~" to "switch_case",
    "`" to "switch_to_lowercase",
    "A-`" to "switch_to_uppercase",
    "home" to "goto_line_start",
    "end" to "goto_line_end",
    "w" to "move_next_word_start",
    "b" to "move_prev_word_start",
    "e" to "move_next_word_end",
    "W" to "move_next_long_word_start",
    "B" to "move_prev_long_word_start",
    "E" to "move_next_long_word_end",
    "v" to "select_mode",
    "G" to "goto_line",
    "g" to Pair(
        "Goto", mapOf(
            "g" to "goto_file_start",
            "|" to "goto_column",
            "e" to "goto_last_line",
            "f" to "goto_file",
            "h" to "goto_line_start",
            "l" to "goto_line_end",
            "s" to "goto_first_nonwhitespace",
            "d" to "goto_definition",
            "D" to "goto_declaration",
            "y" to "goto_type_definition",
            "r" to "goto_reference",
            "i" to "goto_implementation",
            "t" to "goto_window_top",
            "c" to "goto_window_center",
            "b" to "goto_window_bottom",
            "a" to "goto_last_accessed_file",
            "m" to "goto_last_modified_file",
            "n" to "goto_next_buffer",
            "p" to "goto_previous_buffer",
            "k" to "move_line_up",
            "j" to "move_line_down",
            "." to "goto_last_modification",
            "w" to "goto_word",
        )
    ),
    ":" to "command_mode",
    "i" to "insert_mode",
    "I" to "insert_at_line_start",
    "a" to "append_mode",
    "A" to "insert_at_line_end",
    "o" to "open_below",
    "O" to "open_above",
    "d" to "delete_selection",
    "A-d" to "delete_selection_noyank",
    "c" to "change_selection",
    "A-c" to "change_selection_noyank",
    "C" to "copy_selection_on_next_line",
    "A-C" to "copy_selection_on_prev_line",
    "s" to "select_regex",
    "A-s" to "split_selection_on_newline",
    "A-minus" to "merge_selections",
    "A-_" to "merge_consecutive_selections",
    "S" to "split_selection",
    ";" to "collapse_selection",
    "A-;" to "flip_selections",
    "A-o" to "expand_selection",
    "A-i" to "shrink_selection",
    "A-I" to "select_all_children",
    "A-p" to "select_prev_sibling",
    "A-n" to "select_next_sibling",
    "A-up" to "expand_selection",
    "A-down" to "shrink_selection",
    "A-S-down" to "select_all_children",
    "A-left" to "select_prev_sibling",
    "A-right" to "select_next_sibling",
    "A-e" to "move_parent_node_end",
    "A-b" to "move_parent_node_start",
    "A-a" to "select_all_siblings",
    "%" to "select_all",
    "x" to "extend_line_below",
    "X" to "extend_to_line_bounds",
    "A-x" to "shrink_to_line_bounds",
    "m" to Pair(
        "Match", mapOf(
            "m" to "match_brackets",
            "s" to "surround_add",
            "r" to "surround_replace",
            "d" to "surround_delete",
            "a" to "select_textobject_around",
            "i" to "select_textobject_inner",
        )
    ),
    "[" to Pair(
        "Left bracket", mapOf(
            "d" to "goto_prev_diag",
            "D" to "goto_first_diag",
            "g" to "goto_prev_change",
            "G" to "goto_first_change",
            "f" to "goto_prev_function",
            "t" to "goto_prev_class",
            "a" to "goto_prev_parameter",
            "c" to "goto_prev_comment",
            "e" to "goto_prev_entry",
            "T" to "goto_prev_test",
            "p" to "goto_prev_paragraph",
            "x" to "goto_prev_xml_element",
            "space" to "add_newline_above",
        )
    ),
    "]" to Pair(
        "Right bracket", mapOf(
            "d" to "goto_next_diag",
            "D" to "goto_last_diag",
            "g" to "goto_next_change",
            "G" to "goto_last_change",
            "f" to "goto_next_function",
            "t" to "goto_next_class",
            "a" to "goto_next_parameter",
            "c" to "goto_next_comment",
            "e" to "goto_next_entry",
            "T" to "goto_next_test",
            "p" to "goto_next_paragraph",
            "x" to "goto_next_xml_element",
            "space" to "add_newline_below",
        )
    ),
    "/" to "search",
    "?" to "rsearch",
    "n" to "search_next",
    "N" to "search_prev",
    "*" to "search_selection_detect_word_boundaries",
    "A-*" to "search_selection",
    "u" to "undo",
    "U" to "redo",
    "A-u" to "earlier",
    "A-U" to "later",
    "y" to "yank",
    "p" to "paste_after",
    "P" to "paste_before",
    "Q" to "record_macro",
    "q" to "replay_macro",
    ">" to "indent",
    "<" to "unindent",
    "=" to "format_selections",
    "J" to "join_selections",
    "A-J" to "join_selections_space",
    "K" to "keep_selections",
    "A-K" to "remove_selections",
    "," to "keep_primary_selection",
    "A-," to "remove_primary_selection",
    "&" to "align_selections",
    "_" to "trim_selections",
    "(" to "rotate_selections_backward",
    ")" to "rotate_selections_forward",
    "A-(" to "rotate_selection_contents_backward",
    "A-)" to "rotate_selection_contents_forward",
    "A-:" to "ensure_selections_forward",
    "esc" to "normal_mode",
    "C-b" to "page_up",
    "C-f" to "page_down",
    "pageup" to "page_up",
    "pagedown" to "page_down",
    "C-u" to "page_cursor_half_up",
    "C-d" to "page_cursor_half_down",
    "C-w" to Pair(
        "Window", mapOf(
            "C-w" to "rotate_view",
            "C-s" to "hsplit",
            "C-v" to "vsplit",
            "C-t" to "transpose_view",
            "w" to "rotate_view",
            "s" to "hsplit",
            "v" to "vsplit",
            "t" to "transpose_view",
            "f" to "goto_file_hsplit",
            "F" to "goto_file_vsplit",
            "C-q" to "wclose",
            "C-o" to "wonly",
            "q" to "wclose",
            "o" to "wonly",
            "C-h" to "jump_view_left",
            "C-j" to "jump_view_down",
            "C-k" to "jump_view_up",
            "C-l" to "jump_view_right",
            "h" to "jump_view_left",
            "j" to "jump_view_down",
            "k" to "jump_view_up",
            "l" to "jump_view_right",
            "left" to "jump_view_left",
            "down" to "jump_view_down",
            "up" to "jump_view_up",
            "right" to "jump_view_right",
            "L" to "swap_view_right",
            "K" to "swap_view_up",
            "H" to "swap_view_left",
            "J" to "swap_view_down",
            "n" to Pair(
                "New split scratch buffer", mapOf(
                    "C-s" to "hsplit_new",
                    "C-v" to "vsplit_new",
                    "s" to "hsplit_new",
                    "v" to "vsplit_new",
                )
            ),
        )
    ),
    "space" to Pair(
        "Space", mapOf(
            "f" to "file_picker",
            "F" to "file_picker_in_current_directory",
            "e" to "file_explorer",
            "E" to "file_explorer_in_current_buffer_directory",
            "b" to "buffer_picker",
            "j" to "jumplist_picker",
            "s" to "lsp_or_syntax_symbol_picker",
            "S" to "lsp_or_syntax_workspace_symbol_picker",
            "d" to "diagnostics_picker",
            "D" to "workspace_diagnostics_picker",
            "g" to "changed_file_picker",
            "a" to "code_action",
            "'" to "last_picker",
            "w" to Pair(
                "Window", mapOf(
                    "C-w" to "rotate_view",
                    "C-s" to "hsplit",
                    "C-v" to "vsplit",
                    "C-t" to "transpose_view",
                    "w" to "rotate_view",
                    "s" to "hsplit",
                    "v" to "vsplit",
                    "t" to "transpose_view",
                    "f" to "goto_file_hsplit",
                    "F" to "goto_file_vsplit",
                    "C-q" to "wclose",
                    "C-o" to "wonly",
                    "q" to "wclose",
                    "o" to "wonly",
                    "C-h" to "jump_view_left",
                    "C-j" to "jump_view_down",
                    "C-k" to "jump_view_up",
                    "C-l" to "jump_view_right",
                    "h" to "jump_view_left",
                    "j" to "jump_view_down",
                    "k" to "jump_view_up",
                    "l" to "jump_view_right",
                    "left" to "jump_view_left",
                    "down" to "jump_view_down",
                    "up" to "jump_view_up",
                    "right" to "jump_view_right",
                    "H" to "swap_view_left",
                    "J" to "swap_view_down",
                    "K" to "swap_view_up",
                    "L" to "swap_view_right",
                    "n" to Pair(
                        "New split scratch buffer", mapOf(
                            "C-s" to "hsplit_new",
                            "C-v" to "vsplit_new",
                            "s" to "hsplit_new",
                            "v" to "vsplit_new",
                        )
                    ),
                )
            ),
            "y" to "yank_to_clipboard",
            "Y" to "yank_main_selection_to_clipboard",
            "p" to "paste_clipboard_after",
            "P" to "paste_clipboard_before",
            "R" to "replace_selections_with_clipboard",
            "/" to "global_search",
            "k" to "hover",
            "r" to "rename_symbol",
            "h" to "select_references_to_symbol_under_cursor",
            "c" to "toggle_comments",
            "C" to "toggle_block_comments",
            "A-c" to "toggle_line_comments",
            "?" to "command_palette",
        )
    ),
    "z" to Pair(
        "View", mapOf(
            "z" to "align_view_center",
            "c" to "align_view_center",
            "t" to "align_view_top",
            "b" to "align_view_bottom",
            "m" to "align_view_middle",
            "k" to "scroll_up",
            "j" to "scroll_down",
            "C-b" to "page_up",
            "C-f" to "page_down",
            "C-u" to "page_cursor_half_up",
            "C-d" to "page_cursor_half_down",
            "up" to "scroll_up",
            "down" to "scroll_down",
            "pageup" to "page_up",
            "pagedown" to "page_down",
            "backspace" to "page_cursor_half_up",
            "space" to "page_cursor_half_down",

            "/" to "search",
            "?" to "rsearch",
            "n" to "search_next",
            "N" to "search_prev",
        )
    ),
// TODO: sticky minor modes
    //
//            "Z" to { "View" sticky=true
//                "z" | "c" to align_view_center,
//                "t" to align_view_top,
//                "b" to align_view_bottom,
//                "m" to align_view_middle,
//                "k" | "up" to scroll_up,
//                "j" | "down" to scroll_down,
//                "C-b" | "pageup" to page_up,
//                "C-f" | "pagedown" to page_down,
//                "C-u" | "backspace" to page_cursor_half_up,
//                "C-d" | "space" to page_cursor_half_down,
//
//                "/" to search,
//                "?" to rsearch,
//                "n" to search_next,
//                "N" to search_prev,
//            },
    "C-c" to "toggle_comments",
    "C-i" to "jump_forward",
    "tab" to "jump_forward",
    "C-o" to "jump_backward",
    "C-s" to "save_selection",
    "\"" to "select_register",
    "C-z" to "suspend",
    "C-a" to "increment",
    "C-x" to "decrement",
)

private val defaultMappingSelect: Map<String, Any> = defaultMappingNormal + mapOf(
    "h" to "extend_char_left",
    "j" to "extend_visual_line_down",
    "k" to "extend_visual_line_up",
    "l" to "extend_char_right",
    "left" to "extend_char_left",
    "down" to "extend_visual_line_down",
    "up" to "extend_visual_line_up",
    "right" to "extend_char_right",
    "w" to "extend_next_word_start",
    "b" to "extend_prev_word_start",
    "e" to "extend_next_word_end",
    "W" to "extend_next_long_word_start",
    "B" to "extend_prev_long_word_start",
    "E" to "extend_next_long_word_end",
    "A-e" to "extend_parent_node_end",
    "A-b" to "extend_parent_node_start",
    "n" to "extend_search_next",
    "N" to "extend_search_prev",
    "t" to "extend_till_char",
    "f" to "extend_next_char",
    "T" to "extend_till_prev_char",
    "F" to "extend_prev_char",
    "home" to "extend_to_line_start",
    "end" to "extend_to_line_end",
    "esc" to "exit_select_mode",
    "v" to "normal_mode",
    // TODO: Goto
    "g" to Pair(
        "Goto", mapOf(
            "g" to "extend_to_file_start",
            "|" to "extend_to_column",
            "e" to "extend_to_last_line",
            "k" to "extend_line_up",
            "j" to "extend_line_down",
            "w" to "extend_to_word",
        )
    ),
)

private val defaultMappingInsert: Map<String, String> = mapOf(
    "esc" to "normal_mode",
    "C-s" to "commit_undo_checkpoint",
    "C-x" to "completion",
    "C-r" to "insert_register",
    "C-w" to "delete_word_backward",
    "A-d" to "delete_word_forward",
    "A-backspace" to "delete_word_backward",
    "A-del" to "delete_word_forward",
    "C-u" to "kill_to_line_start",
    "C-k" to "kill_to_line_end",
    "C-h" to "delete_char_backward",
    // TODO: see if below are needed
    // "backspace" to "delete_char_backward",
    // "S-backspace" to "delete_char_backward",
    "C-d" to "delete_char_forward",
    "C-j" to "insert_newline",
    // "del" to "delete_char_forward",
    // "ret" to "insert_newline",
    "tab" to "smart_tab",
    "S-tab" to "insert_tab",
    "up" to "move_visual_line_up",
    "down" to "move_visual_line_down",
    "left" to "move_char_left",
    "right" to "move_char_right",
    "pageup" to "page_up",
    "pagedown" to "page_down",
    "home" to "goto_line_start",
    "end" to "goto_line_end_newline",
)
