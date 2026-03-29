@file:Suppress("BooleanLiteralArgument")

package spacewaiker.helixjet.editor

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.input.HJInputHandler
import spacewaiker.helixjet.input.HJInputKey
import spacewaiker.helixjet.ui.HJStatusBarModeWidget
import kotlin.math.max
import kotlin.math.min


class HJAction {
    constructor(name: String, action: ((HJActionContext) -> Unit)? = null) {
        this.name = name
        this.action = action
    }

    fun execute(cx: HJActionContext) {
        if (action == null) {
            HJActions.logger.warn("Action $name is not yet defined")
        } else {
            HJActions.logger.debug("Executing action $name")
            action(cx)
        }
    }

    val name: String

    private val action: ((HJActionContext) -> Unit)?
}

data class HJActionContext(
    val hjEditor: HJEditor,
    val count: Int = 1,
    val register: HJRegister? = null,
)

object HJActions {
    val logger = Logger.getInstance(this::class.java)

    private val actions: MutableMap<String, HJAction> = createActionsMap()

    fun getAction(name: String): HJAction? {
        val ideAction = name.removeSurrounding("<", ">")
        if (ideAction != name) {
            actions[name] = HJAction(name) { cx: HJActionContext -> executeIdeAction(cx, ideAction) }
        }

        return actions[name]
    }

    private fun createActionsMap(): MutableMap<String, HJAction> {
        val map = mutableMapOf<String, HJAction>()

        // @formatter:off
        defActions(map, arrayOf(
            HJAction("no_op") { _ -> },
            HJAction("move_char_left") { cx -> moveImpl(cx, HJCharMovementKind.HORIZONTAL, HJMovementDirection.BACKWARD, false) },
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
            HJAction("delete_selection") { cx -> deleteSelectionImpl(cx, false, true) },
            HJAction("delete_selection_noyank") { cx -> deleteSelectionImpl(cx, false, false) },
            HJAction("change_selection") { cx -> deleteSelectionImpl(cx, true, true) },
            HJAction("change_selection_noyank") { cx -> deleteSelectionImpl(cx, true, false) },
            HJAction("collapse_selection") { cx -> collapseSelection(cx) },
            HJAction("flip_selections"),
            HJAction("ensure_selections_forward"),
            HJAction("insert_mode") { cx -> switchToMode(cx, HJEditorMode.INSERT) },
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
            HJAction("normal_mode") { cx -> switchToMode(cx, HJEditorMode.NORMAL) },
            HJAction("select_mode") { cx -> switchToMode(cx, HJEditorMode.SELECT) },
            HJAction("exit_select_mode") { cx -> if (HJState.mode == HJEditorMode.SELECT) switchToMode(cx, HJEditorMode.NORMAL) },
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
            HJAction("command_palette"),
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

    EditorFactory.getInstance().allEditors.forEach { HJState.getHJEditor(it).reloadCaretShapes() }

    cx.hjEditor.editor.project?.let {
        WindowManager.getInstance().getStatusBar(it)?.updateWidget(HJStatusBarModeWidget.ID)
    }
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
        val string = registerContents[min(idx, registerContents.size - 1)]
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
            Pair(range.head, max(0, range.anchor - 1))

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
                HJMovementDirection.BACKWARD -> max(0, anchorPos.line - offset)
            }
            val headRow = when (direction) {
                HJMovementDirection.FORWARD -> headPos.line + offset
                HJMovementDirection.BACKWARD -> max(0, headPos.line - offset)
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
