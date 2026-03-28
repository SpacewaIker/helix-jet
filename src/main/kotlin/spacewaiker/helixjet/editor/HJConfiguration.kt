package spacewaiker.helixjet.editor

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.tree.nodes.TomlFile
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import com.akuleshov7.ktoml.tree.nodes.TomlNode
import com.intellij.openapi.diagnostic.Logger
import spacewaiker.helixjet.input.HJInputKey
import java.io.File

class HJConfiguration {
    var mapping: MutableMap<Pair<HJEditorMode, HJInputKey>, HJAction> = defaultMappingParsed()

    constructor() {
        applyConfigKeymap()
    }

    /**
     * Reload configuration file.
     *
     * Resets mapping to default, overridden with configuration file.
     */
    fun reloadConfiguration() {
        mapping = defaultMappingParsed()
        applyConfigKeymap()
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
                    val key = HJInputKey.fromString(child.key.toString())
                    var action = (child.value.content as? String)?.let { HJActions.getAction(it) }
                    if (key == null) {
                        logger.warn("Unknown key ${child.key} in mapping configuration. Skipping")
                    } else {
                        if (action == null) {
                            logger.warn("Missing action ${child.value.content} for key $key defined in user config. Mapping to no_op.")
                            action = HJActions.getAction("no_op")!!
                        } else {
                            logger.debug("Remapping $key to ${child.value.content} as defined in user config")
                        }
                        mapping[Pair(mode, key)] = action
                    }
                }

                else -> {}
            }
        }
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)
    }
}

private fun defaultMappingParsed(): MutableMap<Pair<HJEditorMode, HJInputKey>, HJAction> {
    val map: MutableMap<Pair<HJEditorMode, HJInputKey>, HJAction> = mutableMapOf()

    for ((key, value) in defaultMappingNormal) {
        tryAddMapping(map, HJEditorMode.NORMAL, key, value)
    }
    for ((key, value) in defaultMappingSelect) {
        tryAddMapping(map, HJEditorMode.SELECT, key, value)
    }
    for ((key, value) in defaultMappingInsert) {
        tryAddMapping(map, HJEditorMode.INSERT, key, value)
    }

    return map
}

private fun tryAddMapping(
    map: MutableMap<Pair<HJEditorMode, HJInputKey>, HJAction>,
    mode: HJEditorMode,
    key: String,
    action: String,
) {
    val key = HJInputKey.fromString(key)
    val action = HJActions.getAction(action)
    if (key != null && action != null) {
        map[Pair(mode, key)] = action
    }
}

private val defaultMappingNormal: Map<String, String> = mapOf(
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
    // TODO: Goto mode subtable
    // "g" => { "Goto"
    //            "g" => goto_file_start,
    //            "|" => goto_column,
    //            "e" => goto_last_line,
    //            "f" => goto_file,
    //            "h" => goto_line_start,
    //            "l" => goto_line_end,
    //            "s" => goto_first_nonwhitespace,
    //            "d" => goto_definition,
    //            "D" => goto_declaration,
    //            "y" => goto_type_definition,
    //            "r" => goto_reference,
    //            "i" => goto_implementation,
    //            "t" => goto_window_top,
    //            "c" => goto_window_center,
    //            "b" => goto_window_bottom,
    //            "a" => goto_last_accessed_file,
    //            "m" => goto_last_modified_file,
    //            "n" => goto_next_buffer,
    //            "p" => goto_previous_buffer,
    //            "k" => move_line_up,
    //            "j" => move_line_down,
    //            "." => goto_last_modification,
    //            "w" => goto_word,
    //        },
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
    // TODO: match
    // "m" => { "Match"
    //            "m" => match_brackets,
    //            "s" => surround_add,
    //            "r" => surround_replace,
    //            "d" => surround_delete,
    //            "a" => select_textobject_around,
    //            "i" => select_textobject_inner,
    //        },
    // TODO: brackets
    // "[" => { "Left bracket"
    //            "d" => goto_prev_diag,
    //            "D" => goto_first_diag,
    //            "g" => goto_prev_change,
    //            "G" => goto_first_change,
    //            "f" => goto_prev_function,
    //            "t" => goto_prev_class,
    //            "a" => goto_prev_parameter,
    //            "c" => goto_prev_comment,
    //            "e" => goto_prev_entry,
    //            "T" => goto_prev_test,
    //            "p" => goto_prev_paragraph,
    //            "x" => goto_prev_xml_element,
    //            "space" => add_newline_above,
    //        },
    //        "]" => { "Right bracket"
    //            "d" => goto_next_diag,
    //            "D" => goto_last_diag,
    //            "g" => goto_next_change,
    //            "G" => goto_last_change,
    //            "f" => goto_next_function,
    //            "t" => goto_next_class,
    //            "a" => goto_next_parameter,
    //            "c" => goto_next_comment,
    //            "e" => goto_next_entry,
    //            "T" => goto_next_test,
    //            "p" => goto_next_paragraph,
    //            "x" => goto_next_xml_element,
    //            "space" => add_newline_below,
    //        },
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
    // TODO: window
    // "C-w" => { "Window"
    //            "C-w" | "w" => rotate_view,
    //            "C-s" | "s" => hsplit,
    //            "C-v" | "v" => vsplit,
    //            "C-t" | "t" => transpose_view,
    //            "f" => goto_file_hsplit,
    //            "F" => goto_file_vsplit,
    //            "C-q" | "q" => wclose,
    //            "C-o" | "o" => wonly,
    //            "C-h" | "h" | "left" => jump_view_left,
    //            "C-j" | "j" | "down" => jump_view_down,
    //            "C-k" | "k" | "up" => jump_view_up,
    //            "C-l" | "l" | "right" => jump_view_right,
    //            "L" => swap_view_right,
    //            "K" => swap_view_up,
    //            "H" => swap_view_left,
    //            "J" => swap_view_down,
    //            "n" => { "New split scratch buffer"
    //                "C-s" | "s" => hsplit_new,
    //                "C-v" | "v" => vsplit_new,
    //            },
    //        },
    // TODO: space
    // "space" => { "Space"
    //            "f" => file_picker,
    //            "F" => file_picker_in_current_directory,
    //            "e" => file_explorer,
    //            "E" => file_explorer_in_current_buffer_directory,
    //            "b" => buffer_picker,
    //            "j" => jumplist_picker,
    //            "s" => lsp_or_syntax_symbol_picker,
    //            "S" => lsp_or_syntax_workspace_symbol_picker,
    //            "d" => diagnostics_picker,
    //            "D" => workspace_diagnostics_picker,
    //            "g" => changed_file_picker,
    //            "a" => code_action,
    //            "'" => last_picker,
    //            "w" => { "Window"
    //                "C-w" | "w" => rotate_view,
    //                "C-s" | "s" => hsplit,
    //                "C-v" | "v" => vsplit,
    //                "C-t" | "t" => transpose_view,
    //                "f" => goto_file_hsplit,
    //                "F" => goto_file_vsplit,
    //                "C-q" | "q" => wclose,
    //                "C-o" | "o" => wonly,
    //                "C-h" | "h" | "left" => jump_view_left,
    //                "C-j" | "j" | "down" => jump_view_down,
    //                "C-k" | "k" | "up" => jump_view_up,
    //                "C-l" | "l" | "right" => jump_view_right,
    //                "H" => swap_view_left,
    //                "J" => swap_view_down,
    //                "K" => swap_view_up,
    //                "L" => swap_view_right,
    //                "n" => { "New split scratch buffer"
    //                    "C-s" | "s" => hsplit_new,
    //                    "C-v" | "v" => vsplit_new,
    //                },
    //            },
    //            "y" => yank_to_clipboard,
    //            "Y" => yank_main_selection_to_clipboard,
    //            "p" => paste_clipboard_after,
    //            "P" => paste_clipboard_before,
    //            "R" => replace_selections_with_clipboard,
    //            "/" => global_search,
    //            "k" => hover,
    //            "r" => rename_symbol,
    //            "h" => select_references_to_symbol_under_cursor,
    //            "c" => toggle_comments,
    //            "C" => toggle_block_comments,
    //            "A-c" => toggle_line_comments,
    //            "?" => command_palette,
    //        },
    // TODO: view
    // "z" => { "View"
    //            "z" | "c" => align_view_center,
    //            "t" => align_view_top,
    //            "b" => align_view_bottom,
    //            "m" => align_view_middle,
    //            "k" | "up" => scroll_up,
    //            "j" | "down" => scroll_down,
    //            "C-b" | "pageup" => page_up,
    //            "C-f" | "pagedown" => page_down,
    //            "C-u" | "backspace" => page_cursor_half_up,
    //            "C-d" | "space" => page_cursor_half_down,
    //
    //            "/" => search,
    //            "?" => rsearch,
    //            "n" => search_next,
    //            "N" => search_prev,
    //        },
    //        "Z" => { "View" sticky=true
    //            "z" | "c" => align_view_center,
    //            "t" => align_view_top,
    //            "b" => align_view_bottom,
    //            "m" => align_view_middle,
    //            "k" | "up" => scroll_up,
    //            "j" | "down" => scroll_down,
    //            "C-b" | "pageup" => page_up,
    //            "C-f" | "pagedown" => page_down,
    //            "C-u" | "backspace" => page_cursor_half_up,
    //            "C-d" | "space" => page_cursor_half_down,
    //
    //            "/" => search,
    //            "?" => rsearch,
    //            "n" => search_next,
    //            "N" => search_prev,
    //        },
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

private val defaultMappingSelect: Map<String, String> = defaultMappingNormal + mapOf(
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
    // "g" => { "Goto"
    //            "g" => extend_to_file_start,
    //            "|" => extend_to_column,
    //            "e" => extend_to_last_line,
    //            "k" => extend_line_up,
    //            "j" => extend_line_down,
    //            "w" => extend_to_word,
    //        },
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
