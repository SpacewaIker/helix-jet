package spacewaiker.helixjet.editor

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.ui.HJStatusBarModeWidget

//typealias HJAction = (Editor) -> Unit
class HJAction {
    constructor(name: String, action: ((Editor) -> Unit)? = null) {
        this.name = name
        this.action = action
    }

    fun execute(editor: Editor) {
        if (action == null) {
            HJActions.logger.warn("Action $name is not yet defined")
        } else {
            HJActions.logger.debug("Executing action $name")
            action(editor)
        }
    }

    val name: String

    private val action: ((Editor) -> Unit)?
}

object HJActions {
    val logger = Logger.getInstance(this::class.java)

    private val actions: MutableMap<String, HJAction> = createActionsMap()

    fun getAction(name: String): HJAction? {
        val ideAction = name.removeSurrounding("<", ">")
        if (ideAction != name) {
            actions[name] = HJAction(name) { editor: Editor -> executeIdeAction(editor, ideAction) }
        }

        return actions[name]
    }

    private fun createActionsMap(): MutableMap<String, HJAction> {
        val map = mutableMapOf<String, HJAction>()

        // @formatter:off
        defActions(map, arrayOf(
            HJAction("no_op") { _: Editor -> },
//            HJAction("move_char_left") { editor: Editor -> moveCaretDeltaOffset(editor, -1) },
//            HJAction("move_char_right") { editor: Editor -> moveCaretDeltaOffset(editor, 1) },
//            HJAction("move_line_up") { editor: Editor -> moveCaretLogicalPosition(editor, -1, 0) },
//            HJAction("move_line_down") { editor: Editor -> moveCaretLogicalPosition(editor, 1, 0) },
//            HJAction("move_visual_line_up") { editor: Editor -> moveCaretVisualPosition(editor, -1, 0) },
//            HJAction("move_visual_line_down") { editor: Editor -> moveCaretVisualPosition(editor, 1, 0) },
//            HJAction("extend_char_left") { editor: Editor -> extendSelectionWithMovement(editor) { e: Editor -> moveCaretDeltaOffset(e, -1) } },
//            HJAction("extend_char_right") { editor: Editor -> extendSelectionWithMovement(editor) { e: Editor -> moveCaretDeltaOffset(e, 1) } },
//            HJAction("extend_line_up") { editor: Editor -> extendSelectionWithMovement(editor) { e: Editor -> moveCaretLogicalPosition(e, -1, 0) } },
//            HJAction("extend_line_down") { editor: Editor -> extendSelectionWithMovement(editor) { e: Editor -> moveCaretLogicalPosition(e, 1, 0) } },
//            HJAction("extend_visual_line_up") { editor: Editor -> extendSelectionWithMovement(editor) { e: Editor -> moveCaretVisualPosition(e, -1, 0) } },
//            HJAction("extend_visual_line_down") { editor: Editor -> extendSelectionWithMovement(editor) { e: Editor -> moveCaretVisualPosition(e, 1, 0) } },
            HJAction("move_char_left") { e: Editor -> moveImpl(e, HJCharMovementKind.HORIZONTAL, HJMovementDirection.BACKWARD, 1, false) },
            HJAction("move_char_right") { e: Editor -> moveImpl(e, HJCharMovementKind.HORIZONTAL, HJMovementDirection.FORWARD, 1, false) },
            HJAction("move_line_up") { e: Editor -> moveImpl(e, HJCharMovementKind.VERTICAL, HJMovementDirection.BACKWARD, 1, false) },
            HJAction("move_line_down") { e: Editor -> moveImpl(e, HJCharMovementKind.VERTICAL, HJMovementDirection.FORWARD, 1, false) },
            HJAction("move_visual_line_up") { e: Editor -> moveImpl(e, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.BACKWARD, 1, false) },
            HJAction("move_visual_line_down") { e: Editor -> moveImpl(e, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.FORWARD, 1, false) },
            HJAction("extend_char_left") { e: Editor -> moveImpl(e, HJCharMovementKind.HORIZONTAL, HJMovementDirection.BACKWARD, 1, true) },
            HJAction("extend_char_right") { e: Editor -> moveImpl(e, HJCharMovementKind.HORIZONTAL, HJMovementDirection.FORWARD, 1, true) },
            HJAction("extend_line_up") { e: Editor -> moveImpl(e, HJCharMovementKind.VERTICAL, HJMovementDirection.BACKWARD, 1, true) },
            HJAction("extend_line_down") { e: Editor -> moveImpl(e, HJCharMovementKind.VERTICAL, HJMovementDirection.FORWARD, 1, true) },
            HJAction("extend_visual_line_up") { e: Editor -> moveImpl(e, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.BACKWARD, 1, true) },
            HJAction("extend_visual_line_down") { e: Editor -> moveImpl(e, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.FORWARD, 1, true) },
            HJAction("copy_selection_on_next_line"),
            HJAction("copy_selection_on_prev_line"),
            HJAction("move_next_word_start") { editor: Editor ->
                getNextWordStart(editor)?.let {
                    collapseSelection(editor)
                    extendSelectionWithMovement(editor) { e: Editor -> moveCaretToOffset(e, it) }
                }
            },
            HJAction("move_prev_word_start"),
            HJAction("move_next_word_end"),
            HJAction("move_prev_word_end"),
            HJAction("move_next_long_word_start"),
            HJAction("move_prev_long_word_start"),
            HJAction("move_next_long_word_end"),
            HJAction("move_prev_long_word_end"),
            HJAction("move_next_sub_word_start"),
            HJAction("move_prev_sub_word_start"),
            HJAction("move_next_sub_word_end"),
            HJAction("move_prev_sub_word_end"),
            HJAction("move_parent_node_end"),
            HJAction("move_parent_node_start"),
            HJAction("extend_next_word_start"),
            HJAction("extend_prev_word_start"),
            HJAction("extend_next_word_end"),
            HJAction("extend_prev_word_end"),
            HJAction("extend_next_long_word_start"),
            HJAction("extend_prev_long_word_start"),
            HJAction("extend_next_long_word_end"),
            HJAction("extend_prev_long_word_end"),
            HJAction("extend_next_sub_word_start"),
            HJAction("extend_prev_sub_word_start"),
            HJAction("extend_next_sub_word_end"),
            HJAction("extend_prev_sub_word_end"),
            HJAction("extend_parent_node_end"),
            HJAction("extend_parent_node_start"),
            HJAction("find_till_char"),
            HJAction("find_next_char"),
            HJAction("extend_till_char"),
            HJAction("extend_next_char"),
            HJAction("till_prev_char"),
            HJAction("find_prev_char"),
            HJAction("extend_till_prev_char"),
            HJAction("extend_prev_char"),
            HJAction("repeat_last_motion"),
            HJAction("replace"),
            HJAction("switch_case"),
            HJAction("switch_to_uppercase"),
            HJAction("switch_to_lowercase"),
            HJAction("page_up"),
            HJAction("page_down"),
            HJAction("half_page_up"),
            HJAction("half_page_down"),
            HJAction("page_cursor_up"),
            HJAction("page_cursor_down"),
            HJAction("page_cursor_half_up"),
            HJAction("page_cursor_half_down"),
            HJAction("select_all"),
            HJAction("select_regex"),
            HJAction("split_selection"),
            HJAction("split_selection_on_newline"),
            HJAction("merge_selections"),
            HJAction("merge_consecutive_selections"),
            HJAction("search"),
            HJAction("rsearch"),
            HJAction("search_next"),
            HJAction("search_prev"),
            HJAction("extend_search_next"),
            HJAction("extend_search_prev"),
            HJAction("search_selection"),
            HJAction("search_selection_detect_word_boundaries"),
            HJAction("make_search_word_bounded"),
            HJAction("global_search"),
            HJAction("extend_line"),
            HJAction("extend_line_below"),
            HJAction("extend_line_above"),
            HJAction("select_line_above"),
            HJAction("select_line_below"),
            HJAction("extend_to_line_bounds"),
            HJAction("shrink_to_line_bounds"),
            HJAction("delete_selection") { editor: Editor ->
                extendSelection(editor)
                editor.selectionModel.copySelectionToClipboard()
                deleteSelection(editor)
                editor.selectionModel.removeSelection()
                switchToMode(editor, HJEditorMode.NORMAL)
            },
            HJAction("delete_selection_noyank") { editor: Editor ->
                extendSelection(editor)
                deleteSelection(editor)
                editor.selectionModel.removeSelection()
                switchToMode(editor, HJEditorMode.NORMAL)
            },
            HJAction("change_selection") { editor: Editor ->
                extendSelection(editor)
                editor.selectionModel.copySelectionToClipboard()
                deleteSelection(editor)
                editor.selectionModel.removeSelection()
                switchToMode(editor, HJEditorMode.INSERT)
            },
            HJAction("change_selection_noyank") { editor: Editor ->
                extendSelection(editor)
                deleteSelection(editor)
                editor.selectionModel.removeSelection()
                switchToMode(editor, HJEditorMode.INSERT)
            },
            HJAction("collapse_selection") { editor: Editor -> collapseSelection(editor) },
            HJAction("flip_selections"),
            HJAction("ensure_selections_forward"),
            HJAction("insert_mode") { editor: Editor -> switchToMode(editor, HJEditorMode.INSERT) },
            HJAction("append_mode"),
            HJAction("command_mode"),
            HJAction("file_picker"),
            HJAction("file_picker_in_current_buffer_directory"),
            HJAction("file_picker_in_current_directory"),
            HJAction("file_explorer"),
            HJAction("file_explorer_in_current_buffer_directory"),
            HJAction("file_explorer_in_current_directory"),
            HJAction("code_action"),
            HJAction("buffer_picker"),
            HJAction("jumplist_picker"),
            HJAction("symbol_picker"),
            HJAction("syntax_symbol_picker"),
            HJAction("lsp_or_syntax_symbol_picker"),
            HJAction("changed_file_picker"),
            HJAction("select_references_to_symbol_under_cursor"),
            HJAction("workspace_symbol_picker"),
            HJAction("syntax_workspace_symbol_picker"),
            HJAction("lsp_or_syntax_workspace_symbol_picker"),
            HJAction("diagnostics_picker"),
            HJAction("workspace_diagnostics_picker"),
            HJAction("last_picker"),
            HJAction("insert_at_line_start"),
            HJAction("insert_at_line_end"),
            HJAction("open_below"),
            HJAction("open_above"),
            HJAction("normal_mode") { editor: Editor -> switchToMode(editor, HJEditorMode.NORMAL) },
            HJAction("select_mode") { editor: Editor -> switchToMode(editor, HJEditorMode.SELECT) },
            HJAction("exit_select_mode") { editor: Editor -> if (HJEditorState.mode == HJEditorMode.SELECT) switchToMode(editor, HJEditorMode.NORMAL) },
            HJAction("goto_definition"),
            HJAction("goto_declaration"),
            HJAction("add_newline_above"),
            HJAction("add_newline_below"),
            HJAction("goto_type_definition"),
            HJAction("goto_implementation"),
            HJAction("goto_file_start"),
            HJAction("goto_file_end"),
            HJAction("extend_to_file_start"),
            HJAction("extend_to_file_end"),
            HJAction("goto_file"),
            HJAction("goto_file_hsplit"),
            HJAction("goto_file_vsplit"),
            HJAction("goto_reference"),
            HJAction("goto_window_top"),
            HJAction("goto_window_center"),
            HJAction("goto_window_bottom"),
            HJAction("goto_last_accessed_file"),
            HJAction("goto_last_modified_file"),
            HJAction("goto_last_modification"),
            HJAction("goto_line"),
            HJAction("goto_last_line"),
            HJAction("extend_to_last_line"),
            HJAction("goto_first_diag"),
            HJAction("goto_last_diag"),
            HJAction("goto_next_diag"),
            HJAction("goto_prev_diag"),
            HJAction("goto_next_change"),
            HJAction("goto_prev_change"),
            HJAction("goto_first_change"),
            HJAction("goto_last_change"),
            HJAction("goto_line_start"),
            HJAction("goto_line_end"),
            HJAction("goto_column"),
            HJAction("extend_to_column"),
            HJAction("goto_next_buffer"),
            HJAction("goto_previous_buffer"),
            HJAction("goto_line_end_newline"),
            HJAction("goto_first_nonwhitespace"),
            HJAction("trim_selections"),
            HJAction("extend_to_line_start"),
            HJAction("extend_to_first_nonwhitespace"),
            HJAction("extend_to_line_end"),
            HJAction("extend_to_line_end_newline"),
            HJAction("signature_help"),
            HJAction("smart_tab"),
            HJAction("insert_tab"),
            HJAction("insert_newline"),
            HJAction("insert_char_interactive"),
            HJAction("append_char_interactive"),
            HJAction("delete_char_backward"),
            HJAction("delete_char_forward"),
            HJAction("delete_word_backward"),
            HJAction("delete_word_forward"),
            HJAction("kill_to_line_start"),
            HJAction("kill_to_line_end"),
            HJAction("undo") { editor: Editor -> executeIdeAction(editor, IdeActions.ACTION_UNDO) },
            HJAction("redo") { editor: Editor -> executeIdeAction(editor, IdeActions.ACTION_REDO) },
            HJAction("earlier"),
            HJAction("later"),
            HJAction("commit_undo_checkpoint"),
            HJAction("yank") { editor: Editor -> extendSelection(editor); editor.selectionModel.copySelectionToClipboard() },
            HJAction("yank_to_clipboard"),
            HJAction("yank_joined"),
            HJAction("yank_joined_to_clipboard"),
            HJAction("yank_main_selection_to_clipboard"),
            HJAction("replace_with_yanked"),
            HJAction("replace_selections_with_clipboard"),
            HJAction("paste_after"),
            HJAction("paste_before"),
            HJAction("paste_clipboard_after"),
            HJAction("paste_clipboard_before"),
            HJAction("paste_primary_clipboard_after"),
            HJAction("paste_primary_clipboard_before"),
            HJAction("indent"),
            HJAction("unindent"),
            HJAction("format_selections"),
            HJAction("join_selections"),
            HJAction("join_selections_space"),
            HJAction("keep_selections"),
            HJAction("remove_selections"),
            HJAction("align_selections"),
            HJAction("keep_primary_selection"),
            HJAction("remove_primary_selection"),
            HJAction("completion"),
            HJAction("hover") { _: Editor -> com.intellij.openapi.editor.actions.ShowHoverInfoAction() },
            HJAction("toggle_comments"),
            HJAction("toggle_line_comments"),
            HJAction("toggle_block_comments"),
            HJAction("rotate_selections_forward"),
            HJAction("rotate_selections_backward"),
            HJAction("rotate_selection_contents_forward"),
            HJAction("rotate_selection_contents_backward"),
            HJAction("reverse_selection_contents"),
            HJAction("expand_selection"),
            HJAction("shrink_selection"),
            HJAction("select_next_sibling"),
            HJAction("select_prev_sibling"),
            HJAction("select_all_siblings"),
            HJAction("select_all_children"),
            HJAction("jump_forward"),
            HJAction("jump_backward"),
            HJAction("save_selection"),
            HJAction("jump_view_right"),
            HJAction("jump_view_left"),
            HJAction("jump_view_up"),
            HJAction("jump_view_down"),
            HJAction("swap_view_right"),
            HJAction("swap_view_left"),
            HJAction("swap_view_up"),
            HJAction("swap_view_down"),
            HJAction("transpose_view"),
            HJAction("rotate_view"),
            HJAction("rotate_view_reverse"),
            HJAction("hsplit"),
            HJAction("hsplit_new"),
            HJAction("vsplit"),
            HJAction("vsplit_new"),
            HJAction("wclose"),
            HJAction("wonly"),
            HJAction("select_register"),
            HJAction("insert_register"),
            HJAction("copy_between_registers"),
            HJAction("align_view_middle"),
            HJAction("align_view_top"),
            HJAction("align_view_center"),
            HJAction("align_view_bottom"),
            HJAction("scroll_up"),
            HJAction("scroll_down"),
            HJAction("match_brackets"),
            HJAction("surround_add"),
            HJAction("surround_replace"),
            HJAction("surround_delete"),
            HJAction("select_textobject_around"),
            HJAction("select_textobject_inner"),
            HJAction("goto_next_function"),
            HJAction("goto_prev_function"),
            HJAction("goto_next_class"),
            HJAction("goto_prev_class"),
            HJAction("goto_next_parameter"),
            HJAction("goto_prev_parameter"),
            HJAction("goto_next_comment"),
            HJAction("goto_prev_comment"),
            HJAction("goto_next_test"),
            HJAction("goto_prev_test"),
            HJAction("goto_next_xml_element"),
            HJAction("goto_prev_xml_element"),
            HJAction("goto_next_entry"),
            HJAction("goto_prev_entry"),
            HJAction("goto_next_paragraph"),
            HJAction("goto_prev_paragraph"),
            HJAction("suspend"),
            HJAction("rename_symbol"),
            HJAction("increment"),
            HJAction("decrement"),
            HJAction("record_macro"),
            HJAction("replay_macro"),
            HJAction("command_palette"),
            HJAction("goto_word"),
            HJAction("extend_to_word"),
            HJAction("goto_next_tabstop"),
            HJAction("goto_prev_tabstop"),
            HJAction("rotate_selections_first"),
            HJAction("rotate_selections_last"),
        ))
        // @formatter:on

        return map
    }

    private fun defActions(map: MutableMap<String, HJAction>, actions: Array<HJAction>) {
        for (action in actions) {
            defAction(map, action)
        }
    }

    private fun defAction(map: MutableMap<String, HJAction>, action: HJAction) {
        if (map.containsKey(action.name)) {
            logger.warn("Action ${action.name} is already defined in actions")
            return
        }
        map[action.name] = action
    }
}

fun moveImpl(
    editor: Editor,
    charMovement: HJCharMovementKind,
    direction: HJMovementDirection,
    count: Int,
    extend: Boolean,
) {
    when (charMovement) {
        HJCharMovementKind.HORIZONTAL -> {
            moveHorizontally(editor.document, HJRange(editor), direction, count, extend).apply(editor)
        }

        HJCharMovementKind.VERTICAL -> {
//            val newPosition = try {
//                val position = editor.caretModel.logicalPosition
//                LogicalPosition(position.line + direction.value, position.column)
//            } catch (_: IllegalArgumentException) {
//                return
//            }
//            editor.caretModel.moveToLogicalPosition(newPosition)
            moveVertically(editor, HJRange(editor), direction, count, extend).apply(editor)
        }

        HJCharMovementKind.VERTICAL_VISUAL -> {
//            val newPosition = try {
//                val position = editor.caretModel.logicalPosition
//                VisualPosition(position.line + direction.value, position.column)
//            } catch (_: IllegalArgumentException) {
//                return
//            }
//            editor.caretModel.moveToVisualPosition(newPosition)
            moveVerticallyVisual(editor, HJRange(editor), direction, count, extend).apply(editor)
        }
    }
}

private fun moveCaretLogicalPosition(editor: Editor, deltaLine: Int, deltaColumn: Int) {
    val position = editor.caretModel.primaryCaret.logicalPosition
    val newPosition = try {
        LogicalPosition(position.line + deltaLine, position.column + deltaColumn)
    } catch (_: IllegalArgumentException) {
        return
    }

    if (HJEditorState.mode != HJEditorMode.SELECT) collapseSelection(editor)
    editor.caretModel.primaryCaret.moveToLogicalPosition(newPosition)
}

private fun moveCaretVisualPosition(editor: Editor, deltaLine: Int, deltaColumn: Int) {
    val position = editor.caretModel.primaryCaret.logicalPosition
    val newPosition = try {
        VisualPosition(position.line + deltaLine, position.column + deltaColumn)
    } catch (_: IllegalArgumentException) {
        return
    }

    if (HJEditorState.mode != HJEditorMode.SELECT) collapseSelection(editor)
    editor.caretModel.primaryCaret.moveToVisualPosition(newPosition)
}

private fun moveCaretDeltaOffset(editor: Editor, deltaOffset: Int): Int {
    var offset = editor.caretModel.primaryCaret.offset
    offset += deltaOffset
    moveCaretToOffset(editor, offset)
    return offset
}

private fun moveCaretToOffset(editor: Editor, offset: Int) {
    if (HJEditorState.mode != HJEditorMode.SELECT) collapseSelection(editor)
    editor.caretModel.primaryCaret.moveToOffset(offset)
}

fun switchToMode(editor: Editor, newMode: HJEditorMode) {
    if (HJEditorState.mode == newMode) return
    HJEditorState.mode = newMode
    EditorFactory.getInstance().allEditors.forEach { it.settings.isBlockCursor = newMode != HJEditorMode.INSERT }
    editor.project?.let {
        WindowManager.getInstance().getStatusBar(it)?.updateWidget(HJStatusBarModeWidget.ID)
    }
    if (newMode == HJEditorMode.SELECT) {
        extendSelection(editor)
    }
}

private fun collapseSelection(editor: Editor) {
    editor.selectionModel.removeSelection()
    if (HJEditorState.mode == HJEditorMode.SELECT) {
        extendSelection(editor)
    }
}

private fun deleteSelection(editor: Editor) {
    val start = editor.selectionModel.selectionStart
    val end = editor.selectionModel.selectionEnd
    WriteCommandAction.runWriteCommandAction(editor.project) {
        editor.document.deleteString(start, end)
    }
}

fun executeIdeAction(editor: Editor, actionId: String) {
    val action = ActionManager.getInstance().getAction(actionId) ?: return
    ActionManager.getInstance().tryToExecute(action, null, editor.contentComponent, ActionPlaces.UNKNOWN, true)
}
