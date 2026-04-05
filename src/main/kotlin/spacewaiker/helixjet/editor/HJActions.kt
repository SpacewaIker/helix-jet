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
import java.awt.event.KeyEvent
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
            HJAction("move_char_right", "Move right") { moveImpl(it, HJCharMovementKind.HORIZONTAL, HJMovementDirection.FORWARD, false) },
            HJAction("move_line_up", "Move up") { moveImpl(it, HJCharMovementKind.VERTICAL, HJMovementDirection.BACKWARD, false) },
            HJAction("move_line_down", "Move down") { moveImpl(it, HJCharMovementKind.VERTICAL, HJMovementDirection.FORWARD, false) },
            HJAction("move_visual_line_up", "Move up") { moveImpl(it, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.BACKWARD, false) },
            HJAction("move_visual_line_down", "Move down") { moveImpl(it, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.FORWARD, false) },
            HJAction("extend_char_left", "Extend left") { moveImpl(it, HJCharMovementKind.HORIZONTAL, HJMovementDirection.BACKWARD, true) },
            HJAction("extend_char_right", "Extend right") { moveImpl(it, HJCharMovementKind.HORIZONTAL, HJMovementDirection.FORWARD, true) },
            HJAction("extend_line_up", "Extend up") { moveImpl(it, HJCharMovementKind.VERTICAL, HJMovementDirection.BACKWARD, true) },
            HJAction("extend_line_down", "Extend down") { moveImpl(it, HJCharMovementKind.VERTICAL, HJMovementDirection.FORWARD, true) },
            HJAction("extend_visual_line_up", "Extend up") { moveImpl(it, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.BACKWARD, true) },
            HJAction("extend_visual_line_down", "Extend down") { moveImpl(it, HJCharMovementKind.VERTICAL_VISUAL, HJMovementDirection.FORWARD, true) },
            HJAction("copy_selection_on_next_line", "Copy selection on next line") { copySelectionOnLine(it, HJMovementDirection.FORWARD) },
            HJAction("copy_selection_on_prev_line", "Copy selection on previous line") { copySelectionOnLine(it, HJMovementDirection.BACKWARD) },
            HJAction("move_next_word_start", "Move to start of next word") { moveWordImpl(it, HJWordMotionTarget.NEXT_WORD_START, false) },
            HJAction("move_prev_word_start", "Move to start of previous word") { moveWordImpl(it, HJWordMotionTarget.PREV_WORD_START, false) },
            HJAction("move_next_word_end", "Move to end of next word") { moveWordImpl(it, HJWordMotionTarget.NEXT_WORD_END, false) },
            HJAction("move_prev_word_end", "Move to end of previous word") { moveWordImpl(it, HJWordMotionTarget.PREV_WORD_END, false) },
            HJAction("move_next_long_word_start", "Move to start of next long word") { moveWordImpl(it, HJWordMotionTarget.NEXT_LONG_WORD_START, false) },
            HJAction("move_prev_long_word_start", "Move to start of previous long word") { moveWordImpl(it, HJWordMotionTarget.PREV_LONG_WORD_START, false) },
            HJAction("move_next_long_word_end", "Move to end of next long word") { moveWordImpl(it, HJWordMotionTarget.NEXT_LONG_WORD_END, false) },
            HJAction("move_prev_long_word_end", "Move to end of previous long word") { moveWordImpl(it, HJWordMotionTarget.PREV_LONG_WORD_END, false) },
            HJAction("move_next_sub_word_start", "Move to start of next sub word") { moveWordImpl(it, HJWordMotionTarget.NEXT_SUB_WORD_START, false) },
            HJAction("move_prev_sub_word_start", "Move to start of previous sub word") { moveWordImpl(it, HJWordMotionTarget.PREV_SUB_WORD_START, false) },
            HJAction("move_next_sub_word_end", "Move to end of next sub word") { moveWordImpl(it, HJWordMotionTarget.NEXT_SUB_WORD_END, false) },
            HJAction("move_prev_sub_word_end", "Move to end of previous sub word") { moveWordImpl(it, HJWordMotionTarget.PREV_SUB_WORD_END, false) },
            HJAction("move_parent_node_end", "Move to end of the parent node"),
            HJAction("move_parent_node_start", "Move to beginning of the parent node"),
            HJAction("extend_next_word_start", "Extend to start of next word") { moveWordImpl(it, HJWordMotionTarget.NEXT_WORD_START, true) },
            HJAction("extend_prev_word_start", "Extend to start of previous word") { moveWordImpl(it, HJWordMotionTarget.PREV_WORD_START, true) },
            HJAction("extend_next_word_end", "Extend to end of next word") { moveWordImpl(it, HJWordMotionTarget.NEXT_WORD_END, true) },
            HJAction("extend_prev_word_end", "Extend to end of previous word") { moveWordImpl(it, HJWordMotionTarget.PREV_WORD_END, true) },
            HJAction("extend_next_long_word_start", "Extend to start of next long word") { moveWordImpl(it, HJWordMotionTarget.NEXT_LONG_WORD_START, true) },
            HJAction("extend_prev_long_word_start", "Extend to start of previous long word") { moveWordImpl(it, HJWordMotionTarget.PREV_LONG_WORD_START, true) },
            HJAction("extend_next_long_word_end", "Extend to end of next long word") { moveWordImpl(it, HJWordMotionTarget.NEXT_LONG_WORD_END, true) },
            HJAction("extend_prev_long_word_end", "Extend to end of prev long word") { moveWordImpl(it, HJWordMotionTarget.PREV_LONG_WORD_END, true) },
            HJAction("extend_next_sub_word_start", "Extend to start of next sub word") { moveWordImpl(it, HJWordMotionTarget.NEXT_SUB_WORD_START, true) },
            HJAction("extend_prev_sub_word_start", "Extend to start of previous sub word") { moveWordImpl(it, HJWordMotionTarget.PREV_SUB_WORD_START, true) },
            HJAction("extend_next_sub_word_end", "Extend to end of next sub word") { moveWordImpl(it, HJWordMotionTarget.NEXT_SUB_WORD_END, true) },
            HJAction("extend_prev_sub_word_end", "Extend to end of prev sub word") { moveWordImpl(it, HJWordMotionTarget.PREV_SUB_WORD_END, true) },
            HJAction("extend_parent_node_end", "Extend to end of the parent node"),
            HJAction("extend_parent_node_start", "Extend to beginning of the parent node"),
            HJAction("find_till_char", "Move till next occurrence of char") { findChar(it,  HJMovementDirection.FORWARD, false, false) },
            HJAction("find_next_char", "Move to next occurrence of char") { findChar(it,  HJMovementDirection.FORWARD, true, false) },
            HJAction("extend_till_char", "Extend till next occurrence of char") { findChar(it,  HJMovementDirection.FORWARD, false, true) },
            HJAction("extend_next_char", "Extend to next occurrence of char") { findChar(it,  HJMovementDirection.FORWARD, true, true) },
            HJAction("till_prev_char", "Move till previous occurrence of char") { findChar(it,  HJMovementDirection.BACKWARD, false, false) },
            HJAction("find_prev_char", "Move to previous occurrence of char") { findChar(it,  HJMovementDirection.BACKWARD, true, false) },
            HJAction("extend_till_prev_char", "Extend till previous occurrence of char") { findChar(it,  HJMovementDirection.BACKWARD, false, true) },
            HJAction("extend_prev_char", "Extend to previous occurrence of char") { findChar(it,  HJMovementDirection.BACKWARD, true, true) },
            HJAction("repeat_last_motion", "Repeat last motion") { it.hjEditor.repeatLastMotion(1) },
            HJAction("replace", "Replace with new char") { replace(it) },
            HJAction("switch_case", "Switch (toggle) case") { switchCaseImpl(it) { s -> s.map { c -> if (c.isLowerCase()) {c.uppercase()} else if (c.isUpperCase()) {c.lowercase()} else {c} }.joinToString(separator = "") } },
            HJAction("switch_to_uppercase", "Switch to uppercase") { switchCaseImpl(it) { s -> s.uppercase() } },
            HJAction("switch_to_lowercase", "Switch to lowercase") { switchCaseImpl(it) { s -> s.lowercase() } },
            HJAction("page_up", "Move page up") { scrollPageImpl(it, 1.0f, HJMovementDirection.BACKWARD) },
            HJAction("page_down", "Move page down") { scrollPageImpl(it, 1.0f, HJMovementDirection.FORWARD) },
            HJAction("half_page_up", "Move half page up") { scrollPageImpl(it, 0.5f, HJMovementDirection.BACKWARD) },
            HJAction("half_page_down", "Move half page down") { scrollPageImpl(it, 0.5f, HJMovementDirection.FORWARD) },
            HJAction("page_cursor_up", "Move page and cursor up"),
            HJAction("page_cursor_down", "Move page and cursor down"),
            HJAction("page_cursor_half_up", "Move page and cursor half up"),
            HJAction("page_cursor_half_down", "Move page and cursor half down"),
            HJAction("select_all", "Select whole document") { selectAll(it) },
            HJAction("select_regex", "Select all regex matches inside selections") { selectRegex(it) },
            HJAction("split_selection", "Split selections on regex matches") { splitSelection(it) },
            HJAction("split_selection_on_newline", "Split selection on newlines") { splitSelectionOnNewline(it) },
            HJAction("merge_selections", "Merge selections"),
            HJAction("merge_consecutive_selections", "Merge consecutive selections"),
            HJAction("search", "Search for regex pattern"),
            HJAction("rsearch", "Reverse search for regex pattern"),
            HJAction("search_next", "Select next search match"),
            HJAction("search_prev", "Select previous search match"),
            HJAction("extend_search_next", "Add next search match to selection"),
            HJAction("extend_search_prev", "Add previous search match to selection"),
            HJAction("search_selection", "Use current selection as search pattern"),
            HJAction("search_selection_detect_word_boundaries", "Use current selection as the search pattern, automatically wrapping with `\\b` on word boundaries"),
            HJAction("make_search_word_bounded", "Modify current search to make it word bounded"),
            HJAction("global_search", "Global search in workspace folder"),
            HJAction("extend_line", "Select current line, if already selected, extend to another line based on the anchor") { extendLine(it) },
            HJAction("extend_line_below", "Select current line, if already selected, extend to next line") { extendImpl(it, true) },
            HJAction("extend_line_above", "Select current line, if already selected, extend to previous line") { extendImpl(it, false) },
            HJAction("select_line_above", "Select current line, if already selected, extend or shrink line above based on the anchor"),
            HJAction("select_line_below", "Select current line, if already selected, extend or shrink line below based on the anchor"),
            HJAction("extend_to_line_bounds", "Extend selection to line bounds"),
            HJAction("shrink_to_line_bounds", "Shrink selection to line bounds"),
            HJAction("delete_selection", "Delete selection") { deleteSelectionImpl(it, false, true) },
            HJAction("delete_selection_noyank", "Delete selection without yanking") { deleteSelectionImpl(it, false, false) },
            HJAction("change_selection", "Change selection") { deleteSelectionImpl(it, true, true) },
            HJAction("change_selection_noyank", "Change selection without yanking") { deleteSelectionImpl(it, true, false) },
            HJAction("collapse_selection", "Collapse selection into single cursor") { collapseSelection(it) },
            HJAction("flip_selections", "Flip selection cursor and anchor"),
            HJAction("ensure_selections_forward", "Ensure all selections face forward"),
            HJAction("insert_mode", "Insert before selection") { insertMode(it, false) },
            HJAction("append_mode", "Append after selection") { insertMode(it, true) },
            HJAction("command_mode", "Enter command mode") { commandMode(it) },
            HJAction("file_picker", "Open file picker"),
            HJAction("file_picker_in_current_buffer_directory", "Open file picker at current buffer's directory"),
            HJAction("file_picker_in_current_directory", "Open file picker at current working directory"),
            HJAction("file_explorer", "Open file explorer in workspace root"),
            HJAction("file_explorer_in_current_buffer_directory", "Open file explorer at current buffer's directory"),
            HJAction("file_explorer_in_current_directory", "Open file explorer at current working directory"),
            HJAction("code_action", "Perform code action"),
            HJAction("buffer_picker", "Open buffer picker"),
            HJAction("jumplist_picker", "Open jumplist picker"),
            HJAction("symbol_picker", "Open symbol picker"),
            HJAction("syntax_symbol_picker", "Open symbol picker from syntax information"),
            HJAction("lsp_or_syntax_symbol_picker", "Open symbol picker from LSP or syntax information"),
            HJAction("changed_file_picker", "Open changed file picker"),
            HJAction("select_references_to_symbol_under_cursor", "Select symbol references"),
            HJAction("workspace_symbol_picker", "Open workspace symbol picker"),
            HJAction("syntax_workspace_symbol_picker", "Open workspace symbol picker from syntax information"),
            HJAction("lsp_or_syntax_workspace_symbol_picker", "Open workspace symbol picker from LSP or syntax information"),
            HJAction("diagnostics_picker", "Open diagnostic picker"),
            HJAction("workspace_diagnostics_picker", "Open workspace diagnostic picker"),
            HJAction("last_picker", "Open last picker"),
            HJAction("insert_at_line_start", "Insert at start of line"),
            HJAction("insert_at_line_end", "Insert at end of line"),
            HJAction("open_below", "Open new line below selection"),
            HJAction("open_above", "Open new line above selection"),
            HJAction("normal_mode", "Enter normal mode") { switchToMode(it, HJEditorMode.NORMAL) },
            HJAction("select_mode", "Enter selection extend mode") { switchToMode(it, HJEditorMode.SELECT) },
            HJAction("exit_select_mode", "Exit selection mode") { if (HJState.mode == HJEditorMode.SELECT) switchToMode(it, HJEditorMode.NORMAL) },
            HJAction("goto_definition", "Goto definition"),
            HJAction("goto_declaration", "Goto declaration"),
            HJAction("add_newline_above", "Add newline above"),
            HJAction("add_newline_below", "Add newline below"),
            HJAction("goto_type_definition", "Goto type definition"),
            HJAction("goto_implementation", "Goto implementation"),
            HJAction("goto_file_start", "Goto line number `<n>` else file start") { gotoFileStartImpl(it, false) },
            HJAction("goto_file_end", "Goto file end") { gotoFileEndImpl(it, false) },
            HJAction("extend_to_file_start", "Extend to line number `<n>` else file start") { gotoFileStartImpl(it, true) },
            HJAction("extend_to_file_end", "Extend to file end") { gotoFileEndImpl(it, true) },
            HJAction("goto_file", "Goto files/URLs in selections"),
            HJAction("goto_file_hsplit", "Goto files in selections (hsplit)"),
            HJAction("goto_file_vsplit", "Goto files in selections (vsplit)"),
            HJAction("goto_reference", "Goto references"),
            HJAction("goto_window_top", "Goto window top"),
            HJAction("goto_window_center", "Goto window center"),
            HJAction("goto_window_bottom", "Goto window bottom"),
            HJAction("goto_last_accessed_file", "Goto last accessed file"),
            HJAction("goto_last_modified_file", "Goto last modified file"),
            HJAction("goto_last_modification", "Goto last modification"),
            HJAction("goto_line", "Goto line") { gotoLineImpl(it, false) },
            HJAction("goto_last_line", "Goto last line") { gotoLastLineImpl(it, false) },
            HJAction("extend_to_last_line", "Extend to last line") { gotoLastLineImpl(it, true) },
            HJAction("goto_first_diag", "Goto first diagnostic"),
            HJAction("goto_last_diag", "Goto last diagnostic"),
            HJAction("goto_next_diag", "Goto next diagnostic"),
            HJAction("goto_prev_diag", "Goto previous diagnostic"),
            HJAction("goto_next_change", "Goto next change"),
            HJAction("goto_prev_change", "Goto previous change"),
            HJAction("goto_first_change", "Goto first change"),
            HJAction("goto_last_change", "Goto last change"),
            HJAction("goto_line_start", "Goto line start") { gotoLineStartEnd(it, true) },
            HJAction("goto_line_end", "Goto line end") { gotoLineStartEnd(it, false) },
            HJAction("goto_column", "Goto column"),
            HJAction("extend_to_column", "Extend to column"),
            HJAction("goto_next_buffer", "Goto next buffer"),
            HJAction("goto_previous_buffer", "Goto previous buffer"),
            HJAction("goto_line_end_newline", "Goto newline at line end") { gotoLineEndNewline(it) },
            HJAction("goto_first_nonwhitespace", "Goto first non-blank in line") { gotoFirstNonWhitespace(it) },
            HJAction("trim_selections", "Trim whitespace from selections"),
            HJAction("extend_to_line_start", "Extend to line start") { gotoLineStartEndImpl(it, true, true) },
            HJAction("extend_to_first_nonwhitespace", "Extend to first non-blank in line") { gotoFirstNonWhitespaceImpl(it, true) },
            HJAction("extend_to_line_end", "Extend to line end") { gotoLineStartEndImpl(it, false, true) },
            HJAction("extend_to_line_end_newline", "Extend to line end") { gotoLineEndNewlineImpl(it, true) },
            HJAction("signature_help", "Show signature help"),
            HJAction("smart_tab", "Insert tab if all cursors have all whitespace to their left; otherwise"),
            HJAction("insert_tab", "Insert tab char"),
            HJAction("insert_newline", "Insert newline char"),
            HJAction("insert_char_interactive", "Insert an interactively-chosen char"),
            HJAction("append_char_interactive", "Append an interactively-chosen char"),
            HJAction("delete_char_backward", "Delete previous char"),
            HJAction("delete_char_forward", "Delete next char"),
            HJAction("delete_word_backward", "Delete previous word"),
            HJAction("delete_word_forward", "Delete next word"),
            HJAction("kill_to_line_start", "Delete till start of line"),
            HJAction("kill_to_line_end", "Delete till end of line"),
            HJAction("undo", "Undo change") { executeIdeAction(it, IdeActions.ACTION_UNDO) },
            HJAction("redo", "Redo change") { executeIdeAction(it, IdeActions.ACTION_REDO) },
            HJAction("earlier", "Move backward in history"),
            HJAction("later", "Move forward in history"),
            HJAction("commit_undo_checkpoint", "Commit changes to new checkpoint"),
            HJAction("yank", "Yank selection") { yank(it) },
            HJAction("yank_to_clipboard", "Yank selections to clipboard") { yankImpl(it.hjEditor.editor.document, it.hjEditor.selection, HJRegister.Clipboard) },
            HJAction("yank_to_primary_clipboard", "Yank selections to primary clipboard"),
            HJAction("yank_joined", "Join and yank selections"),
            HJAction("yank_joined_to_clipboard", "Join and yank selections to clipboard"),
            HJAction("yank_main_selection_to_clipboard", "Yank main selection to clipboard"),
            HJAction("yank_main_selection_to_primary_clipboard", "Join and yank selections to primary clipboard"),
            HJAction("replace_with_yanked", "Yank main selection to primary clipboard"),
            HJAction("replace_selections_with_clipboard", "Replace with yanked text"),
            HJAction("replace_selections_with_primary_clipboard", "Replace selections by clipboard content"),
            HJAction("paste_after", "Replace selections by primary clipboard") { pasteImpl(it, false) },
            HJAction("paste_before", "Paste after selection") { pasteImpl(it, true) },
            HJAction("paste_clipboard_after", "Paste before selection") { pasteImpl(it.copy(register = HJRegister.Clipboard), false) },
            HJAction("paste_clipboard_before", "Paste clipboard after selections") { pasteImpl(it.copy(register = HJRegister.Clipboard), true) },
            HJAction("paste_primary_clipboard_after", "Paste clipboard before selections"),
            HJAction("paste_primary_clipboard_before", "Paste primary clipboard after selections"),
            HJAction("indent", "Paste primary clipboard before selections"),
            HJAction("unindent", "Indent selection"),
            HJAction("format_selections", "Unindent selection"),
            HJAction("join_selections", "Format selection"),
            HJAction("join_selections_space", "Join lines inside selection"),
            HJAction("keep_selections", "Join lines inside selection and select spaces"),
            HJAction("remove_selections", "Keep selections matching regex"),
            HJAction("align_selections", "Remove selections matching regex"),
            HJAction("keep_primary_selection", "Align selections in column") { keepPrimarySelection(it) },
            HJAction("remove_primary_selection", "Keep primary selection"),
            HJAction("completion", "Remove primary selection"),
            HJAction("hover", "Invoke completion popup") { executeIdeAction(it, "ShowHoverInfo") },
            HJAction("toggle_comments", "Show docs for item under cursor"),
            HJAction("toggle_line_comments", "Comment/uncomment selections"),
            HJAction("toggle_block_comments", "Line comment/uncomment selections"),
            HJAction("rotate_selections_forward", "Block comment/uncomment selections"),
            HJAction("rotate_selections_backward", "Rotate selections forward"),
            HJAction("rotate_selection_contents_forward", "Rotate selections backward"),
            HJAction("rotate_selection_contents_backward", "Rotate selection contents forward"),
            HJAction("reverse_selection_contents", "Reverse selections contents"),
            HJAction("expand_selection", "Expand selection to parent syntax node"),
            HJAction("shrink_selection", "Shrink selection to previously expanded syntax node"),
            HJAction("select_next_sibling", "Select next sibling in the syntax tree"),
            HJAction("select_prev_sibling", "Select previous sibling the in syntax tree"),
            HJAction("select_all_siblings", "Select all siblings of the current node"),
            HJAction("select_all_children", "Select all children of the current node"),
            HJAction("jump_forward", "Jump forward on jumplist"),
            HJAction("jump_backward", "Jump backward on jumplist"),
            HJAction("save_selection", "Save current selection to jumplist"),
            HJAction("jump_view_right", "Jump to right split"),
            HJAction("jump_view_left", "Jump to left split"),
            HJAction("jump_view_up", "Jump to split above"),
            HJAction("jump_view_down", "Jump to split below"),
            HJAction("swap_view_right", "Swap with right split"),
            HJAction("swap_view_left", "Swap with left split"),
            HJAction("swap_view_up", "Swap with split above"),
            HJAction("swap_view_down", "Swap with split below"),
            HJAction("transpose_view", "Transpose splits"),
            HJAction("rotate_view", "Goto next window"),
            HJAction("rotate_view_reverse", "Goto previous window"),
            HJAction("hsplit", "Horizontal bottom split"),
            HJAction("hsplit_new", "Horizontal bottom split scratch buffer"),
            HJAction("vsplit", "Vertical right split"),
            HJAction("vsplit_new", "Vertical right split scratch buffer"),
            HJAction("wclose", "Close window"),
            HJAction("wonly", "Close windows except current"),
            HJAction("select_register", "Select register"),
            HJAction("insert_register", "Insert register"),
            HJAction("copy_between_registers", "Copy between two registers"),
            HJAction("align_view_middle", "Align view middle"),
            HJAction("align_view_top", "Align view top"),
            HJAction("align_view_center", "Align view center"),
            HJAction("align_view_bottom", "Align view bottom"),
            HJAction("scroll_up", "Scroll view up"),
            HJAction("scroll_down", "Scroll view down"),
            HJAction("match_brackets", "Goto matching bracket"),
            HJAction("surround_add", "Surround add"),
            HJAction("surround_replace", "Surround replace"),
            HJAction("surround_delete", "Surround delete"),
            HJAction("select_textobject_around", "Select around object"),
            HJAction("select_textobject_inner", "Select inside object"),
            HJAction("goto_next_function", "Goto next function"),
            HJAction("goto_prev_function", "Goto previous function"),
            HJAction("goto_next_class", "Goto next type definition"),
            HJAction("goto_prev_class", "Goto previous type definition"),
            HJAction("goto_next_parameter", "Goto next parameter"),
            HJAction("goto_prev_parameter", "Goto previous parameter"),
            HJAction("goto_next_comment", "Goto next comment"),
            HJAction("goto_prev_comment", "Goto previous comment"),
            HJAction("goto_next_test", "Goto next test"),
            HJAction("goto_prev_test", "Goto previous test"),
            HJAction("goto_next_xml_element", "Goto next (X)HTML element"),
            HJAction("goto_prev_xml_element", "Goto previous (X)HTML element"),
            HJAction("goto_next_entry", "Goto next pairing"),
            HJAction("goto_prev_entry", "Goto previous pairing"),
            HJAction("goto_next_paragraph", "Goto next paragraph"),
            HJAction("goto_prev_paragraph", "Goto previous paragraph"),
            HJAction("dap_launch", "Launch debug target"),
            HJAction("dap_restart", "Restart debugging session"),
            HJAction("dap_toggle_breakpoint", "Toggle breakpoint"),
            HJAction("dap_continue", "Continue program execution"),
            HJAction("dap_pause", "Pause program execution"),
            HJAction("dap_step_in", "Step in"),
            HJAction("dap_step_out", "Step out"),
            HJAction("dap_next", "Step to next"),
            HJAction("dap_variables", "List variables"),
            HJAction("dap_terminate", "End debug session"),
            HJAction("dap_edit_condition", "Edit breakpoint condition on current line"),
            HJAction("dap_edit_log", "Edit breakpoint log message on current line"),
            HJAction("dap_switch_thread", "Switch current thread"),
            HJAction("dap_switch_stack_frame", "Switch stack frame"),
            HJAction("dap_enable_exceptions", "Enable exception breakpoints"),
            HJAction("dap_disable_exceptions", "Disable exception breakpoints"),
            HJAction("shell_pipe", "Pipe selections through shell command"),
            HJAction("shell_pipe_to", "Pipe selections into shell command ignoring output"),
            HJAction("shell_insert_output", "Insert shell command output before selections"),
            HJAction("shell_append_output", "Append shell command output after selections"),
            HJAction("shell_keep_pipe", "Filter selections with shell predicate"),
            HJAction("suspend", "Suspend and return to shell"),
            HJAction("rename_symbol", "Rename symbol"),
            HJAction("increment", "Increment item under cursor"),
            HJAction("decrement", "Decrement item under cursor"),
            HJAction("record_macro", "Record macro"),
            HJAction("replay_macro", "Replay macro"),
            HJAction("command_palette", "Open command palette") { commandPalette(it) },
            HJAction("goto_word", "Jump to a two-character label"),
            HJAction("extend_to_word", "Extend to a two-character label"),
            HJAction("goto_next_tabstop", "Goto next snippet placeholder"),
            HJAction("goto_prev_tabstop", "Goto next snippet placeholder"),
            HJAction("rotate_selections_first", "Make the first selection your primary one"),
            HJAction("rotate_selections_last", "Make the last selection your primary one"),
            // non-helix action
            HJAction("debug") { it.hjEditor.editor.document.charsSequence.forEach { c: Char -> println(c.code)}}
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

    if (newMode == HJEditorMode.INSERT) {
        collapseSelection(cx)
        HJState.typedHandlerBlock = true
    }

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
        val motion = if (key is HJInputKey.Character || (
                    key is HJInputKey.Other && (key.code == KeyEvent.VK_ENTER || key.code == KeyEvent.VK_TAB))
        ) {
            // contrary to Helix, where newlines can be different characters, here we only have to deal with \n when
            // reading from a Document.
            val ch = when (key) {
                is HJInputKey.Character -> key.char
                is HJInputKey.Other if key.code == KeyEvent.VK_ENTER -> '\n'
                else -> '\t'
            }

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
        } else null

        motion?.let { cx.hjEditor.applyMotion(it) }
    }
}

private fun replace(cx: HJActionContext) {
    HJInputHandler.onNextKey { key: HJInputKey ->
        val char = when (key) {
            is HJInputKey.Character -> key.char
            is HJInputKey.Other if key.code == KeyEvent.VK_ENTER -> '\n'
            is HJInputKey.Other if key.code == KeyEvent.VK_TAB -> '\t'
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
