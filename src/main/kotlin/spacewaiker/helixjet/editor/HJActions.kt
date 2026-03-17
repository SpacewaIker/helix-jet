package spacewaiker.helixjet.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.ui.HJStatusBarWidget

typealias HJAction = (Editor) -> Unit

object HJActions {
    private val logger = Logger.getInstance(this::class.java)

    val actions: Map<String, HJAction> = createActionsMap()

    private fun createActionsMap(): Map<String, HJAction> {
        val map = mutableMapOf<String, HJAction>()

        // @formatter:off
        defActions(map, arrayOf(
            Pair("no_op") { _: Editor -> },
            Pair("move_char_left") { editor: Editor -> moveCaretOffset(editor, -1) },
            Pair("move_char_right") { editor: Editor -> moveCaretOffset(editor, 1) },
            Pair("move_line_up", null),
            Pair("move_line_down", null),
            Pair("move_visual_line_up") { editor: Editor -> moveCaretLogicalPosition(editor, -1, 0) },
            Pair("move_visual_line_down") { editor: Editor -> moveCaretLogicalPosition(editor, 1, 0) },
            Pair("extend_char_left", null),
            Pair("extend_char_right'", null),
            Pair("extend_line_up", null),
            Pair("extend_line_down", null),
            Pair("extend_visual_line_up", null),
            Pair("extend_visual_line_down", null),
            Pair("copy_selection_on_next_line", null),
            Pair("copy_selection_on_prev_line", null),
            Pair("move_next_word_start", null),
            Pair("move_prev_word_start", null),
            Pair("move_next_word_end", null),
            Pair("move_prev_word_end", null),
            Pair("move_next_long_word_start", null),
            Pair("move_prev_long_word_start", null),
            Pair("move_next_long_word_end", null),
            Pair("move_prev_long_word_end", null),
            Pair("move_next_sub_word_start", null),
            Pair("move_prev_sub_word_start", null),
            Pair("move_next_sub_word_end", null),
            Pair("move_prev_sub_word_end", null),
            Pair("move_parent_node_end", null),
            Pair("move_parent_node_start", null),
            Pair("extend_next_word_start", null),
            Pair("extend_prev_word_start", null),
            Pair("extend_next_word_end", null),
            Pair("extend_prev_word_end", null),
            Pair("extend_next_long_word_start", null),
            Pair("extend_prev_long_word_start", null),
            Pair("extend_next_long_word_end", null),
            Pair("extend_prev_long_word_end", null),
            Pair("extend_next_sub_word_start", null),
            Pair("extend_prev_sub_word_start", null),
            Pair("extend_next_sub_word_end", null),
            Pair("extend_prev_sub_word_end", null),
            Pair("extend_parent_node_end", null),
            Pair("extend_parent_node_start", null),
            Pair("find_till_char", null),
            Pair("find_next_char", null),
            Pair("extend_till_char", null),
            Pair("extend_next_char", null),
            Pair("till_prev_char", null),
            Pair("find_prev_char", null),
            Pair("extend_till_prev_char", null),
            Pair("extend_prev_char", null),
            Pair("repeat_last_motion", null),
            Pair("replace", null),
            Pair("switch_case", null),
            Pair("switch_to_uppercase", null),
            Pair("switch_to_lowercase", null),
            Pair("page_up", null),
            Pair("page_down", null),
            Pair("half_page_up", null),
            Pair("half_page_down", null),
            Pair("page_cursor_up", null),
            Pair("page_cursor_down", null),
            Pair("page_cursor_half_up", null),
            Pair("page_cursor_half_down", null),
            Pair("select_all", null),
            Pair("select_regex", null),
            Pair("split_selection", null),
            Pair("split_selection_on_newline", null),
            Pair("merge_selections", null),
            Pair("merge_consecutive_selections", null),
            Pair("search", null),
            Pair("rsearch", null),
            Pair("search_next", null),
            Pair("search_prev", null),
            Pair("extend_search_next", null),
            Pair("extend_search_prev", null),
            Pair("search_selection", null),
            Pair("search_selection_detect_word_boundaries", null),
            Pair("make_search_word_bounded", null),
            Pair("global_search", null),
            Pair("extend_line", null),
            Pair("extend_line_below", null),
            Pair("extend_line_above", null),
            Pair("select_line_above", null),
            Pair("select_line_below", null),
            Pair("extend_to_line_bounds", null),
            Pair("shrink_to_line_bounds", null),
            Pair("delete_selection") { editor: Editor ->
                    editor.selectionModel.copySelectionToClipboard()
                    deleteSelection(editor)
                    collapseSelection(editor)
                    switchToMode(editor, HJEditorMode.NORMAL)
            },
            Pair("delete_selection_noyank") { editor: Editor ->
                    deleteSelection(editor)
                    collapseSelection(editor)
                    switchToMode(editor, HJEditorMode.NORMAL)
            },
            Pair("change_selection") { editor: Editor ->
                editor.selectionModel.copySelectionToClipboard()
                deleteSelection(editor)
                collapseSelection(editor)
                switchToMode(editor, HJEditorMode.INSERT)
            },
            Pair("change_selection_noyank") { editor: Editor ->
                deleteSelection(editor)
                collapseSelection(editor)
                switchToMode(editor, HJEditorMode.INSERT)
            },
            Pair("collapse_selection") { editor: Editor -> collapseSelection(editor) },
            Pair("flip_selections", null),
            Pair("ensure_selections_forward", null),
            Pair("insert_mode") { editor: Editor -> switchToMode(editor, HJEditorMode.INSERT) },
            Pair("append_mode", null),
            Pair("command_mode", null),
            Pair("file_picker", null),
            Pair("file_picker_in_current_buffer_directory", null),
            Pair("file_picker_in_current_directory", null),
            Pair("file_explorer", null),
            Pair("file_explorer_in_current_buffer_directory", null),
            Pair("file_explorer_in_current_directory", null),
            Pair("code_action", null),
            Pair("buffer_picker", null),
            Pair("jumplist_picker", null),
            Pair("symbol_picker", null),
            Pair("syntax_symbol_picker", null),
            Pair("lsp_or_syntax_symbol_picker", null),
            Pair("changed_file_picker", null),
            Pair("select_references_to_symbol_under_cursor", null),
            Pair("workspace_symbol_picker", null),
            Pair("syntax_workspace_symbol_picker", null),
            Pair("lsp_or_syntax_workspace_symbol_picker", null),
            Pair("diagnostics_picker", null),
            Pair("workspace_diagnostics_picker", null),
            Pair("last_picker", null),
            Pair("insert_at_line_start", null),
            Pair("insert_at_line_end", null),
            Pair("open_below", null),
            Pair("open_above", null),
            Pair("normal_mode") { editor: Editor -> switchToMode(editor, HJEditorMode.NORMAL) },
            Pair("select_mode") { editor: Editor -> switchToMode(editor, HJEditorMode.SELECT) },
            Pair("exit_select_mode", null),
            Pair("goto_definition", null),
            Pair("goto_declaration", null),
            Pair("add_newline_above", null),
            Pair("add_newline_below", null),
            Pair("goto_type_definition", null),
            Pair("goto_implementation", null),
            Pair("goto_file_start", null),
            Pair("goto_file_end", null),
            Pair("extend_to_file_start", null),
            Pair("extend_to_file_end", null),
            Pair("goto_file", null),
            Pair("goto_file_hsplit", null),
            Pair("goto_file_vsplit", null),
            Pair("goto_reference", null),
            Pair("goto_window_top", null),
            Pair("goto_window_center", null),
            Pair("goto_window_bottom", null),
            Pair("goto_last_accessed_file", null),
            Pair("goto_last_modified_file", null),
            Pair("goto_last_modification", null),
            Pair("goto_line", null),
            Pair("goto_last_line", null),
            Pair("extend_to_last_line", null),
            Pair("goto_first_diag", null),
            Pair("goto_last_diag", null),
            Pair("goto_next_diag", null),
            Pair("goto_prev_diag", null),
            Pair("goto_next_change", null),
            Pair("goto_prev_change", null),
            Pair("goto_first_change", null),
            Pair("goto_last_change", null),
            Pair("goto_line_start", null),
            Pair("goto_line_end", null),
            Pair("goto_column", null),
            Pair("extend_to_column", null),
            Pair("goto_next_buffer", null),
            Pair("goto_previous_buffer", null),
            Pair("goto_line_end_newline", null),
            Pair("goto_first_nonwhitespace", null),
            Pair("trim_selections", null),
            Pair("extend_to_line_start", null),
            Pair("extend_to_first_nonwhitespace", null),
            Pair("extend_to_line_end", null),
            Pair("extend_to_line_end_newline", null),
            Pair("signature_help", null),
            Pair("smart_tab", null),
            Pair("insert_tab", null),
            Pair("insert_newline", null),
            Pair("insert_char_interactive", null),
            Pair("append_char_interactive", null),
            Pair("delete_char_backward", null),
            Pair("delete_char_forward", null),
            Pair("delete_word_backward", null),
            Pair("delete_word_forward", null),
            Pair("kill_to_line_start", null),
            Pair("kill_to_line_end", null),
            Pair("undo", null),
            Pair("redo", null),
            Pair("earlier", null),
            Pair("later", null),
            Pair("commit_undo_checkpoint", null),
            Pair("yank", null),
            Pair("yank_to_clipboard", null),
            Pair("yank_to_primary_clipboard", null),
            Pair("yank_joined", null),
            Pair("yank_joined_to_clipboard", null),
            Pair("yank_main_selection_to_clipboard", null),
            Pair("yank_joined_to_primary_clipboard", null),
            Pair("yank_main_selection_to_primary_clipboard", null),
            Pair("replace_with_yanked", null),
            Pair("replace_selections_with_clipboard", null),
            Pair("replace_selections_with_primary_clipboard", null),
            Pair("paste_after", null),
            Pair("paste_before", null),
            Pair("paste_clipboard_after", null),
            Pair("paste_clipboard_before", null),
            Pair("paste_primary_clipboard_after", null),
            Pair("paste_primary_clipboard_before", null),
            Pair("indent", null),
            Pair("unindent", null),
            Pair("format_selections", null),
            Pair("join_selections", null),
            Pair("join_selections_space", null),
            Pair("keep_selections", null),
            Pair("remove_selections", null),
            Pair("align_selections", null),
            Pair("keep_primary_selection", null),
            Pair("remove_primary_selection", null),
            Pair("completion", null),
            Pair("hover", null),
            Pair("toggle_comments", null),
            Pair("toggle_line_comments", null),
            Pair("toggle_block_comments", null),
            Pair("rotate_selections_forward", null),
            Pair("rotate_selections_backward", null),
            Pair("rotate_selection_contents_forward", null),
            Pair("rotate_selection_contents_backward", null),
            Pair("reverse_selection_contents", null),
            Pair("expand_selection", null),
            Pair("shrink_selection", null),
            Pair("select_next_sibling", null),
            Pair("select_prev_sibling", null),
            Pair("select_all_siblings", null),
            Pair("select_all_children", null),
            Pair("jump_forward", null),
            Pair("jump_backward", null),
            Pair("save_selection", null),
            Pair("jump_view_right", null),
            Pair("jump_view_left", null),
            Pair("jump_view_up", null),
            Pair("jump_view_down", null),
            Pair("swap_view_right", null),
            Pair("swap_view_left", null),
            Pair("swap_view_up", null),
            Pair("swap_view_down", null),
            Pair("transpose_view", null),
            Pair("rotate_view", null),
            Pair("rotate_view_reverse", null),
            Pair("hsplit", null),
            Pair("hsplit_new", null),
            Pair("vsplit", null),
            Pair("vsplit_new", null),
            Pair("wclose", null),
            Pair("wonly", null),
            Pair("select_register", null),
            Pair("insert_register", null),
            Pair("copy_between_registers", null),
            Pair("align_view_middle", null),
            Pair("align_view_top", null),
            Pair("align_view_center", null),
            Pair("align_view_bottom", null),
            Pair("scroll_up", null),
            Pair("scroll_down", null),
            Pair("match_brackets", null),
            Pair("surround_add", null),
            Pair("surround_replace", null),
            Pair("surround_delete", null),
            Pair("select_textobject_around", null),
            Pair("select_textobject_inner", null),
            Pair("goto_next_function", null),
            Pair("goto_prev_function", null),
            Pair("goto_next_class", null),
            Pair("goto_prev_class", null),
            Pair("goto_next_parameter", null),
            Pair("goto_prev_parameter", null),
            Pair("goto_next_comment", null),
            Pair("goto_prev_comment", null),
            Pair("goto_next_test", null),
            Pair("goto_prev_test", null),
            Pair("goto_next_xml_element", null),
            Pair("goto_prev_xml_element", null),
            Pair("goto_next_entry", null),
            Pair("goto_prev_entry", null),
            Pair("goto_next_paragraph", null),
            Pair("goto_prev_paragraph", null),
            Pair("suspend", null),
            Pair("rename_symbol", null),
            Pair("increment", null),
            Pair("decrement", null),
            Pair("record_macro", null),
            Pair("replay_macro", null),
            Pair("command_palette", null),
            Pair("goto_word", null),
            Pair("extend_to_word", null),
            Pair("goto_next_tabstop", null),
            Pair("goto_prev_tabstop", null),
            Pair("rotate_selections_first", null),
            Pair("rotate_selections_last", null),
        ))
        // @formatter:on

        return map
    }

    private fun defActions(map: MutableMap<String, HJAction>, actions: Array<Pair<String, HJAction?>>) {
        for (action in actions) {
            defAction(map, action.first, action.second)
        }
    }

    private fun defAction(map: MutableMap<String, HJAction>, name: String, action: HJAction?) {
        if (map.containsKey(name)) {
            logger.warn("Action $name is already defined in actions")
            return
        }
        if (action == null) {
            map[name] = { _: Editor -> logger.warn("Action $name is not yet defined") }
        } else {
            map[name] = { editor: Editor ->
                logger.debug("Executing action $name")
                action(editor)
            }
        }
    }
}

private fun moveCaretLogicalPosition(editor: Editor, deltaLine: Int, deltaColumn: Int) {
    val position = editor.caretModel.primaryCaret.logicalPosition
    editor.caretModel.primaryCaret.moveToLogicalPosition(
        LogicalPosition(
            position.line + deltaLine, position.column + deltaColumn
        )
    )
}

private fun moveCaretOffset(editor: Editor, deltaOffset: Int) {
    val offset = editor.caretModel.primaryCaret.offset
    editor.caretModel.primaryCaret.moveToOffset(offset + deltaOffset)
}

fun switchToMode(editor: Editor, newMode: HJEditorMode) {
    if (HJEditorState.mode == newMode) return
    HJEditorState.mode = newMode
    EditorFactory.getInstance().allEditors.forEach { it.settings.isBlockCursor = newMode != HJEditorMode.INSERT }
    editor.project?.let {
        WindowManager.getInstance().getStatusBar(it)?.updateWidget(HJStatusBarWidget.ID)
    }
}

private fun collapseSelection(editor: Editor) {
    editor.selectionModel.removeSelection()
}

private fun deleteSelection(editor: Editor) {
    val caret = editor.caretModel.primaryCaret
    val start = caret.selectionStart
    var end = caret.selectionEnd
    if (caret.offset == end) end += 1

    WriteCommandAction.runWriteCommandAction(editor.project) {
        editor.document.deleteString(start, end)
    }
}