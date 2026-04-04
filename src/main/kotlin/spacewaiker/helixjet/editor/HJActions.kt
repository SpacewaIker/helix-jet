@file:Suppress("BooleanLiteralArgument")

package spacewaiker.helixjet.editor

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.editor.HJKeyAction.HJAction
import spacewaiker.helixjet.input.HJInputHandler
import spacewaiker.helixjet.input.HJInputKey
import spacewaiker.helixjet.ui.HJPicker
import spacewaiker.helixjet.ui.HJPrompt
import spacewaiker.helixjet.ui.HJPromptEvent
import spacewaiker.helixjet.ui.HJStatusBarModeWidget
import javax.swing.table.AbstractTableModel
import kotlin.math.max
import kotlin.math.min

object HJActions {
    val logger = Logger.getInstance(this::class.java)

    private val actions: MutableMap<String, HJAction> = createActionsMap()
    val allActions get() = actions.values.toList()

    fun getAction(name: String): HJAction? {
        val ideAction = name.removeSurrounding("<", ">")
        if (ideAction != name) {
            actions[name] =
                HJAction(name, "IDE Action $ideAction") { cx: HJActionContext -> executeIdeAction(cx, ideAction) }
        }

        return actions[name]
    }

    private fun createActionsMap(): MutableMap<String, HJAction> {
        val map = mutableMapOf<String, HJAction>()

        // @formatter:off
        defActions(map, arrayOf(
            HJAction("no_op", "Do nothing") { _ -> },
            HJAction("move_char_left", "Move left") { moveImpl(it, HJCharMovementKind.HORIZONTAL, HJMovementDirection.BACKWARD, false) },
            HJAction("move_char_right") { cx -> moveImpl(cx, HJCharMovementKind.HORIZONTAL, HJMovementDirection.FORWARD, false) },
            HJAction("move_line_up") { cx -> moveImpl(cx, HJCharMovementKind.VERTICAL, HJMovementDirection.BACKWARD, false) },
            HJAction("move_line_down") { cx -> moveImpl(cx, HJCharMovementKind.VERTICAL, HJMovementDirection.FORWARD, false) },
            HJAction("move_visual_line_up") { cx -> moveImpl(cx, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.BACKWARD, false) },
            HJAction("move_visual_line_down") { cx -> moveImpl(cx, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.FORWARD, false) },
            HJAction("extend_char_left") { cx -> moveImpl(cx, HJCharMovementKind.HORIZONTAL, HJMovementDirection.BACKWARD, true) },
            HJAction("extend_char_right") { cx -> moveImpl(cx, HJCharMovementKind.HORIZONTAL, HJMovementDirection.FORWARD, true) },
            HJAction("extend_line_up") { cx -> moveImpl(cx, HJCharMovementKind.VERTICAL, HJMovementDirection.BACKWARD, true) },
            HJAction("extend_line_down") { cx -> moveImpl(cx, HJCharMovementKind.VERTICAL, HJMovementDirection.FORWARD, true) },
            HJAction("extend_visual_line_up") { cx -> moveImpl(cx, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.BACKWARD, true) },
            HJAction("extend_visual_line_down") { cx -> moveImpl(cx, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.FORWARD, true) },
            HJAction("copy_selection_on_next_line") { cx -> copySelectionOnLine(cx, HJMovementDirection.FORWARD) },
            HJAction("copy_selection_on_prev_line") { cx -> copySelectionOnLine(cx, HJMovementDirection.BACKWARD) },
            HJAction("move_next_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_WORD_START, false) },
            HJAction("move_prev_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_WORD_START, false) },
            HJAction("move_next_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_WORD_END, false) },
            HJAction("move_prev_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_WORD_END, false) },
            HJAction("move_next_long_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_LONG_WORD_START, false) },
            HJAction("move_prev_long_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_LONG_WORD_START, false) },
            HJAction("move_next_long_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_LONG_WORD_END, false) },
            HJAction("move_prev_long_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_LONG_WORD_END, false) },
            HJAction("move_next_sub_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_SUB_WORD_START, false) },
            HJAction("move_prev_sub_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_SUB_WORD_START, false) },
            HJAction("move_next_sub_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_SUB_WORD_END, false) },
            HJAction("move_prev_sub_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_SUB_WORD_END, false) },
            HJAction("move_parent_node_end"),
            HJAction("move_parent_node_start"),
            HJAction("extend_next_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_WORD_START, true) },
            HJAction("extend_prev_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_WORD_START, true) },
            HJAction("extend_next_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_WORD_END, true) },
            HJAction("extend_prev_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_WORD_END, true) },
            HJAction("extend_next_long_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_LONG_WORD_START, true) },
            HJAction("extend_prev_long_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_LONG_WORD_START, true) },
            HJAction("extend_next_long_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_LONG_WORD_END, true) },
            HJAction("extend_prev_long_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_LONG_WORD_END, true) },
            HJAction("extend_next_sub_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_SUB_WORD_START, true) },
            HJAction("extend_prev_sub_word_start") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_SUB_WORD_START, true) },
            HJAction("extend_next_sub_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.NEXT_SUB_WORD_END, true) },
            HJAction("extend_prev_sub_word_end") { cx -> moveWordImpl(cx, HJWordMotionTarget.PREV_SUB_WORD_END, true) },
            HJAction("extend_parent_node_end"),
            HJAction("extend_parent_node_start"),
            HJAction("find_till_char") { cx -> findChar(cx,  HJMovementDirection.FORWARD, false, false) },
            HJAction("find_next_char") { cx -> findChar(cx,  HJMovementDirection.FORWARD, true, false) },
            HJAction("extend_till_char") { cx -> findChar(cx,  HJMovementDirection.FORWARD, false, true) },
            HJAction("extend_next_char") { cx -> findChar(cx,  HJMovementDirection.FORWARD, true, true) },
            HJAction("till_prev_char") { cx -> findChar(cx,  HJMovementDirection.BACKWARD, false, false) },
            HJAction("find_prev_char") { cx -> findChar(cx,  HJMovementDirection.BACKWARD, true, false) },
            HJAction("extend_till_prev_char") { cx -> findChar(cx,  HJMovementDirection.BACKWARD, false, true) },
            HJAction("extend_prev_char") { cx -> findChar(cx,  HJMovementDirection.BACKWARD, true, true) },
            HJAction("repeat_last_motion") { cx -> cx.hjEditor.repeatLastMotion(1) },
            HJAction("replace") { cx -> replace(cx) },
            HJAction("switch_case") { cx -> switchCaseImpl(cx) { s -> s.map { c -> if (c.isLowerCase()) {c.uppercase()} else if (c.isUpperCase()) {c.lowercase()} else {c} }.joinToString(separator = "") } },
            HJAction("switch_to_uppercase") { cx -> switchCaseImpl(cx) { s -> s.uppercase() } },
            HJAction("switch_to_lowercase") { cx -> switchCaseImpl(cx) { s -> s.lowercase() } },
            HJAction("page_up") { cx -> scrollPageImpl(cx, 1.0f, HJMovementDirection.BACKWARD) },
            HJAction("page_down") { cx -> scrollPageImpl(cx, 1.0f, HJMovementDirection.FORWARD) },
            HJAction("half_page_up") { cx -> scrollPageImpl(cx, 0.5f, HJMovementDirection.BACKWARD) },
            HJAction("half_page_down") { cx -> scrollPageImpl(cx, 0.5f, HJMovementDirection.FORWARD) },
            HJAction("page_cursor_up"),
            HJAction("page_cursor_down"),
            HJAction("page_cursor_half_up"),
            HJAction("page_cursor_half_down"),
            HJAction("select_all") { cx -> selectAll(cx) },
            HJAction("select_regex") { cx -> selectRegex(cx) },
            HJAction("split_selection") { cx -> splitSelection(cx) },
            HJAction("split_selection_on_newline") { cx -> splitSelectionOnNewline(cx) },
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
            HJAction("extend_line") { cx -> extendLine(cx) },
            HJAction("extend_line_below") { cx -> extendImpl(cx, true) },
            HJAction("extend_line_above") { cx -> extendImpl(cx, false) },
            HJAction("select_line_above"),
            HJAction("select_line_below"),
            HJAction("extend_to_line_bounds"),
            HJAction("shrink_to_line_bounds"),
            HJAction("delete_selection") { cx -> deleteSelectionImpl(cx, false, true) },
            HJAction("delete_selection_noyank") { cx -> deleteSelectionImpl(cx, false, false) },
            HJAction("change_selection") { cx -> deleteSelectionImpl(cx, true, true) },
            HJAction("change_selection_noyank") { cx -> deleteSelectionImpl(cx, true, false) },
            HJAction("collapse_selection") { cx -> collapseSelection(cx) },
            HJAction("flip_selections"),
            HJAction("ensure_selections_forward"),
            HJAction("insert_mode") { cx -> insertMode(cx, false) },
            HJAction("append_mode") { cx -> insertMode(cx, true) },
            HJAction("command_mode") { cx -> commandMode(cx) },
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
            HJAction("normal_mode") { cx -> switchToMode(cx, HJEditorMode.NORMAL) },
            HJAction("select_mode") { cx -> switchToMode(cx, HJEditorMode.SELECT) },
            HJAction("exit_select_mode") { cx -> if (HJState.mode == HJEditorMode.SELECT) switchToMode(cx, HJEditorMode.NORMAL) },
            HJAction("goto_definition"),
            HJAction("goto_declaration"),
            HJAction("add_newline_above"),
            HJAction("add_newline_below"),
            HJAction("goto_type_definition"),
            HJAction("goto_implementation"),
            HJAction("goto_file_start") { cx -> gotoFileStartImpl(cx, false) },
            HJAction("goto_file_end") { cx -> gotoFileEndImpl(cx, false) },
            HJAction("extend_to_file_start") { cx -> gotoFileStartImpl(cx, true) },
            HJAction("extend_to_file_end") { cx -> gotoFileEndImpl(cx, true) },
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
            HJAction("goto_line") { cx -> gotoLineImpl(cx, false) },
            HJAction("goto_last_line") { cx -> gotoLastLineImpl(cx, false) },
            HJAction("extend_to_last_line") { cx -> gotoLastLineImpl(cx, true) },
            HJAction("goto_first_diag"),
            HJAction("goto_last_diag"),
            HJAction("goto_next_diag"),
            HJAction("goto_prev_diag"),
            HJAction("goto_next_change"),
            HJAction("goto_prev_change"),
            HJAction("goto_first_change"),
            HJAction("goto_last_change"),
            HJAction("goto_line_start") { cx -> gotoLineStartEnd(cx, true) },
            HJAction("goto_line_end") { cx -> gotoLineStartEnd(cx, false) },
            HJAction("goto_column"),
            HJAction("extend_to_column"),
            HJAction("goto_next_buffer"),
            HJAction("goto_previous_buffer"),
            HJAction("goto_line_end_newline") { cx -> gotoLineEndNewline(cx) },
            HJAction("goto_first_nonwhitespace") { cx -> gotoFirstNonWhitespace(cx) },
            HJAction("trim_selections"),
            HJAction("extend_to_line_start") { cx -> gotoLineStartEndImpl(cx, true, true) },
            HJAction("extend_to_first_nonwhitespace") { cx -> gotoFirstNonWhitespaceImpl(cx, true) },
            HJAction("extend_to_line_end") { cx -> gotoLineStartEndImpl(cx, false, true) },
            HJAction("extend_to_line_end_newline") { cx -> gotoLineEndNewlineImpl(cx, true) },
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
            HJAction("undo") { cx -> executeIdeAction(cx, IdeActions.ACTION_UNDO) },
            HJAction("redo") { cx -> executeIdeAction(cx, IdeActions.ACTION_REDO) },
            HJAction("earlier"),
            HJAction("later"),
            HJAction("commit_undo_checkpoint"),
            HJAction("yank") { cx -> yank(cx) },
            HJAction("yank_to_clipboard") { cx -> yankImpl(cx.hjEditor.editor.document, cx.hjEditor.selection, HJRegister.Clipboard) },
            HJAction("yank_joined"),
            HJAction("yank_joined_to_clipboard"),
            HJAction("yank_main_selection_to_clipboard"),
            HJAction("replace_with_yanked"),
            HJAction("replace_selections_with_clipboard"),
            HJAction("paste_after") { cx -> pasteImpl(cx, false) },
            HJAction("paste_before") { cx -> pasteImpl(cx, true) },
            HJAction("paste_clipboard_after") { cx -> pasteImpl(cx.copy(register = HJRegister.Clipboard), false) },
            HJAction("paste_clipboard_before") { cx -> pasteImpl(cx.copy(register = HJRegister.Clipboard), true) },
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
            HJAction("keep_primary_selection") { cx -> keepPrimarySelection(cx) },
            HJAction("remove_primary_selection"),
            HJAction("completion"),
            HJAction("hover") { cx -> executeIdeAction(cx, "ShowHoverInfo") },
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
            HJAction("command_palette") { cx -> commandPalette(cx) },
            HJAction("goto_word"),
            HJAction("extend_to_word"),
            HJAction("goto_next_tabstop"),
            HJAction("goto_prev_tabstop"),
            HJAction("rotate_selections_first"),
            HJAction("rotate_selections_last"),
            HJAction("debug") { cx -> cx.hjEditor.editor.document.charsSequence.forEach { c: Char -> println(c.code)}}
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

private fun moveImpl(
    cx: HJActionContext,
    charMovement: HJCharMovementKind,
    direction: HJMovementDirection,
    extend: Boolean,
) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        when (charMovement) {
            HJCharMovementKind.HORIZONTAL -> moveHorizontally(
                cx.hjEditor.editor.document,
                range,
                direction,
                cx.count,
                extend
            )

            HJCharMovementKind.VERTICAL -> moveVertically(cx.hjEditor.editor, range, direction, cx.count, extend)
            HJCharMovementKind.VERTICAL_VISUAL -> moveVerticallyVisual(
                cx.hjEditor.editor,
                range,
                direction,
                cx.count,
                extend
            )
        }
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun moveWordImpl(cx: HJActionContext, target: HJWordMotionTarget, extend: Boolean) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val newRange = wordMove(cx.hjEditor.editor.document, range, target, cx.count)
        if (extend) {
            val pos = newRange.cursor
            range.putCursor(cx.hjEditor.editor.document, pos, true)
        } else {
            newRange
        }
    }
    cx.hjEditor.setNewSelection(selection)
}

fun switchToMode(cx: HJActionContext, newMode: HJEditorMode) {
    if (HJState.mode == newMode) return
    HJState.mode = newMode

    if (newMode == HJEditorMode.INSERT)
        collapseSelection(cx)

    HJState.forAllEditors { it.reloadCaretShapes() }

    cx.hjEditor.editor.project?.let {
        WindowManager.getInstance().getStatusBar(it)?.updateWidget(HJStatusBarModeWidget.ID)
    }
}

private fun insertMode(cx: HJActionContext, append: Boolean) {
    val selection = cx.hjEditor.selection.clone().transform {
        val cursor = if (append) it.to else it.from
        HJRange.point(cursor)
    }
    cx.hjEditor.setNewSelection(selection)
    switchToMode(cx, HJEditorMode.INSERT)
}

private fun commandMode(cx: HJActionContext) {
    val onEvent = { e: HJPromptEvent, s: String? -> if (e == HJPromptEvent.Validate) println(s) }
    val commandBar = HJPrompt(cx.hjEditor.editor, onEvent, historyRegister = HJRegister.LastCmd)
    commandBar.show()
}

private fun collapseSelection(cx: HJActionContext) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val pos = range.cursor
        HJRange.point(pos)
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun deleteSelectionImpl(cx: HJActionContext, change: Boolean, yank: Boolean) {
    // TODO: correct implementation following helix -> if change and only_whole_lines then open ??

    if (yank) {
        yank(cx)
    }

    val transaction = cx.hjEditor.changeBySelection { range -> HJChange.Delete(range.from, range.to) }
    cx.hjEditor.apply(transaction)
    switchToMode(cx, if (change) HJEditorMode.INSERT else HJEditorMode.NORMAL)
}

private fun yank(cx: HJActionContext) {
    yankImpl(cx.hjEditor.editor.document, cx.hjEditor.selection, cx.register ?: HJRegister.LastYank)
}

private fun yankImpl(doc: Document, selection: HJSelection, register: HJRegister) {
    // TODO: full, correct implementation
    HJState.copyToRegister(doc, selection, register)
}

private fun pasteImpl(cx: HJActionContext, pasteBefore: Boolean) {
    // TODO: full, correct implementation
    val registerContents = HJState.getRegisterContents(cx.register ?: HJRegister.LastYank)

    if (registerContents == null) {
        return
    }

    val transaction = cx.hjEditor.changeBySelectionIndexed { idx, range ->
        val pos = if (pasteBefore) range.from else range.to
        val string = registerContents[idx.coerceAtMost(registerContents.size - 1)]
        HJChange.Insert(pos, string)
    }
    cx.hjEditor.apply(transaction)
}

private fun executeIdeAction(cx: HJActionContext, actionId: String) {
    val action = ActionManager.getInstance().getAction(actionId) ?: return
    repeat(cx.count) {
        ActionManager.getInstance()
            .tryToExecute(action, null, cx.hjEditor.editor.contentComponent, ActionPlaces.UNKNOWN, true)
    }
}

private fun findChar(cx: HJActionContext, direction: HJMovementDirection, inclusive: Boolean, extend: Boolean) {
    HJInputHandler.onNextKey { key: HJInputKey ->
        val motion = when (key) {
            is HJInputKey.Enter, is HJInputKey.Tab, is HJInputKey.Character -> {
                // contrary to Helix, where newlines can be different characters, here we only have to deal with \n when
                // reading from a Document.
                val ch = if (key is HJInputKey.Character) key.c else if (key == HJInputKey.Enter) '\n' else '\t'
                { hjEditor: HJEditor ->
                    val selection = hjEditor.selection.clone().transform { range ->
                        val cursorAnchor = range.cursor
                        val cursorHead = nextGraphemeBoundary(cx.hjEditor.editor.document, cursorAnchor)

                        // exclusive search skips the next char after cursor to enable repeated application
                        val searchStartPos = when (Pair(inclusive, direction)) {
                            Pair(true, HJMovementDirection.FORWARD) -> cursorHead
                            Pair(true, HJMovementDirection.BACKWARD) -> cursorAnchor
                            Pair(false, HJMovementDirection.FORWARD) -> cursorHead + 1
                            Pair(false, HJMovementDirection.BACKWARD) -> cursorAnchor - 1
                            else -> { // Should never happen
                                HJActions.logger.error("Impossible condition")
                                0
                            }
                        }

                        var pos = findNthChar(
                            cx.hjEditor.editor.document,
                            HJCharMatcher.Character(ch),
                            searchStartPos,
                            cx.count,
                            direction
                        )
                        if (pos != null) {
                            // exclusive search should stop on previous character
                            pos = when (Pair(inclusive, direction)) {
                                Pair(true, HJMovementDirection.FORWARD) -> pos
                                Pair(true, HJMovementDirection.BACKWARD) -> pos
                                Pair(false, HJMovementDirection.FORWARD) -> pos - 1
                                Pair(false, HJMovementDirection.BACKWARD) -> pos + 1
                                else -> { // Should never happen
                                    HJActions.logger.error("Impossible condition")
                                    0
                                }
                            }
                        }

                        if (pos == null) range else {
                            if (extend) {
                                range.putCursor(hjEditor.editor.document, pos, true)
                            } else {
                                HJRange.point(range.cursor).putCursor(hjEditor.editor.document, pos, true)
                            }
                        }
                    }
                    hjEditor.setNewSelection(selection)
                }
            }

            else -> null
        }

        motion?.let { cx.hjEditor.applyMotion(it) }
    }
}

private fun replace(cx: HJActionContext) {
    HJInputHandler.onNextKey { key: HJInputKey ->
        val char = when (key) {
            is HJInputKey.Character -> key.c
            is HJInputKey.Enter -> '\n'
            is HJInputKey.Tab -> '\t'
            else -> null
        }

        if (char != null) {
            val hjEditor = HJState.getHJEditor(cx.hjEditor.editor)
            val transaction = hjEditor.changeBySelection { range ->
                if (range.isEmpty) {
                    // no change
                    null
                } else {
                    val new = char.toString().repeat(range.len)
                    HJChange.Replace(range.from, range.to, new)
                }
            }
            hjEditor.apply(transaction)
            switchToMode(cx, HJEditorMode.NORMAL)
        }
    }
}

private fun copySelectionOnLine(cx: HJActionContext, direction: HJMovementDirection) {
    val editor = cx.hjEditor.editor
    val doc = cx.hjEditor.editor.document

    val selection = cx.hjEditor.selection
    val ranges = selection.ranges.toMutableList()
    var primaryIndex = 0

    for (range in selection.ranges) {
        val isPrimary = range == selection.primary

        // the range is always head exclusive
        val (head, anchor) = if (range.anchor < range.head)
            Pair(range.head - 1, range.anchor)
        else
            Pair(range.head, (range.anchor - 1).coerceAtLeast(0))

        val headPos = editor.offsetToVisualPosition(head)
        val anchorPos = editor.offsetToVisualPosition(anchor)

        val height = max(headPos.line, anchorPos.line) - min(headPos.line, anchorPos.line) + 1

        if (isPrimary) {
            primaryIndex = ranges.size
        }

        ranges.add(range)

        var sels = 0
        var i = 0
        while (sels < cx.count) {
            val offset = (i + 1) * height

            val anchorRow = when (direction) {
                HJMovementDirection.FORWARD -> anchorPos.line + offset
                HJMovementDirection.BACKWARD -> (anchorPos.line - offset).coerceAtLeast(0)
            }
            val headRow = when (direction) {
                HJMovementDirection.FORWARD -> headPos.line + offset
                HJMovementDirection.BACKWARD -> (headPos.line - offset).coerceAtLeast(0)
            }

            if (anchorRow >= doc.lineCount || headRow >= doc.lineCount)
                break

            // skip lines that are too short
            try {
                val anchor = editor.visualPositionToOffset(VisualPosition(anchorRow, anchorPos.column))
                val head = editor.visualPositionToOffset(VisualPosition(headRow, headPos.column))

                if (isPrimary) {
                    primaryIndex = ranges.size
                }

                ranges.add(HJRange.point(anchor).putCursor(doc, head, true))
                sels++
            } catch (_: IllegalArgumentException) {
                // ignore
            }

            if (anchorRow == 0 && headRow == 0)
                break

            i++
        }
    }

    val newSelection = HJSelection(ranges, primaryIndex)
    cx.hjEditor.setNewSelection(newSelection)
}

private fun keepPrimarySelection(cx: HJActionContext) {
    val range = cx.hjEditor.selection.primary
    cx.hjEditor.setNewSelection(HJSelection(range))
}

private fun switchCaseImpl(cx: HJActionContext, switchFun: (String) -> String) {
    val doc = cx.hjEditor.editor.document
    val transaction = cx.hjEditor.changeBySelection { range ->
        val text = doc.getText(TextRange(range.from, range.to))
        val newText = switchFun(text)
        HJChange.Replace(range.from, range.to, newText)
    }
    cx.hjEditor.apply(transaction)
    switchToMode(cx, HJEditorMode.NORMAL)
}

private fun scrollPageImpl(cx: HJActionContext, multiplier: Float, direction: HJMovementDirection) {
    val scrollModel = cx.hjEditor.editor.scrollingModel
    val area = scrollModel.visibleArea
    val deltaOffset = (area.height * multiplier).toInt()
    val newOffset = scrollModel.verticalScrollOffset + deltaOffset * direction.value
    scrollModel.scrollVertically(newOffset)
}

private fun selectAll(cx: HJActionContext) {
    val last = cx.hjEditor.editor.document.textLength
    cx.hjEditor.setNewSelection(HJSelection(HJRange(0, last)))
}

private fun selectRegex(cx: HJActionContext) {
    val snapshot = cx.hjEditor.selection.clone()
    val onEvent = { e: HJPromptEvent, regex: String? ->
        when (e) {
            HJPromptEvent.Abort -> {
                cx.hjEditor.setNewSelection(snapshot)
            }

            else if regex != null -> {
                if (regex.isEmpty()) {
                    cx.hjEditor.setNewSelection(snapshot)
                } else {
                    val doc = cx.hjEditor.editor.document
                    val selection = HJSelection.selectOnMatches(doc, snapshot, regex)
                    if (selection != null) {
                        cx.hjEditor.setNewSelection(selection)
                    } else if (e == HJPromptEvent.Validate) {
                        // TODO: User facing warning
                        HJActions.logger.debug("selectRegex - nothing is selected")
                    }
                }
            }

            else -> {}
        }
    }
    val commandBar = HJPrompt(cx.hjEditor.editor, onEvent, "select:", HJRegister.LastSearch)
    commandBar.show()
}

private fun splitSelection(cx: HJActionContext) {
    val snapshot = cx.hjEditor.selection.clone()
    val onEvent = { e: HJPromptEvent, regex: String? ->
        when (e) {
            HJPromptEvent.Abort -> {
                cx.hjEditor.setNewSelection(snapshot)
            }

            else if regex != null -> {
                if (regex.isEmpty()) {
                    cx.hjEditor.setNewSelection(snapshot)
                } else {
                    val doc = cx.hjEditor.editor.document
                    val selection = HJSelection.splitOnMatches(doc, snapshot, regex)
                    cx.hjEditor.setNewSelection(selection)
                }
            }

            else -> {}
        }
    }
    val commandBar = HJPrompt(cx.hjEditor.editor, onEvent, "split:", HJRegister.LastSearch)
    commandBar.show()
}

private fun splitSelectionOnNewline(cx: HJActionContext) {
    val selection = HJSelection.splitOnNewline(cx.hjEditor.editor.document, cx.hjEditor.selection)
    cx.hjEditor.setNewSelection(selection)
}

private fun extendLine(cx: HJActionContext) {
    val below = cx.hjEditor.selection.primary.direction == HJMovementDirection.FORWARD
    extendImpl(cx, below)
}

private fun extendImpl(cx: HJActionContext, below: Boolean) {
    val lastLine = (cx.doc.lineCount - 1).coerceAtLeast(0)

    val selection = cx.hjEditor.selection.clone().transform { range ->
        val (startLine, endLine) = range.lineRange(cx.doc)

        val start = cx.doc.getLineStartOffset(startLine)
        val end = 1 + cx.doc.getLineEndOffset(endLine.coerceAtMost(lastLine))

        val (anchor, head) = if (range.from == start && range.to == end) {
            if (below) {
                Pair(start, 1 + cx.doc.getLineEndOffset((endLine + cx.count).coerceAtMost(lastLine)))
            } else {
                Pair(end, cx.doc.getLineStartOffset((startLine - cx.count).coerceAtLeast(0)))
            }
        } else {
            if (below) {
                Pair(start, 1 + cx.doc.getLineEndOffset((endLine + cx.count - 1).coerceAtMost(lastLine)))
            } else {
                Pair(end, cx.doc.getLineStartOffset((startLine - (cx.count - 1)).coerceAtLeast(0)))
            }
        }

        // helix handles the above only with "lineStartOffset". If we do this here, we can't select the last line of a file
        // so instead we opt for line start and line end + 1. That means we need to check that we're not trying to select
        // past the very last character
        val lastOffset = cx.doc.textLength // last caret position is last char + 1
        HJRange(anchor, head.coerceAtMost(lastOffset))
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun gotoLineImpl(cx: HJActionContext, extend: Boolean) {
    if (cx.rawCount != null) {
        val maxLine =
            if (cx.doc.getLineEndOffset(cx.doc.lineCount - 1) == cx.doc.getLineStartOffset(cx.doc.lineCount - 1)) {
                // if the last line is blank, don't jump to it
                (cx.doc.lineCount - 2).coerceAtLeast(0)
            } else cx.doc.lineCount - 1

        val lineIdx = (cx.count - 1).coerceAtMost(maxLine)
        val pos = cx.doc.getLineStartOffset(lineIdx)
        val selection = cx.hjEditor.selection.clone().transform { it.putCursor(cx.doc, pos, extend) }
        cx.hjEditor.setNewSelection(selection)
    }
}

private fun gotoFileStartImpl(cx: HJActionContext, extend: Boolean) {
    if (cx.rawCount != null) {
        gotoLineImpl(cx, extend)
    } else {
        val selection = cx.hjEditor.selection.clone().transform { it.putCursor(cx.doc, 0, extend) }
        cx.hjEditor.setNewSelection(selection)
    }
}

private fun gotoFileEndImpl(cx: HJActionContext, extend: Boolean) {
    val end = cx.doc.textLength
    val selection = cx.hjEditor.selection.clone().transform { it.putCursor(cx.doc, end, extend) }
    cx.hjEditor.setNewSelection(selection)
}

private fun gotoLastLineImpl(cx: HJActionContext, extend: Boolean) {
    val lastLine = cx.doc.lineCount
    gotoLineImpl(cx.copy(rawCount = lastLine), extend)
}

private fun gotoLineStartEndImpl(cx: HJActionContext, start: Boolean, extend: Boolean) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val line = cx.doc.getLineNumber(range.cursor)
        // adjust to start/end of the line
        val lineStart = cx.doc.getLineStartOffset(line)
        val pos = if (start) lineStart else {
            prevGraphemeBoundary(cx.doc.getLineEndOffset(line)).coerceAtLeast(lineStart)
        }
        range.putCursor(cx.doc, pos, extend)
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun gotoLineStartEnd(cx: HJActionContext, start: Boolean) {
    gotoLineStartEndImpl(cx, start, HJState.mode == HJEditorMode.SELECT)
}

private fun gotoFirstNonWhitespaceImpl(cx: HJActionContext, extend: Boolean) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val line = cx.doc.getLineNumber(range.cursor)
        val pos = firstNonWhitespaceChar(getLine(cx.doc, line)!!, cx.doc.getLineStartOffset(line))
        if (pos == null) {
            range
        } else {
            range.putCursor(cx.doc, pos, extend)
        }
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun gotoFirstNonWhitespace(cx: HJActionContext) {
    gotoFirstNonWhitespaceImpl(cx, HJState.mode == HJEditorMode.SELECT)
}

private fun gotoLineEndNewlineImpl(cx: HJActionContext, extend: Boolean) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val line = cx.doc.getLineNumber(range.cursor)
        val pos = cx.doc.getLineEndOffset(line)

        range.putCursor(cx.doc, pos, extend)
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun gotoLineEndNewline(cx: HJActionContext) {
    gotoLineEndNewlineImpl(cx, HJState.mode == HJEditorMode.SELECT)
}

private fun commandPalette(cx: HJActionContext) {
    val data = HJState.config.allActionsWithBindings(HJState.mode).filterNot { (action, _) -> action.name == "no_op" }

    val model = object : AbstractTableModel() {
        override fun getRowCount() = data.size
        override fun getColumnCount() = 3

        override fun getValueAt(rowIndex: Int, columnIndex: Int) = when (columnIndex) {
            0 -> data[rowIndex].first.name
            1 -> data[rowIndex].second.joinToString(" ") { keys -> keys.joinToString("") }
            2 -> data[rowIndex].first.doc
            else -> ""
        }

        override fun getColumnName(column: Int) = when (column) {
            0 -> "Name"
            1 -> "Bindings"
            2 -> "Doc"
            else -> ""
        }
    }

    HJPicker("HelixJet Commands", model) { rowIdx ->
        val action = data[rowIdx].first
        action.execute(cx)
    }.show(cx.hjEditor.editor.project!!)
}