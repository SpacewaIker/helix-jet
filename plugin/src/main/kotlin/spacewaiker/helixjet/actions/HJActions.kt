@file:Suppress("BooleanLiteralArgument")

package spacewaiker.helixjet.actions

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.BrowserUtil
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.LanguageCommenters
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import spacewaiker.helixjet.actions.HJKeyAction.HJAction
import spacewaiker.helixjet.editor.*
import spacewaiker.helixjet.input.HJInputHandler
import spacewaiker.helixjet.input.HJInputKey
import spacewaiker.helixjet.treesitter.HJLanguageSyntax
import spacewaiker.helixjet.treesitter.HJTreesitterHandling
import spacewaiker.helixjet.ui.*
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
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

        val action = actions[name]
        if (action == null) logger.debug("HJActions: No action found for $name")
        return actions[name]
    }

    private fun createActionsMap(): MutableMap<String, HJAction> {
        val map = mutableMapOf<String, HJAction>()

        for (action in actionsRaw) {
            if (map.containsKey(action.name)) {
                logger.error("Action ${action.name} is already defined in actions")
                continue
            }
            map[action.name] = action
        }

        return map
    }
}

// @formatter:off
private val actionsRaw = arrayOf(
    HJAction("no_op", "Do nothing") { _ -> },
    HJAction("move_char_left", "Move left") { moveImpl(it, HJCharMovementKind.Horizontal, HJDirection.Backward, false) },
    HJAction("move_char_right", "Move right") { moveImpl(it, HJCharMovementKind.Horizontal, HJDirection.Forward, false) },
    HJAction("move_line_up", "Move up") { moveImpl(it, HJCharMovementKind.Vertical, HJDirection.Backward, false) },
    HJAction("move_line_down", "Move down") { moveImpl(it, HJCharMovementKind.Vertical, HJDirection.Forward, false) },
    HJAction("move_visual_line_up", "Move up") { moveImpl(it, HJCharMovementKind.VerticalVisual, HJDirection.Backward, false) },
    HJAction("move_visual_line_down", "Move down") { moveImpl(it, HJCharMovementKind.VerticalVisual, HJDirection.Forward, false) },
    HJAction("extend_char_left", "Extend left") { moveImpl(it, HJCharMovementKind.Horizontal, HJDirection.Backward, true) },
    HJAction("extend_char_right", "Extend right") { moveImpl(it, HJCharMovementKind.Horizontal, HJDirection.Forward, true) },
    HJAction("extend_line_up", "Extend up") { moveImpl(it, HJCharMovementKind.Vertical, HJDirection.Backward, true) },
    HJAction("extend_line_down", "Extend down") { moveImpl(it, HJCharMovementKind.Vertical, HJDirection.Forward, true) },
    HJAction("extend_visual_line_up", "Extend up") { moveImpl(it, HJCharMovementKind.VerticalVisual, HJDirection.Backward, true) },
    HJAction("extend_visual_line_down", "Extend down") { moveImpl(it, HJCharMovementKind.VerticalVisual, HJDirection.Forward, true) },
    HJAction("copy_selection_on_next_line", "Copy selection on next line") { copySelectionOnLine(it, HJDirection.Forward) },
    HJAction("copy_selection_on_prev_line", "Copy selection on previous line") { copySelectionOnLine(it, HJDirection.Backward) },
    HJAction("move_next_word_start", "Move to start of next word") { moveWordImpl(it, HJWordMotionTarget.NextWordStart, false) },
    HJAction("move_prev_word_start", "Move to start of previous word") { moveWordImpl(it, HJWordMotionTarget.PrevWordStart, false) },
    HJAction("move_next_word_end", "Move to end of next word") { moveWordImpl(it, HJWordMotionTarget.NextWordEnd, false) },
    HJAction("move_prev_word_end", "Move to end of previous word") { moveWordImpl(it, HJWordMotionTarget.PrevWordEnd, false) },
    HJAction("move_next_long_word_start", "Move to start of next long word") { moveWordImpl(it, HJWordMotionTarget.NextLongWordStart, false) },
    HJAction("move_prev_long_word_start", "Move to start of previous long word") { moveWordImpl(it, HJWordMotionTarget.PrevLongWordStart, false) },
    HJAction("move_next_long_word_end", "Move to end of next long word") { moveWordImpl(it, HJWordMotionTarget.NextLongWordEnd, false) },
    HJAction("move_prev_long_word_end", "Move to end of previous long word") { moveWordImpl(it, HJWordMotionTarget.PrevLongWordEnd, false) },
    HJAction("move_next_sub_word_start", "Move to start of next sub word") { moveWordImpl(it, HJWordMotionTarget.NextSubWordStart, false) },
    HJAction("move_prev_sub_word_start", "Move to start of previous sub word") { moveWordImpl(it, HJWordMotionTarget.PrevSubWordStart, false) },
    HJAction("move_next_sub_word_end", "Move to end of next sub word") { moveWordImpl(it, HJWordMotionTarget.NextSubWordEnd, false) },
    HJAction("move_prev_sub_word_end", "Move to end of previous sub word") { moveWordImpl(it, HJWordMotionTarget.PrevSubWordEnd, false) },
    HJAction("move_parent_node_end", "Move to end of the parent node") { moveNodeBoundImpl(it, HJDirection.Forward, false) },
    HJAction("move_parent_node_start", "Move to beginning of the parent node") { moveNodeBoundImpl(it, HJDirection.Backward, false) },
    HJAction("extend_next_word_start", "Extend to start of next word") { moveWordImpl(it, HJWordMotionTarget.NextWordStart, true) },
    HJAction("extend_prev_word_start", "Extend to start of previous word") { moveWordImpl(it, HJWordMotionTarget.PrevWordStart, true) },
    HJAction("extend_next_word_end", "Extend to end of next word") { moveWordImpl(it, HJWordMotionTarget.NextWordEnd, true) },
    HJAction("extend_prev_word_end", "Extend to end of previous word") { moveWordImpl(it, HJWordMotionTarget.PrevWordEnd, true) },
    HJAction("extend_next_long_word_start", "Extend to start of next long word") { moveWordImpl(it, HJWordMotionTarget.NextLongWordStart, true) },
    HJAction("extend_prev_long_word_start", "Extend to start of previous long word") { moveWordImpl(it, HJWordMotionTarget.PrevLongWordStart, true) },
    HJAction("extend_next_long_word_end", "Extend to end of next long word") { moveWordImpl(it, HJWordMotionTarget.NextLongWordEnd, true) },
    HJAction("extend_prev_long_word_end", "Extend to end of prev long word") { moveWordImpl(it, HJWordMotionTarget.PrevLongWordEnd, true) },
    HJAction("extend_next_sub_word_start", "Extend to start of next sub word") { moveWordImpl(it, HJWordMotionTarget.NextSubWordStart, true) },
    HJAction("extend_prev_sub_word_start", "Extend to start of previous sub word") { moveWordImpl(it, HJWordMotionTarget.PrevSubWordStart, true) },
    HJAction("extend_next_sub_word_end", "Extend to end of next sub word") { moveWordImpl(it, HJWordMotionTarget.NextSubWordEnd, true) },
    HJAction("extend_prev_sub_word_end", "Extend to end of prev sub word") { moveWordImpl(it, HJWordMotionTarget.PrevSubWordEnd, true) },
    HJAction("extend_parent_node_end", "Extend to end of the parent node") { moveNodeBoundImpl(it, HJDirection.Forward, true) },
    HJAction("extend_parent_node_start", "Extend to beginning of the parent node") { moveNodeBoundImpl(it, HJDirection.Backward, true) },
    HJAction("find_till_char", "Move till next occurrence of char") { findChar(it,  HJDirection.Forward, false, false) },
    HJAction("find_next_char", "Move to next occurrence of char") { findChar(it,  HJDirection.Forward, true, false) },
    HJAction("extend_till_char", "Extend till next occurrence of char") { findChar(it,  HJDirection.Forward, false, true) },
    HJAction("extend_next_char", "Extend to next occurrence of char") { findChar(it,  HJDirection.Forward, true, true) },
    HJAction("till_prev_char", "Move till previous occurrence of char") { findChar(it,  HJDirection.Backward, false, false) },
    HJAction("find_prev_char", "Move to previous occurrence of char") { findChar(it,  HJDirection.Backward, true, false) },
    HJAction("extend_till_prev_char", "Extend till previous occurrence of char") { findChar(it,  HJDirection.Backward, false, true) },
    HJAction("extend_prev_char", "Extend to previous occurrence of char") { findChar(it,  HJDirection.Backward, true, true) },
    HJAction("repeat_last_motion", "Repeat last motion") { repeatLastMotion(it) },
    HJAction("repeat_last_insert", "Repeat last insert") { repeatLastInsert(it) },
    HJAction("replace", "Replace with new char") { replace(it) },
    HJAction("switch_case", "Switch (toggle) case") { switchCaseImpl(it) { s -> s.map { c -> if (c.isLowerCase()) {c.uppercase()} else if (c.isUpperCase()) {c.lowercase()} else {c} }.joinToString(separator = "") } },
    HJAction("switch_to_uppercase", "Switch to uppercase") { switchCaseImpl(it) { s -> s.uppercase() } },
    HJAction("switch_to_lowercase", "Switch to lowercase") { switchCaseImpl(it) { s -> s.lowercase() } },
    HJAction("page_up", "Move page up") { scrollPageImpl(it, 1.0f, HJDirection.Backward, false) },
    HJAction("page_down", "Move page down") { scrollPageImpl(it, 1.0f, HJDirection.Forward, false) },
    HJAction("half_page_up", "Move half page up") { scrollPageImpl(it, 0.5f, HJDirection.Backward, false) },
    HJAction("half_page_down", "Move half page down") { scrollPageImpl(it, 0.5f, HJDirection.Forward, false) },
    HJAction("page_cursor_up", "Move page and cursor up") { scrollPageImpl(it, 1.0f, HJDirection.Backward, true) },
    HJAction("page_cursor_down", "Move page and cursor down") { scrollPageImpl(it, 1.0f, HJDirection.Forward, true) },
    HJAction("page_cursor_half_up", "Move page and cursor half up") { scrollPageImpl(it, 0.5f, HJDirection.Backward, true) },
    HJAction("page_cursor_half_down", "Move page and cursor half down") { scrollPageImpl(it, 0.5f, HJDirection.Forward, true) },
    HJAction("select_all", "Select whole document") { selectAll(it) },
    HJAction("select_regex", "Select all regex matches inside selections") { selectRegex(it) },
    HJAction("split_selection", "Split selections on regex matches") { splitSelection(it) },
    HJAction("split_selection_on_newline", "Split selection on newlines") { splitSelectionOnNewline(it) },
    HJAction("merge_selections", "Merge selections") { mergeSelections(it) },
    HJAction("merge_consecutive_selections", "Merge consecutive selections") { cx -> mergeConsecutiveSelections(cx) },
    HJAction("search", "Search for regex pattern") { searcher(it, HJDirection.Forward) },
    HJAction("rsearch", "Reverse search for regex pattern") { searcher(it, HJDirection.Backward) },
    HJAction("search_next", "Select next search match") { searchNextOrPrevImpl(it, false, HJDirection.Forward) },
    HJAction("search_prev", "Select previous search match") { searchNextOrPrevImpl(it, false, HJDirection.Backward) },
    HJAction("extend_search_next", "Add next search match to selection") { searchNextOrPrevImpl(it, true, HJDirection.Forward) },
    HJAction("extend_search_prev", "Add previous search match to selection") { searchNextOrPrevImpl(it, true, HJDirection.Backward) },
    HJAction("search_selection", "Use current selection as search pattern") { searchSelectionImpl(it, false) },
    HJAction("search_selection_detect_word_boundaries", "Use current selection as the search pattern, automatically wrapping with `\\b` on word boundaries") { searchSelectionImpl(it, true) },
    HJAction("make_search_word_bounded", "Modify current search to make it word bounded") { makeSearchWordBounded(it) },
    HJAction("global_search", "Global search in workspace folder") {executeIdeAction(it, "TextSearchAction") },
    HJAction("extend_line", "Select current line, if already selected, extend to another line based on the anchor") { extendLine(it) },
    HJAction("extend_line_below", "Select current line, if already selected, extend to next line") { extendImpl(it, true) },
    HJAction("extend_line_above", "Select current line, if already selected, extend to previous line") { extendImpl(it, false) },
    HJAction("select_line_above", "Select current line, if already selected, extend or shrink line above based on the anchor") { selectLineImpl(it, true) },
    HJAction("select_line_below", "Select current line, if already selected, extend or shrink line below based on the anchor") { selectLineImpl(it, false) },
    HJAction("extend_to_line_bounds", "Extend selection to line bounds") { extendToLineBounds(it) },
    HJAction("shrink_to_line_bounds", "Shrink selection to line bounds") { shrinkToLineBounds(it) },
    HJAction("delete_selection", "Delete selection") { deleteSelectionImpl(it, false, true) },
    HJAction("delete_selection_noyank", "Delete selection without yanking") { deleteSelectionImpl(it, false, false) },
    HJAction("change_selection", "Change selection") { deleteSelectionImpl(it, true, true) },
    HJAction("change_selection_noyank", "Change selection without yanking") { deleteSelectionImpl(it, true, false) },
    HJAction("collapse_selection", "Collapse selection into single cursor") { collapseSelection(it) },
    HJAction("flip_selections", "Flip selection cursor and anchor") { flipSelections(it) },
    HJAction("ensure_selections_forward", "Ensure all selections face forward") { ensureSelectionsForward(it) },
    HJAction("insert_mode", "Insert before selection") { insertMode(it, false) },
    HJAction("append_mode", "Append after selection") { insertMode(it, true) },
    HJAction("command_mode", "Enter command mode") { commandMode(it) },
    HJAction("file_picker", "Open file picker") { filePickerProjectRoot(it) },
    HJAction("file_picker_in_current_buffer_directory", "Open file picker at current buffer's directory") { filePickerImpl(it, "File Picker (Current Buffer Directory)", it.hjEditor.editor.virtualFile.parent) },
    HJAction("file_picker_in_current_directory", "Open file picker at current working directory"),
    HJAction("file_explorer", "Open file explorer in workspace root"),
    HJAction("file_explorer_in_current_buffer_directory", "Open file explorer at current buffer's directory"),
    HJAction("file_explorer_in_current_directory", "Open file explorer at current working directory"),
    HJAction("code_action", "Perform code action") { executeIdeAction(it, IdeActions.ACTION_SHOW_INTENTION_ACTIONS) },
    HJAction("buffer_picker", "Open buffer picker") { bufferPicker(it) },
    HJAction("jumplist_picker", "Open jumplist picker") { jumplistPicker(it) },
    HJAction("symbol_picker", "Open symbol picker") { symbolPicker(it) },
    HJAction("syntax_symbol_picker", "Open symbol picker from syntax information") { HJTreesitterHandling.syntaxSymbolPicker(it) },
    HJAction("lsp_or_syntax_symbol_picker", "Open symbol picker from LSP or syntax information"), // TODO: is LSP better than TS?
    HJAction("changed_file_picker", "Open changed file picker") { changedFilePicker(it) },
    HJAction("select_references_to_symbol_under_cursor", "Select symbol references") { executeIdeAction(it, "HighlightUsagesInFile") },
    HJAction("workspace_symbol_picker", "Open workspace symbol picker") { executeIdeAction(it, "GotoSymbol") },
    HJAction("syntax_workspace_symbol_picker", "Open workspace symbol picker from syntax information") { HJTreesitterHandling.syntaxWorkspaceSymbolPicker(it) },
    HJAction("lsp_or_syntax_workspace_symbol_picker", "Open workspace symbol picker from LSP or syntax information"),
    HJAction("diagnostics_picker", "Open diagnostic picker") { diagnosticPicker(it) },
    HJAction("workspace_diagnostics_picker", "Open workspace diagnostic picker") { diagnosticPickerWorkspace(it) },
    HJAction("last_picker", "Open last picker") { lastPicker(it) },
    HJAction("insert_at_line_start", "Insert at start of line") { insertWithIndent(it, true) },
    HJAction("insert_at_line_end", "Insert at end of line") { insertWithIndent(it, false) },
    HJAction("open_below", "Open new line below selection") { open(it, true, true) },
    HJAction("open_above", "Open new line above selection") { open(it, false, true) },
    HJAction("normal_mode", "Enter normal mode") { switchToMode(it, HJEditorMode.Normal) },
    HJAction("select_mode", "Enter selection extend mode") { switchToMode(it, HJEditorMode.Select) },
    HJAction("exit_select_mode", "Exit selection mode") { if (HJState.mode == HJEditorMode.Select) switchToMode(it, HJEditorMode.Normal) },
    HJAction("goto_definition", "Goto definition") { executeIdeJumpAction(it, IdeActions.ACTION_GOTO_IMPLEMENTATION) },
    HJAction("goto_declaration", "Goto declaration") { executeIdeJumpAction(it, IdeActions.ACTION_GOTO_DECLARATION) },
    HJAction("add_newline_above", "Add newline above") { addNewlineImpl(it, true) },
    HJAction("add_newline_below", "Add newline below") { addNewlineImpl(it, false) },
    HJAction("goto_type_definition", "Goto type definition") { executeIdeJumpAction(it, IdeActions.ACTION_GOTO_TYPE_DECLARATION) },
    HJAction("goto_implementation", "Goto implementation") { executeIdeJumpAction(it, IdeActions.ACTION_GOTO_IMPLEMENTATION) },
    HJAction("goto_file_start", "Goto line number `<n>` else file start") { gotoFileStartImpl(it, false) },
    HJAction("goto_file_end", "Goto file end") { gotoFileEndImpl(it, false) },
    HJAction("extend_to_file_start", "Extend to line number `<n>` else file start") { gotoFileStartImpl(it, true) },
    HJAction("extend_to_file_end", "Extend to file end") { gotoFileEndImpl(it, true) },
    HJAction("goto_file", "Goto files/URLs in selections") { gotoFileImpl(it, HJFileOpenLocation.InPlace) },
    HJAction("goto_file_hsplit", "Goto files in selections (hsplit)") { gotoFileImpl(it, HJFileOpenLocation.HSplit) },
    HJAction("goto_file_vsplit", "Goto files in selections (vsplit)") { gotoFileImpl(it, HJFileOpenLocation.VSplit) },
    HJAction("goto_file_background", "Goto files in selections (background)") { gotoFileImpl(it, HJFileOpenLocation.BackgroundTab) },
    HJAction("goto_reference", "Goto references") { executeIdeJumpAction(it, IdeActions.ACTION_FIND_USAGES) },
    HJAction("goto_window_top", "Goto window top") { gotoWindowImpl(it, HJAlignment.Top) },
    HJAction("goto_window_center", "Goto window center") { gotoWindowImpl(it, HJAlignment.Center) },
    HJAction("goto_window_bottom", "Goto window bottom") { gotoWindowImpl(it, HJAlignment.Bottom) },
    HJAction("goto_last_accessed_file", "Goto last accessed file") { gotoLastAccessedFile(it) },
    HJAction("goto_last_modified_file", "Goto last modified file") { gotoLastModifiedFile(it, false) },
    HJAction("goto_last_modification", "Goto last modification") { gotoLastModifiedFile(it, true) },
    HJAction("goto_line", "Goto line") { gotoLineImpl(it, false) },
    HJAction("goto_last_line", "Goto last line") { gotoLastLineImpl(it, false) },
    HJAction("extend_to_last_line", "Extend to last line") { gotoLastLineImpl(it, true) },
    HJAction("goto_first_diag", "Goto first diagnostic") { gotoDiagnosticImpl(it, HJGotoTarget.First) },
    HJAction("goto_last_diag", "Goto last diagnostic") { gotoDiagnosticImpl(it, HJGotoTarget.Last) },
    HJAction("goto_next_diag", "Goto next diagnostic") { gotoDiagnosticImpl(it, HJGotoTarget.Next) },
    HJAction("goto_prev_diag", "Goto previous diagnostic") { gotoDiagnosticImpl(it, HJGotoTarget.Prev) },
    HJAction("goto_next_change", "Goto next change") { gotoChangeImpl(it, HJGotoTarget.Next) },
    HJAction("goto_prev_change", "Goto previous change") { gotoChangeImpl(it, HJGotoTarget.Prev) },
    HJAction("goto_first_change", "Goto first change") { gotoChangeImpl(it, HJGotoTarget.First) },
    HJAction("goto_last_change", "Goto last change") { gotoChangeImpl(it, HJGotoTarget.Last) },
    HJAction("goto_line_start", "Goto line start") { gotoLineStartEnd(it, true) },
    HJAction("goto_line_end", "Goto line end") { gotoLineStartEnd(it, false) },
    HJAction("goto_column", "Goto column") { gotoColumnImpl(it, false) },
    HJAction("extend_to_column", "Extend to column") { gotoColumnImpl(it, true) },
    HJAction("goto_next_buffer", "Goto next buffer") { executeIdeJumpAction(it, IdeActions.ACTION_NEXT_TAB) },
    HJAction("goto_previous_buffer", "Goto previous buffer") { executeIdeJumpAction(it, IdeActions.ACTION_PREVIOUS_TAB) },
    HJAction("goto_line_end_newline", "Goto newline at line end") { gotoLineEndNewline(it) },
    HJAction("goto_first_nonwhitespace", "Goto first non-blank in line") { gotoFirstNonWhitespace(it) },
    HJAction("trim_selections", "Trim whitespace from selections") { trimSelections(it) },
    HJAction("extend_to_line_start", "Extend to line start") { gotoLineStartEndImpl(it, true, true) },
    HJAction("extend_to_first_nonwhitespace", "Extend to first non-blank in line") { gotoFirstNonWhitespaceImpl(it, true) },
    HJAction("extend_to_line_end", "Extend to line end") { gotoLineStartEndImpl(it, false, true) },
    HJAction("extend_to_line_end_newline", "Extend to line end") { gotoLineEndNewlineImpl(it, true) },
    HJAction("signature_help", "Show signature help"),
    HJAction("smart_tab", "Insert tab if all cursors have all whitespace to their left; otherwise") { executeIdeAction(it, IdeActions.ACTION_EDITOR_TAB); it.hjEditor.setNewSelection(HJSelection(it.hjEditor.editor.caretModel)) },
    HJAction("insert_tab", "Insert tab char"),
    HJAction("insert_newline", "Insert newline char") { executeIdeAction(it, IdeActions.ACTION_EDITOR_ENTER); it.hjEditor.setNewSelection(HJSelection(it.hjEditor.editor.caretModel)) },
    HJAction("insert_char_interactive", "Insert an interactively-chosen char"),
    HJAction("append_char_interactive", "Append an interactively-chosen char"),
    HJAction("delete_char_backward", "Delete previous char") { executeIdeAction(it, IdeActions.ACTION_EDITOR_BACKSPACE); it.hjEditor.setNewSelection(HJSelection(it.hjEditor.editor.caretModel)) },
    HJAction("delete_char_forward", "Delete next char") { executeIdeAction(it, IdeActions.ACTION_EDITOR_DELETE); it.hjEditor.setNewSelection(HJSelection(it.hjEditor.editor.caretModel)) },
    HJAction("delete_word_backward", "Delete previous word") { executeIdeAction(it, IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START); it.hjEditor.setNewSelection(HJSelection(it.hjEditor.editor.caretModel)) },
    HJAction("delete_word_forward", "Delete next word") { executeIdeAction(it, IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END); it.hjEditor.setNewSelection(HJSelection(it.hjEditor.editor.caretModel)) },
    HJAction("kill_to_line_start", "Delete till start of line") { killToLineStart(it) },
    HJAction("kill_to_line_end", "Delete till end of line") { killToLineEnd(it) },
    HJAction("undo", "Undo change") { executeIdeAction(it, IdeActions.ACTION_UNDO); it.hjEditor.setNewSelection(HJSelection(it.hjEditor.editor.caretModel)) },
    HJAction("redo", "Redo change") { executeIdeAction(it, IdeActions.ACTION_REDO); it.hjEditor.setNewSelection(HJSelection(it.hjEditor.editor.caretModel)) },
    HJAction("earlier", "Move backward in history"),
    HJAction("later", "Move forward in history"),
    HJAction("commit_undo_checkpoint", "Commit changes to new checkpoint"),
    HJAction("yank", "Yank selection") { yank(it) },
    HJAction("yank_to_clipboard", "Yank selections to clipboard") { yankImpl(it, it.hjEditor.selection, HJRegister.Clipboard) },
    HJAction("yank_to_primary_clipboard", "Yank selections to primary clipboard"),
    HJAction("yank_joined", "Join and yank selections") { yankJoinedImpl(it, "\n", it.register ?: HJRegister.LastYank) },
    HJAction("yank_joined_to_clipboard", "Join and yank selections to clipboard") { yankJoinedImpl(it, "\n", HJRegister.Clipboard) },
    HJAction("yank_main_selection", "Yank main selection") { yankMainSelectionImpl(it, it.register ?: HJRegister.LastYank) },
    HJAction("yank_main_selection_to_clipboard", "Yank main selection to clipboard") { yankMainSelectionImpl(it, HJRegister.Clipboard) },
    HJAction("yank_joined_to_primary_clipboard", "Join and yank selections to primary clipboard"),
    HJAction("yank_main_selection_to_primary_clipboard", "Yank main selection to primary clipboard"),
    HJAction("replace_with_yanked", "Replace with yanked text") { replaceWithYanked(it) },
    HJAction("replace_selections_with_clipboard", "Replace selections by clipboard content") { replaceWithClipboard(it) },
    HJAction("replace_selections_with_primary_clipboard", "Replace selections by primary clipboard"),
    HJAction("paste_after", "Paste after selection") { pasteImpl(it, false) },
    HJAction("paste_before", "Paste before selection") { pasteImpl(it, true) },
    HJAction("paste_clipboard_after", "Paste clipboard after selections") { pasteImpl(it.copy(register = HJRegister.Clipboard), false) },
    HJAction("paste_clipboard_before", "Paste clipboard before selections") { pasteImpl(it.copy(register = HJRegister.Clipboard), true) },
    HJAction("paste_primary_clipboard_after", "Paste primary clipboard after selections"),
    HJAction("paste_primary_clipboard_before", "Paste primary clipboard before selections"),
    HJAction("indent", "Indent selection") { executeIdeAction(it, IdeActions.ACTION_EDITOR_INDENT_SELECTION, true) }, // TODO: indent manually? or update cursor location manually after
    HJAction("unindent", "Unindent selection") { executeIdeAction(it, IdeActions.ACTION_EDITOR_UNINDENT_SELECTION, true) },
    HJAction("format_selections", "Format selection") { executeIdeAction(it, IdeActions.ACTION_EDITOR_REFORMAT)}, // TODO: only format selected?
    HJAction("join_selections", "Join lines inside selection") { joinSelectionsImpl(it, false) },
    HJAction("join_selections_space", "Join lines inside selection and select spaces") { joinSelectionsImpl(it, true) },
    HJAction("keep_selections", "Keep selections matching regex") { keepOrRemoveSelectionsImpl(it, false) },
    HJAction("remove_selections", "Remove selections matching regex") { keepOrRemoveSelectionsImpl(it, true) },
    HJAction("align_selections", "Align selections in column") { alignSelections(it) },
    HJAction("keep_primary_selection", "Keep primary selection") { keepPrimarySelection(it) },
    HJAction("remove_primary_selection", "Remove primary selection") { removePrimarySelection(it) },
    HJAction("completion", "Invoke completion popup") { executeIdeAction(it, IdeActions.ACTION_CODE_COMPLETION) },
    HJAction("hover", "Show docs for item under cursor") { executeIdeAction(it, "ShowHoverInfo") },
    HJAction("toggle_comments", "Comment/uncomment selections"), // TODO: see helix's commenting behaviour
    HJAction("toggle_line_comments", "Line comment/uncomment selections") { executeIdeAction(it, "CommentByLineComment") },
    HJAction("toggle_block_comments", "Block comment/uncomment selections") { executeIdeAction(it, "CommentByBlockComment") },
    HJAction("rotate_selections_forward", "Rotate selections forward") { rotateSelections(it, HJDirection.Forward) },
    HJAction("rotate_selections_backward", "Rotate selections backward") { rotateSelections(it, HJDirection.Backward) },
    HJAction("rotate_selection_contents_forward", "Rotate selection contents forward") { reorderSelectionContents(it, HJReorderStrategy.RotateForward) },
    HJAction("rotate_selection_contents_backward", "Rotate selection contents backward") { reorderSelectionContents(it, HJReorderStrategy.RotateBackward) },
    HJAction("reverse_selection_contents", "Reverse selections contents") { reorderSelectionContents(it, HJReorderStrategy.Reverse) },
    HJAction("expand_selection", "Expand selection to parent syntax node") { expandSelection(it) },
    HJAction("shrink_selection", "Shrink selection to previously expanded syntax node") { shrinkSelection(it) },
    HJAction("select_next_sibling", "Select next sibling in the syntax tree") { selectSiblingImpl(it, HJTreesitterHandling::selectNextSibling) },
    HJAction("select_prev_sibling", "Select previous sibling the in syntax tree") { selectSiblingImpl(it, HJTreesitterHandling::selectPrevSibling) },
    HJAction("select_all_siblings", "Select all siblings of the current node") { selectAllImpl(it, HJTreesitterHandling::selectAllSiblings) },
    HJAction("select_all_children", "Select all children of the current node") { selectAllImpl(it, HJTreesitterHandling::selectAllChildren) },
    HJAction("jump_forward", "Jump forward on jumplist") { jumpForward(it) },
    HJAction("jump_backward", "Jump backward on jumplist") { jumpBackward(it) },
    HJAction("save_selection", "Save current selection to jumplist") { saveSelection(it) },
    HJAction("jump_view_right", "Jump to right split") { jumpViewImpl(it, HJNavDirection.Right) },
    HJAction("jump_view_left", "Jump to left split") { jumpViewImpl(it, HJNavDirection.Left) },
    HJAction("jump_view_up", "Jump to split above") { jumpViewImpl(it, HJNavDirection.Up) },
    HJAction("jump_view_down", "Jump to split below") { jumpViewImpl(it, HJNavDirection.Down) },
    HJAction("swap_view_right", "Swap with right split") { swapViewImpl(it, HJNavDirection.Right) },
    HJAction("swap_view_left", "Swap with left split") { swapViewImpl(it, HJNavDirection.Left) },
    HJAction("swap_view_up", "Swap with split above") { swapViewImpl(it, HJNavDirection.Up) },
    HJAction("swap_view_down", "Swap with split below") { swapViewImpl(it, HJNavDirection.Down) },
    HJAction("transpose_view", "Transpose splits") { transposeSplit(it) },
    HJAction("rotate_view", "Goto next window") { rotateViewImpl(it, HJDirection.Forward) },
    HJAction("rotate_view_reverse", "Goto previous window") { rotateViewImpl(it, HJDirection.Backward) },
    HJAction("hsplit", "Horizontal bottom split") { splitBufferImpl(it, HJFileOpenLocation.HSplit, false) },
    HJAction("hsplit_new", "Horizontal bottom split scratch buffer") { splitBufferImpl(it, HJFileOpenLocation.HSplit, true) },
    HJAction("vsplit", "Vertical right split") { splitBufferImpl(it, HJFileOpenLocation.VSplit, false) },
    HJAction("vsplit_new", "Vertical right split scratch buffer") { splitBufferImpl(it, HJFileOpenLocation.VSplit, true) },
    HJAction("wclose", "Close window") { closeWindow(it) },
    HJAction("wonly", "Close windows except current") { closeOtherWindows(it) },
    HJAction("select_register", "Select register") { selectRegister(it) },
    HJAction("insert_register", "Insert register") { insertRegister(it) },
    HJAction("copy_between_registers", "Copy between two registers") { copyBetweenRegisters(it) },
    HJAction("align_view_middle", "Align view middle") { alignViewMiddle(it) },
    HJAction("align_view_top", "Align view top") { alignViewImpl(it, HJAlignment.Top) },
    HJAction("align_view_center", "Align view center") { alignViewImpl(it, HJAlignment.Center) },
    HJAction("align_view_bottom", "Align view bottom") { alignViewImpl(it, HJAlignment.Bottom) },
    HJAction("scroll_up", "Scroll view up") { scrollPageByLineImpl(it, HJDirection.Backward) },
    HJAction("scroll_down", "Scroll view down") { scrollPageByLineImpl(it, HJDirection.Forward) },
    HJAction("match_brackets", "Goto matching bracket") { matchBrackets(it) },
    HJAction("surround_add", "Surround add") { surroundAdd(it) },
    HJAction("surround_replace", "Surround replace") { surroundReplace(it) },
    HJAction("surround_delete", "Surround delete") { surroundDelete(it) },
    HJAction("select_textobject_around", "Select around object") { selectTextObject(it, HJTextObject.TextObject.Around) },
    HJAction("select_textobject_inner", "Select inside object") { selectTextObject(it, HJTextObject.TextObject.Inside) },
    HJAction("goto_next_function", "Goto next function") { gotoTsObjectImpl(it, "function", HJDirection.Forward) },
    HJAction("goto_prev_function", "Goto previous function") { gotoTsObjectImpl(it, "function", HJDirection.Backward) },
    HJAction("goto_next_class", "Goto next type definition") { gotoTsObjectImpl(it, "class", HJDirection.Forward) },
    HJAction("goto_prev_class", "Goto previous type definition") { gotoTsObjectImpl(it, "class", HJDirection.Backward) },
    HJAction("goto_next_parameter", "Goto next parameter") { gotoTsObjectImpl(it, "parameter", HJDirection.Forward) },
    HJAction("goto_prev_parameter", "Goto previous parameter") { gotoTsObjectImpl(it, "parameter", HJDirection.Backward) },
    HJAction("goto_next_comment", "Goto next comment") { gotoTsObjectImpl(it, "comment", HJDirection.Forward) },
    HJAction("goto_prev_comment", "Goto previous comment") { gotoTsObjectImpl(it, "comment", HJDirection.Backward) },
    HJAction("goto_next_test", "Goto next test") { gotoTsObjectImpl(it, "test", HJDirection.Forward) },
    HJAction("goto_prev_test", "Goto previous test") { gotoTsObjectImpl(it, "test", HJDirection.Backward) },
    HJAction("goto_next_xml_element", "Goto next (X)HTML element") { gotoTsObjectImpl(it, "xml-element", HJDirection.Forward) },
    HJAction("goto_prev_xml_element", "Goto previous (X)HTML element") { gotoTsObjectImpl(it, "xml-element", HJDirection.Backward) },
    HJAction("goto_next_entry", "Goto next pairing") { gotoTsObjectImpl(it, "entry", HJDirection.Forward) },
    HJAction("goto_prev_entry", "Goto previous pairing") { gotoTsObjectImpl(it, "entry", HJDirection.Backward) },
    HJAction("goto_next_paragraph", "Goto next paragraph") { gotoParagraphImpl(it, HJDirection.Forward) },
    HJAction("goto_prev_paragraph", "Goto previous paragraph") { gotoParagraphImpl(it, HJDirection.Backward) },
    HJAction("dap_launch", "Launch debug target") { executeIdeAction(it, "Debug") },
    HJAction("dap_restart", "Restart debugging session") { executeIdeAction(it, "Debug") },
    HJAction("dap_toggle_breakpoint", "Toggle breakpoint") { executeIdeAction(it, "ToggleLineBreakpoint") },
    HJAction("dap_continue", "Continue program execution") { executeIdeAction(it, "Resume") },
    HJAction("dap_pause", "Pause program execution") { executeIdeAction(it, "Pause") },
    HJAction("dap_step_in", "Step in") { executeIdeAction(it, "StepInto") },
    HJAction("dap_step_out", "Step out") { executeIdeAction(it, "StepOut") },
    HJAction("dap_next", "Step to next"),
    HJAction("dap_variables", "List variables"),
    HJAction("dap_terminate", "End debug session") { executeIdeAction(it, "Stop") },
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
    HJAction("rename_symbol", "Rename symbol") { executeIdeAction(it, IdeActions.ACTION_RENAME) },
    HJAction("increment", "Increment item under cursor") { incrementImpl(it, HJDirection.Forward) },
    HJAction("decrement", "Decrement item under cursor") { incrementImpl(it, HJDirection.Backward) },
    HJAction("record_macro", "Record macro") { recordMacro(it) },
    HJAction("replay_macro", "Replay macro") { replayMacro(it) },
    HJAction("command_palette", "Open command palette") { commandPalette(it) },
    HJAction("goto_word", "Jump to a two-character label") { jumpToWord(it, false) },
    HJAction("extend_to_word", "Extend to a two-character label") { jumpToWord(it, true) },
    HJAction("goto_next_tabstop", "Goto next snippet placeholder"),
    HJAction("goto_prev_tabstop", "Goto next snippet placeholder"),
    HJAction("rotate_selections_first", "Make the first selection your primary one") { rotateSelectionsFirstLast(it, HJDirection.Backward) },
    HJAction("rotate_selections_last", "Make the last selection your primary one") { rotateSelectionsFirstLast(it, HJDirection.Forward) },
    HJAction("toggle_fold", "Toggle fold under cursor") { executeIdeAction(it, "ExpandCollapseToggleAction") },
    HJAction("collapse_fold", "Collapse fold under cursor") { executeIdeAction(it, "CollapseRegion") },
    HJAction("collapse_all_folds", "Collapse all folds") { executeIdeAction(it, "CollapseAllRegions") },
    HJAction("expand_fold", "Expand fold under cursor") { executeIdeAction(it, "ExpandRegion") },
    HJAction("expand_all_folds", "Expand all folds") { executeIdeAction(it, "ExpandAllRegions") },
)
// @formatter:on

private fun moveImpl(
    cx: HJActionContext,
    charMovement: HJCharMovementKind,
    direction: HJDirection,
    extend: Boolean,
) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        when (charMovement) {
            HJCharMovementKind.Horizontal -> moveHorizontally(
                cx.hjEditor.editor.document,
                range,
                direction,
                cx.count,
                extend
            )

            HJCharMovementKind.Vertical -> moveVertically(cx.hjEditor.editor, range, direction, cx.count, extend)
            HJCharMovementKind.VerticalVisual -> moveVerticallyVisual(
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

    if (newMode == HJEditorMode.Insert) {
        collapseSelection(cx)
        HJState.typedHandlerBlock = true
    }

    HJState.forAllEditors { it.reloadCaretShapes() }

    cx.hjEditor.editor.project?.let {
        WindowManager.getInstance().getStatusBar(it)?.updateWidget(HJStatusBarModeWidget.ID)
    }
}

fun exitSelectMode(cx: HJActionContext) {
    if (HJState.mode == HJEditorMode.Select) {
        switchToMode(cx, HJEditorMode.Normal)
    }
}

private fun insertMode(cx: HJActionContext, append: Boolean) {
    HJState.lastInsertKeystrokes.clear()
    val selection = cx.hjEditor.selection.clone().transform {
        val cursor = if (append) it.to else it.from
        HJRange.point(cursor)
    }
    cx.hjEditor.setNewSelection(selection)
    switchToMode(cx, HJEditorMode.Insert)
}

private fun commandMode(cx: HJActionContext) {
    val popup = HJCommandPopup(cx.hjEditor.editor)
    val autocompleteUi = HJPromptAutocomplete(cx.hjEditor.editor)

    val onEvent = { e: HJPromptEvent, s: String? ->
        if (e == HJPromptEvent.Update && s != null) {
            val words = s.split(Regex("\\s+"))
            val commandName = words.first()
            val command = HJCommands.getCommand(commandName)
            popup.setNewCommand(command)
        } else if (e == HJPromptEvent.Validate && s != null) {
            val words = s.split(Regex("\\s+"))
            val commandName = words.first()

            // parse args and flags
            val args = mutableListOf<String>()
            val flags = mutableListOf<String>()
            for (word in words.drop(1)) {
                if (word.startsWith("--")) {
                    flags.add(word.removePrefix("--"))
                } else if (word.startsWith('-')) {
                    for (c in word.removePrefix("-")) {
                        flags.add(c.toString())
                    }
                } else {
                    args.add(word)
                }
            }

            HJCommands.getCommand(commandName)?.execute(cx, args, flags)
        }

        if (e == HJPromptEvent.Validate || e == HJPromptEvent.Abort) {
            popup.hide()
            autocompleteUi.hide()
        }
    }

    val autocompleteProvider = HJCommands.allCommands.map { it.name }
    val commandBar = HJPrompt(
        cx.hjEditor, onEvent, historyRegister = HJRegister.LastCmd,
        autocompleteProvider = autocompleteProvider, autocompleteUi = autocompleteUi, commandPopup = popup,
    )
    commandBar.show()
    popup.show()
    autocompleteUi.show()
}

private fun collapseSelection(cx: HJActionContext) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val pos = range.cursor
        HJRange.point(pos)
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun deleteSelectionImpl(cx: HJActionContext, change: Boolean, yank: Boolean) {
    val onlyWholeLines = cx.hjEditor.selectionIsLinewise

    if (yank) {
        yank(cx)
    }

    val transaction = cx.hjEditor.changeBySelection { range -> HJChange.Delete(range.from, range.to) }
    cx.hjEditor.apply(transaction)

    if (change) {
        if (onlyWholeLines) {
            open(cx, false, false)
        } else {
            insertMode(cx, false)
        }
    } else {
        switchToMode(cx, HJEditorMode.Normal)
    }
}

private fun killToLineStart(cx: HJActionContext) {
    val transaction = cx.hjEditor.changeBySelection { range ->
        val line = range.cursorLine(cx.doc)
        val firstChar = cx.doc.getLineStartOffset(line)
        val anchor = range.cursor
        val head = if (anchor == firstChar && line != 0) {
            cx.doc.getLineEndOffset(line - 1)
        } else {
            val pos = cx.doc.getLine(line)?.let { firstNonWhitespaceChar(it) }
            if (pos != null) {
                if (firstChar + pos < anchor) {
                    firstChar + pos
                } else firstChar
            } else firstChar
        }
        HJChange.Delete(head, anchor)
    }

    cx.hjEditor.apply(transaction)
}

private fun killToLineEnd(cx: HJActionContext) {
    val transaction = cx.hjEditor.changeBySelection { range ->
        val line = range.cursorLine(cx.doc)
        val lineEndPos = cx.doc.getLineEndOffset(line)
        val pos = range.cursor

        // if the cursor is on the newline char delete that
        if (pos == lineEndPos) {
            HJChange.Delete(pos, cx.doc.getLineStartOffset(line + 1))
        } else {
            HJChange.Delete(pos, lineEndPos)
        }
    }

    cx.hjEditor.apply(transaction)
}

private fun yank(cx: HJActionContext) {
    yankImpl(cx, cx.hjEditor.selection, cx.register ?: HJRegister.LastYank)
}

private fun yankImpl(cx: HJActionContext, selection: HJSelection, register: HJRegister) {
    val values = selection.slices(cx.doc).filterNotNull()
    val selections = values.size

    val plural = if (selections == 1) "" else "s"
    if (HJState.registers.write(register, values, cx.hjEditor)) {
        cx.hjEditor.setStatus("Yanked $selections selection$plural to register $register")
    }
}

fun yankJoinedImpl(cx: HJActionContext, separator: String, register: HJRegister) {
    val selection = cx.hjEditor.selection
    val selections = selection.ranges.size
    val joined = selection.slices(cx.doc)
        .fold(StringBuilder()) { acc, slice ->
            if (acc.isNotEmpty()) {
                acc.append(separator)
            }
            acc.append(slice)
            acc
        }
        .toString()

    val plural = if (selections == 1) "" else "s"
    if (HJState.registers.write(register, listOf(joined), cx.hjEditor)) {
        cx.hjEditor.setStatus("Joined and yanked $selections selection$plural to register $register")
    }
}

fun yankMainSelectionImpl(cx: HJActionContext, register: HJRegister) {
    val selection = cx.hjEditor.selection.primary.slice(cx.doc) ?: return
    if (HJState.registers.write(register, listOf(selection), cx.hjEditor)) {
        cx.hjEditor.setStatus("Yanked primary selection to register $register")
    }
}

private fun pasteImpl(cx: HJActionContext, pasteBefore: Boolean, registerOverride: HJRegister? = null) {
    val register = registerOverride ?: cx.register ?: HJRegister.LastYank
    val registerContents = HJState.registers.read(register, cx.hjEditor) ?: return

    val transaction = cx.hjEditor.changeBySelectionIndexed { idx, range ->
        val pos = if (pasteBefore) range.from else range.to
        val string = registerContents[idx.coerceAtMost(registerContents.size - 1)]
        HJChange.Insert(pos, string)
    }
    cx.hjEditor.apply(transaction)
    exitSelectMode(cx)
}

private fun replaceWithYanked(cx: HJActionContext) {
    replaceSelectionsWithRegister(cx.hjEditor, HJRegister.LastYank, cx.count)
    exitSelectMode(cx)
}

private fun replaceWithClipboard(cx: HJActionContext) {
    replaceSelectionsWithRegister(cx.hjEditor, HJRegister.Clipboard, cx.count)
    exitSelectMode(cx)
}

private fun replaceSelectionsWithRegister(editor: HJEditor, register: HJRegister, count: Int) {
    val values = HJState.registers.read(register, editor)?.filter { it.isNotEmpty() } ?: return

    val mapValue = { value: String ->
        val value = LINE_ENDING_REGEX.replace(value, "\n")
        val out = StringBuilder(value)
        repeat(count - 1) {
            out.append(value)
        }
        out.toString()
    }
    // 'values' is asserted to have at least one entry above
    val last = values.last()
    val repeat = generateSequence(mapValue(last)) { it }
    val valuesIt = (values.map(mapValue).asSequence() + repeat).iterator()
    val transaction = editor.changeBySelection { range ->
        if (!range.isEmpty) {
            HJChange.Replace(range.from, range.to, valuesIt.next())
        } else null
    }
    editor.apply(transaction)
}

fun executeIdeAction(cx: HJActionContext, actionId: String, extendLines: Boolean = false) {
    val action = ActionManager.getInstance().getAction(actionId)
    if (action == null) {
        cx.hjEditor.setError("Could not find action with id '$actionId'")
        return
    }
    val selection = cx.hjEditor.selection.clone()
    if (extendLines) {
        extendLine(cx)
    }
    repeat(cx.count) {
        ActionManager.getInstance()
            .tryToExecute(action, null, cx.hjEditor.editor.contentComponent, ActionPlaces.UNKNOWN, true)
    }
    if (extendLines) {
        cx.hjEditor.setNewSelection(selection)
    }
}

fun executeIdeJumpAction(cx: HJActionContext, actionId: String) {
    pushJump(cx.hjEditor)
    executeIdeAction(cx, actionId)
}

private fun findChar(cx: HJActionContext, direction: HJDirection, inclusive: Boolean, extend: Boolean) {
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
                        Pair(true, HJDirection.Forward) -> cursorHead
                        Pair(true, HJDirection.Backward) -> cursorAnchor
                        Pair(false, HJDirection.Forward) -> cursorHead + 1
                        Pair(false, HJDirection.Backward) -> cursorAnchor - 1
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
                            Pair(true, HJDirection.Forward) -> pos
                            Pair(true, HJDirection.Backward) -> pos
                            Pair(false, HJDirection.Forward) -> pos - 1
                            Pair(false, HJDirection.Backward) -> pos + 1
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
            val transaction = cx.hjEditor.changeBySelection { range ->
                if (range.isEmpty) {
                    // no change
                    null
                } else {
                    val new = char.toString().repeat(range.len)
                    HJChange.Replace(range.from, range.to, new)
                }
            }
            cx.hjEditor.apply(transaction)
            switchToMode(cx, HJEditorMode.Normal)
        }
    }
}

private fun copySelectionOnLine(cx: HJActionContext, direction: HJDirection) {
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
                HJDirection.Forward -> anchorPos.line + offset
                HJDirection.Backward -> (anchorPos.line - offset).coerceAtLeast(0)
            }
            val headRow = when (direction) {
                HJDirection.Forward -> headPos.line + offset
                HJDirection.Backward -> (headPos.line - offset).coerceAtLeast(0)
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

private fun removePrimarySelection(cx: HJActionContext) {
    if (cx.hjEditor.selection.len == 1) {
        cx.hjEditor.setError("No selections remaining")
        return
    }
    val index = cx.hjEditor.selection.primaryIndex
    val selection = cx.hjEditor.selection.clone().remove(index)
    cx.hjEditor.setNewSelection(selection)
}

private fun switchCaseImpl(cx: HJActionContext, switchFun: (String) -> String) {
    val doc = cx.hjEditor.editor.document
    val transaction = cx.hjEditor.changeBySelection { range ->
        val text = doc.getText(TextRange(range.from, range.to))
        val newText = switchFun(text)
        HJChange.Replace(range.from, range.to, newText)
    }
    cx.hjEditor.apply(transaction)
    switchToMode(cx, HJEditorMode.Normal)
}

private fun scrollPageImpl(cx: HJActionContext, multiplier: Float, direction: HJDirection, moveCursor: Boolean) {
    collapseSelection(cx)
    val selection = cx.hjEditor.selection.clone()

    val scrollModel = cx.hjEditor.editor.scrollingModel
    val area = scrollModel.visibleArea
    val deltaOffset = (area.height * multiplier * direction.value).toInt()
    val newOffset = scrollModel.verticalScrollOffset + deltaOffset
    scrollModel.scrollVertically(newOffset)

    if (moveCursor) {
        val linesScrolled = deltaOffset / cx.hjEditor.editor.lineHeight
        val moveLine = { offset: Int ->
            val logical = cx.hjEditor.editor.offsetToLogicalPosition(offset)
            val line = (logical.line + linesScrolled).coerceIn(0, cx.hjEditor.editor.document.lineCount - 1)
            cx.hjEditor.editor.logicalPositionToOffset(LogicalPosition(line, logical.column))
        }
        selection.transform { range ->
            range.anchor = moveLine(range.anchor)
            range.head = moveLine(range.head)
            range
        }
        cx.hjEditor.setNewSelection(selection)
    }
}

private fun scrollPageByLineImpl(cx: HJActionContext, direction: HJDirection) {
    val scrollModel = cx.hjEditor.editor.scrollingModel
    val deltaOffset = cx.hjEditor.editor.lineHeight
    val newOffset = scrollModel.verticalScrollOffset + deltaOffset * direction.value
    scrollModel.scrollVertically(newOffset)
}

private fun alignViewMiddle(cx: HJActionContext) {
    // there is no horizontal position when softwrap is enabled
    if (cx.hjEditor.editor.settings.isUseSoftWraps) return

    val scrollingModel = cx.hjEditor.editor.scrollingModel

    val cursor = cx.hjEditor.selection.primary.cursor
    val cursorCol = cx.hjEditor.editor.offsetToVisualPosition(cursor).column

    val visibleWidth = scrollingModel.visibleArea.width
    val charWidth = EditorUtil.getPlainSpaceWidth(cx.hjEditor.editor)
    val scrollTo = (cursorCol - (visibleWidth / charWidth) / 2).coerceAtLeast(0) * charWidth

    scrollingModel.scrollHorizontally(scrollTo)
}

private fun alignViewImpl(cx: HJActionContext, align: HJAlignment) {
    val scrollingModel = cx.hjEditor.editor.scrollingModel

    val cursor = cx.hjEditor.selection.primary.cursor
    val cursorRow = cx.hjEditor.editor.offsetToVisualPosition(cursor).line

    val lineHeight = cx.hjEditor.editor.lineHeight
    val lastLineHeight = ((scrollingModel.visibleArea.height / lineHeight) - 1).coerceAtLeast(0)

    val relative = when (align) {
        HJAlignment.Center -> lastLineHeight / 2
        HJAlignment.Top -> HJState.config.scrolloff
        HJAlignment.Bottom -> lastLineHeight - HJState.config.scrolloff
    }

    val scrollTo = (cursorRow - relative).coerceAtLeast(0) * lineHeight
    scrollingModel.scrollVertically(scrollTo)
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
                    val selection = HJSelection.selectOnMatches(cx.doc, snapshot, regex)
                    if (selection != null) {
                        cx.hjEditor.setNewSelection(selection)
                    } else if (e == HJPromptEvent.Validate) {
                        cx.hjEditor.setError("Nothing selected")
                        HJActions.logger.debug("selectRegex - nothing is selected")
                    }
                }
            }

            else -> {}
        }
    }
    val commandBar = HJPrompt(cx.hjEditor, onEvent, "select:", HJRegister.LastSearch)
    commandBar.show()
}

private fun splitSelection(cx: HJActionContext) {
    val snapshot = cx.hjEditor.selection.clone()
    val onEvent = { e: HJPromptEvent, regex: String? ->
        when (e) {
            HJPromptEvent.Abort -> {
                cx.hjEditor.setNewSelection(snapshot)
            }

            HJPromptEvent.Update if regex != null -> {
                if (regex.isEmpty()) {
                    cx.hjEditor.setNewSelection(snapshot)
                } else {
                    val selection = HJSelection.splitOnMatches(cx.doc, snapshot, regex)
                    cx.hjEditor.setNewSelection(selection)
                }
            }

            else -> {}
        }
    }
    val commandBar = HJPrompt(cx.hjEditor, onEvent, "split:", HJRegister.LastSearch)
    commandBar.show()
}

private fun splitSelectionOnNewline(cx: HJActionContext) {
    val selection = HJSelection.splitOnNewline(cx.hjEditor.editor.document, cx.hjEditor.selection)
    cx.hjEditor.setNewSelection(selection)
}

private fun extendLine(cx: HJActionContext) {
    val below = cx.hjEditor.selection.primary.direction == HJDirection.Forward
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
        pushJump(cx.hjEditor)
        cx.hjEditor.setNewSelection(selection)
    }
}

private fun gotoFileStartImpl(cx: HJActionContext, extend: Boolean) {
    if (cx.rawCount != null) {
        gotoLineImpl(cx, extend)
    } else {
        val selection = cx.hjEditor.selection.clone().transform { it.putCursor(cx.doc, 0, extend) }
        pushJump(cx.hjEditor)
        cx.hjEditor.setNewSelection(selection)
    }
}

private fun gotoFileEndImpl(cx: HJActionContext, extend: Boolean) {
    val end = cx.doc.textLength
    val selection = cx.hjEditor.selection.clone().transform { it.putCursor(cx.doc, end, extend) }
    pushJump(cx.hjEditor)
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
    gotoLineStartEndImpl(cx, start, HJState.mode == HJEditorMode.Select)
}

private fun gotoFirstNonWhitespaceImpl(cx: HJActionContext, extend: Boolean) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val line = cx.doc.getLineNumber(range.cursor)
        val pos = firstNonWhitespaceChar(cx.doc.getLine(line)!!, cx.doc.getLineStartOffset(line))
        if (pos == null) {
            range
        } else {
            range.putCursor(cx.doc, pos, extend)
        }
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun gotoFirstNonWhitespace(cx: HJActionContext) {
    gotoFirstNonWhitespaceImpl(cx, HJState.mode == HJEditorMode.Select)
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
    gotoLineEndNewlineImpl(cx, HJState.mode == HJEditorMode.Select)
}

private fun gotoFileImpl(cx: HJActionContext, location: HJFileOpenLocation) {
    // TODO: detect URL/path from LSP?
    val project = cx.hjEditor.editor.project!!

    val relPath = Path(cx.hjEditor.editor.virtualFile.path)

    val paths = cx.hjEditor.selection.ranges.mapNotNull { range ->
        if (range.isSingleGrapheme) {
            // cap the search at 1000 chars around the cursor
            val lookaround = 1000
            val pos = range.cursor
            val searchStart = (pos - lookaround).coerceAtLeast(0)
            val searchEnd = (pos + lookaround).coerceAtMost(cx.doc.textLength - 1)
            val searchRange = cx.doc.charsSequence.subSequence(searchStart, searchEnd).toString()
            val path = findPaths(searchRange, true)
                .takeWhile { searchStart + it.first <= pos + 1 }
                .find { pos <= searchStart + it.last }
                ?.let { searchRange.substring(it) }

            HJActions.logger.debug("gotoFile auto-detected path: $path")

            path
        } else range.slice(cx.doc)?.trim()?.let { it.ifEmpty { null } }
    }

    for (path in paths) {
        if (BrowserUtil.isAbsoluteURL(path)) {
            BrowserUtil.browse(path)
            continue
        }

        var path = Path(path)
        path = relPath.resolve(path)
        val virtualFile = VfsUtil.findFile(path, true) ?: continue
        if (path.isDirectory()) {
            filePickerImpl(cx, "File Picker", virtualFile)
            break
        } else {
            openFile(cx, virtualFile, location)
        }
    }
}

private fun gotoDiagnosticImpl(cx: HJActionContext, gotoTarget: HJGotoTarget) {
    val diagnostics = getDiagnostics(cx.hjEditor.editor)

    val startOffsetFn = { diagnostic: HighlightInfo -> diagnostic.startOffset }
    val endOffsetFn = { diagnostic: HighlightInfo -> diagnostic.endOffset }
    val targetDiagnostic = gotoElementInList(cx, diagnostics, gotoTarget, startOffsetFn, endOffsetFn) ?: return

    pushJump(cx.hjEditor)
    cx.hjEditor.setNewSelection(HJSelection(HJRange(targetDiagnostic.startOffset, targetDiagnostic.endOffset)))
}

private fun gotoChangeImpl(cx: HJActionContext, gotoTarget: HJGotoTarget) {
    val psiFile = cx.hjEditor.psiFile ?: return
    val changedRangesInfo = VcsFacade.getInstance().getChangedRangesInfo(psiFile) ?: return
    val changes = changedRangesInfo.allChangedRanges

    val startOffsetFn = { change: TextRange -> change.startOffset }
    val endOffsetFn = { change: TextRange -> change.startOffset }
    val targetChange = gotoElementInList(cx, changes, gotoTarget, startOffsetFn, endOffsetFn) ?: return

    pushJump(cx.hjEditor)
    cx.hjEditor.setNewSelection(HJSelection(HJRange(targetChange.startOffset, targetChange.endOffset)))
}

private fun gotoWindowImpl(cx: HJActionContext, align: HJAlignment) {
    val view = cx.hjEditor.editor.scrollingModel.visibleArea
    val scrolloffOffset = HJState.config.scrolloff
    val countOffset = cx.rawCount ?: 0

    val topLine = (cx.hjEditor.editor.xyToLogicalPosition(Point(0, view.y)).line
            + scrolloffOffset) // TODO: should also consider sticky lines, but haven't found a way to get this info
    val bottomLine = (cx.hjEditor.editor.xyToLogicalPosition(Point(0, view.y + view.height)).line
            - 1 - scrolloffOffset)

    val visualLine = when (align) {
        HJAlignment.Top -> topLine + countOffset
        HJAlignment.Center -> (topLine + bottomLine) / 2
        HJAlignment.Bottom -> bottomLine - countOffset
    }.coerceIn(topLine..bottomLine)

    val offset = cx.doc.getLineStartOffset(visualLine)

    val selection = cx.hjEditor.selection.clone()
        .transform { it.putCursor(cx.doc, offset, HJState.mode == HJEditorMode.Select) }
    cx.hjEditor.setNewSelection(selection)
}

private fun gotoTsObjectImpl(cx: HJActionContext, tsObject: String, direction: HJDirection) {
    cx.hjEditor.applyMotion { hjEditor ->
        val syntax = hjEditor.syntax
        if (syntax == null) {
            hjEditor.setStatus("Syntax tree is not available in current buffer")
            return@applyMotion
        }

        val root = syntax.tree.rootNode

        val selection = hjEditor.selection.clone().transform { range ->
            val newRange = HJTreesitterHandling.gotoTreesitterObject(
                hjEditor.editor.document,
                range,
                tsObject,
                direction,
                root,
                syntax,
                cx.count
            )

            if (HJState.mode == HJEditorMode.Select) {
                val head = if (newRange.head < range.anchor) newRange.anchor else newRange.head
                HJRange(range.anchor, head)
            } else newRange.withDirection(direction)
        }

        pushJump(cx.hjEditor)
        hjEditor.setNewSelection(selection)
    }
}

private fun gotoParagraphImpl(cx: HJActionContext, direction: HJDirection) {
    cx.hjEditor.applyMotion { hJEditor ->
        val extend = HJState.mode == HJEditorMode.Select
        val moveFn = when (direction) {
            HJDirection.Forward -> ::moveNextParagraph
            HJDirection.Backward -> ::movePrevParagraph
        }

        val selection = hJEditor.selection.clone().transform { range ->
            moveFn(cx.doc, range, cx.count, extend)
        }
        hJEditor.setNewSelection(selection)
    }
}

private fun gotoLastAccessedFile(cx: HJActionContext) {
    val project = cx.hjEditor.editor.project!!
    val history = EditorHistoryManager.getInstance(project).fileList
    if (history.size < 2) return
    val lastFile = history[history.size - 2]
    openFile(cx, lastFile)
}

private fun gotoLastModifiedFile(cx: HJActionContext, gotoModification: Boolean) {
    val lastModification = HJState.lastModification ?: return
    val fileEditor = openFile(cx, lastModification.first) ?: return
    if (gotoModification) {
        val hjEditor = HJState.getHJEditor((fileEditor as TextEditor).editor) ?: return
        hjEditor.setNewSelection(HJSelection(HJRange.point(lastModification.second)))
    }
}

private fun moveNodeBoundImpl(cx: HJActionContext, direction: HJDirection, extend: Boolean) {
    cx.hjEditor.applyMotion { hjEditor ->
        hjEditor.syntax?.let { syntax ->
            val currentSelection = hjEditor.selection

            val selection = HJTreesitterHandling.moveParentNodeEnd(
                syntax,
                hjEditor.editor.document,
                currentSelection.clone(),
                direction,
                extend
            )

            hjEditor.setNewSelection(selection)
        }
    }
}

private fun expandSelection(cx: HJActionContext) {
    cx.hjEditor.applyMotion { hjEditor ->
        hjEditor.syntax?.let { syntax ->
            val currentSelection = hjEditor.selection
            val selection =
                HJTreesitterHandling.expandSelection(syntax, hjEditor.editor.document, currentSelection.clone())

            // check if selection is different from the last one
            if (currentSelection != selection) {
                // save current selection so it can be restored using shrink_selection
                hjEditor.objectSelections.add(currentSelection.clone())

                hjEditor.setNewSelection(selection)
            }
        }
    }
}

private fun shrinkSelection(cx: HJActionContext) {
    cx.hjEditor.applyMotion { hjEditor ->
        val currentSelection = hjEditor.selection

        // try to restore previous selection
        hjEditor.objectSelections.removeLastOrNull()?.let { prevSelection ->
            if (currentSelection.contains(prevSelection)) {
                hjEditor.setNewSelection(prevSelection)
                return@applyMotion
            } else {
                // clear existing selection as they can't be shrunk to anyway
                hjEditor.objectSelections.clear()
            }
        }

        // if not previous selection, shrink to first child
        hjEditor.syntax?.let { syntax ->
            val selection =
                HJTreesitterHandling.shrinkSelection(syntax, hjEditor.editor.document, currentSelection.clone())
            hjEditor.setNewSelection(selection)
        }
    }
}

private fun selectSiblingImpl(
    cx: HJActionContext,
    siblingFn: (HJLanguageSyntax, Document, HJSelection) -> HJSelection,
) {
    cx.hjEditor.applyMotion { hjEditor ->
        hjEditor.syntax?.let { syntax ->
            val currentSelection = hjEditor.selection
            val selection = siblingFn(syntax, hjEditor.editor.document, currentSelection.clone())
            hjEditor.setNewSelection(selection)
        }
    }
}

private fun selectAllImpl(cx: HJActionContext, selectFn: (HJLanguageSyntax, Document, HJSelection) -> HJSelection) {
    cx.hjEditor.syntax?.let { syntax ->
        val currentSelection = cx.hjEditor.selection
        val selection = selectFn(syntax, cx.doc, currentSelection.clone())
        cx.hjEditor.setNewSelection(selection)
    }
}

private fun jumpToWord(cx: HJActionContext, extend: Boolean) {
    // Calculate the jump candidates: ranges for any visible words with two or more characters.

    val alphabet = HJState.config.jumpLabelAlphabet
    val jumpLabelLimit = alphabet.size * alphabet.size
    val words = mutableListOf<HJRange>()

    val visibleArea = cx.hjEditor.editor.scrollingModel.visibleArea
    val start = cx.hjEditor.editor.logicalPositionToOffset(
        cx.hjEditor.editor.xyToLogicalPosition(Point(visibleArea.x, visibleArea.y))
    )
    val end = cx.hjEditor.editor.logicalPositionToOffset(
        cx.hjEditor.editor.xyToLogicalPosition(
            Point(visibleArea.x + visibleArea.width, visibleArea.y + visibleArea.height)
        )
    )

    val primarySelection = cx.hjEditor.selection.primary
    val cursor = primarySelection.cursor
    var cursorFwd = HJRange.point(cursor)
    var cursorRev = HJRange.point(cursor)
    if (cx.doc.charsSequence.getOrNull(cursor)?.let { !it.isWhitespace() } ?: false) {
        val cursorWordEnd = wordMove(cx.doc, cursorFwd, HJWordMotionTarget.NextWordEnd, 1)
        // single grapheme words need a special case
        if (cursorWordEnd.anchor == cursor) {
            cursorFwd = cursorWordEnd
        }
        val cursorWordStart = wordMove(cx.doc, cursorRev, HJWordMotionTarget.PrevWordStart, 1)
        if (cursorWordStart.anchor == nextGraphemeBoundary(cx.doc, cursor)) {
            cursorRev = cursorWordStart
        }
    }

    outer@ while (true) {
        var changed = false
        while (cursorFwd.head < end) {
            cursorFwd = wordMove(cx.doc, cursorFwd, HJWordMotionTarget.NextWordEnd, 1)
            // The cursor is on a word that is at least two graphemes long and made up of word characters. The latter
            // condition is needed because move_next_word_end simply treats a sequence of characters from the same char
            // class as a word so '=<' would also count as a word.
            val addLabel = cx.doc.charsSequence
                .take(cursorFwd.head)
                .reversed()
                .take(2)
                .takeWhile(::charIsWord)
                .count() == 2
            if (!addLabel) {
                continue
            }
            changed = true
            // skip any leading whitespace
            cursorFwd.anchor += cx.doc.charsSequence.drop(cursorFwd.anchor).takeWhile { !charIsWord(it) }.count()
            words.add(cursorFwd)
            if (words.size == jumpLabelLimit) {
                break@outer
            }
            break
        }
        while (cursorRev.head > start) {
            cursorRev = wordMove(cx.doc, cursorRev, HJWordMotionTarget.PrevWordStart, 1)
            // The cursor is on a word that is at least two graphemes long and made up of word characters. The latter
            // condition is needed because move_next_word_end simply treats a sequence of characters from the same char
            // class as a word so '=<' would also count as a word.
            val addLabel = cx.doc.charsSequence
                .drop(cursorRev.head)
                .take(2)
                .takeWhile(::charIsWord)
                .count() == 2
            if (!addLabel) {
                continue
            }
            changed = true
            cursorRev.anchor -= cx.doc.charsSequence.take(cursorRev.anchor).reversed().takeWhile { !charIsWord(it) }
                .count()
            words.add(cursorRev)
            if (words.size == jumpLabelLimit) {
                break@outer
            }
            break
        }
        if (!changed) {
            break
        }
    }
    jumpToLabel(cx, words, extend)
}

private fun jumpToLabel(cx: HJActionContext, labels: List<HJRange>, extend: Boolean) {
    val alphabet = HJState.config.jumpLabelAlphabet
    if (labels.isEmpty()) {
        return
    }

    // Add label for each jump candidate
    cx.hjEditor.setJumpLabels(labels.withIndex().map { (i, range) ->
        val firstChar = alphabet[i / alphabet.size]
        val secondChar = alphabet[i % alphabet.size]
        Pair(range.from, charArrayOf(firstChar, secondChar).concatToString())
    })

    // Accept two characters matching a visible label. Jump to the candidate for that label if it exists
    val primarySelection = cx.hjEditor.selection.primary
    HJInputHandler.onNextKey { inputKey ->
        val i = if (inputKey is HJInputKey.Character && !(inputKey.ctrl || inputKey.shift || inputKey.alt)) {
            inputKey.char.let { alphabet.indexOfOrNull(it) }
        } else null
        // bail if the given character is invalid or cannot be a jump label
        if (i == null || i * alphabet.size > labels.size) {
            cx.hjEditor.hideJumpLabels()
            return@onNextKey
        }
        val outer = i * alphabet.size
        HJInputHandler.onNextKey { inputKey ->
            cx.hjEditor.hideJumpLabels()

            val inner = if (inputKey is HJInputKey.Character && !(inputKey.ctrl || inputKey.shift || inputKey.alt)) {
                inputKey.char.let { alphabet.indexOfOrNull(it) }
            } else null
            if (inner == null) {
                return@onNextKey
            }
            labels.getOrNull(outer + inner)?.let { range ->
                val range = if (extend) {
                    val anchor = if (range.anchor < range.head) {
                        min(range.anchor, primarySelection.from)
                    } else {
                        max(range.anchor, primarySelection.to)
                    }
                    HJRange(anchor, range.head)
                } else {
                    range.withDirection(HJDirection.Forward)
                }
                cx.hjEditor.setNewSelection(HJSelection(range))
            }
        }
    }
}

private fun commandPalette(cx: HJActionContext) {
    val actions =
        HJState.config.allActionsWithBindings(HJState.mode).filterNot { (action, _) -> action.name == "no_op" }
    val commands = HJCommands.allCommands.map { command -> Pair(command, listOf<List<HJInputKey>>()) }
    val data = actions + commands

    val rowGetName = { rowIdx: Int ->
        when (val act = data[rowIdx].first) {
            is HJAction -> act.name
            is HJCommand -> ":${act.name}"
            else -> ""
        }
    }
    val rowGetDoc = { rowIdx: Int ->
        when (val act = data[rowIdx].first) {
            is HJAction -> if (act.isImplemented) act.doc else "Not implemented"
            is HJCommand -> act.doc
            else -> ""
        }
    }

    val model = object : AbstractTableModel() {
        override fun getRowCount() = data.size
        override fun getColumnCount() = 3

        override fun getValueAt(rowIndex: Int, columnIndex: Int) = when (columnIndex) {
            0 -> rowGetName(rowIndex)
            1 -> data[rowIndex].second.joinToString(" ") { keys -> keys.joinToString("") }
            2 -> rowGetDoc(rowIndex)
            else -> ""
        }

        override fun getColumnName(column: Int) = when (column) {
            0 -> "Name"
            1 -> "Bindings"
            2 -> "Doc"
            else -> ""
        }
    }

    HJPicker(cx.hjEditor.editor, "HelixJet Commands", model) { rowIdx ->
        when (val act = data[rowIdx].first) {
            is HJAction -> act.execute(cx)
            is HJCommand -> act.execute(cx, listOf(), listOf())
        }
    }.show()
}

private fun mergeSelections(cx: HJActionContext) {
    val selection = cx.hjEditor.selection.clone().mergeRanges()
    cx.hjEditor.setNewSelection(selection)
}

private fun mergeConsecutiveSelections(cx: HJActionContext) {
    val selection = cx.hjEditor.selection.clone().mergeConsecutiveRanges()
    cx.hjEditor.setNewSelection(selection)
}

private fun open(cx: HJActionContext, openBelow: Boolean, commentContinuationEnabled: Boolean) {
    insertMode(cx, false)

    var offs = 0
    val ranges = mutableListOf<HJRange>()

    val continueCommentTokens = if (commentContinuationEnabled && HJState.config.continueComments) {
        val language = cx.hjEditor.psiFile?.language
        if (language != null) {
            val commenter = LanguageCommenters.INSTANCE.forLanguage(language)
            commenter?.lineCommentPrefixes
        } else cx.hjEditor.language?.commentTokens
    } else null

    var transaction = cx.hjEditor.changeBySelection { range ->
        // the line number, where the cursor is currently
        val currLineNum = cx.doc.getLineNumber(if (openBelow) prevGraphemeBoundary(range.to) else range.from)
        // the next line number, where the cursor will be, after finishing the transaction
        val nextNewLineNum = if (openBelow) currLineNum + 1 else currLineNum
        val aboveNextNewLineNum = (nextNewLineNum - 1).coerceAtLeast(0)

        val continueCommentToken = continueCommentTokens?.let { getCommentToken(cx.doc, it, currLineNum) }

        // index to insert newlines after, as well as the char width to use to compensate for those inserted newlines
        val (aboveNextLineEndIndex, aboveNextLineEndWidth) = if (nextNewLineNum == 0) Pair(0, 0) else {
            Pair(cx.doc.getLineEndOffset(aboveNextNewLineNum), 1)
        }

        val line = cx.doc.getLine(currLineNum)!!
        val indent = when (val pos = firstNonWhitespaceChar(line)) {
            is Int if continueCommentToken != null -> line.substring(0, pos)
            else -> cx.hjEditor.codeStyleManager?.getLineIndent(cx.doc, cx.doc.getLineStartOffset(currLineNum))
                ?: (cx.hjEditor.language?.indent?.unit ?: "")
        }

        val builder = StringBuilder()
        if (!openBelow && nextNewLineNum == 0) {
            builder.append(indent)
            if (continueCommentToken != null) {
                builder.append(continueCommentToken)
                builder.append(" ")
            }
            builder.append("\n")
        } else {
            builder.append("\n")
            builder.append(indent)
            if (continueCommentToken != null) {
                builder.append(continueCommentToken)
                builder.append(" ")
            }
        }
        val text = builder.repeat(cx.count)

        // calculate new selection ranges
        val pos = offs + aboveNextLineEndIndex + aboveNextLineEndWidth
        val commentLen = continueCommentToken?.let { it.length + 1 } ?: 0 // + 1 for the extra space added
        for (i in 0 until cx.count) {
            // pos                     -> beginning of reference line,
            // + (i * (line_ending_len + indent_len + comment_len)) -> beginning of i'th line from pos (possibly including comment token)
            // + indent_len + comment_len ->        -> indent for i'th line
            ranges.add(
                HJRange.point(
                    pos + (i * (1 + indent.length + commentLen))
                            + indent.length + commentLen
                )
            )
        }

        // update the offset for the next range
        offs += text.length

        HJChange.Insert(aboveNextLineEndIndex, text)
    }

    transaction = transaction.copy(selection = HJSelection(ranges, cx.hjEditor.selection.primaryIndex))
    cx.hjEditor.apply(transaction)
}

private fun matchBrackets(cx: HJActionContext) {
    val isSelection = HJState.mode == HJEditorMode.Select
    val syntax = cx.hjEditor.syntax

    val selection = cx.hjEditor.selection.clone().transform { range ->
        val pos = range.cursor

        val matchedPos = if (syntax == null) HJMatchBrackets.findMatchingBracketPlaintext(cx.doc.text, pos)
        else HJMatchBrackets.findMatchingBracketFuzzy(syntax, cx.doc, pos)

        if (matchedPos != null) range.putCursor(cx.doc, matchedPos, isSelection)
        else range
    }

    cx.hjEditor.setNewSelection(selection)
}

private val surroundHelpTextLines = listOf(
    Pair("m", "Nearest matching pair"),
    Pair("( or )", "Parentheses"),
    Pair("{ or }", "Curly braces"),
    Pair("< or >", "Angled brackets"),
    Pair("[ or ]", "Square brackets"),
    Pair(" ", "... or any character"),
)

private fun surroundAdd(cx: HJActionContext) {
    HJInputHandler.onNextKey { inputKey ->
        val (open, close, surroundLen) = when (inputKey) {
            is HJInputKey.Character -> {
                val (o, c) = HJMatchBrackets.getPair(inputKey.char)
                Triple(o.toString(), c.toString(), 2)
            }

            is HJInputKey.Other if inputKey.code == KeyEvent.VK_ENTER -> Triple("\n", "\n", 2)
            else -> return@onNextKey
        }

        val selection = cx.hjEditor.selection
        val changes = mutableListOf<HJChange>()
        val ranges = mutableListOf<HJRange>()
        var offs = 0

        for (range in selection.ranges) {
            changes.add(HJChange.Insert(range.from, open))
            changes.add(HJChange.Insert(range.to, close))

            ranges.add(HJRange(offs + range.from, offs + range.to + surroundLen).withDirection(range.direction))

            offs += surroundLen
        }

        val transaction = HJTransaction(changes, HJSelection(ranges, selection.primaryIndex))
        cx.hjEditor.apply(transaction)
        switchToMode(cx, HJEditorMode.Normal)
    }

    HJState.getOrCreateHelpWidget(cx.hjEditor.editor)
        .setHelpContent("Surround selections with", surroundHelpTextLines.drop(1))
}

private fun surroundReplace(cx: HJActionContext) {
    HJInputHandler.onNextKey { inputKey ->
        val surroundCh = when (inputKey) {
            is HJInputKey.Character if inputKey.char == 'm' -> null
            is HJInputKey.Character -> inputKey.char
            is HJInputKey.Other if inputKey.code == KeyEvent.VK_SPACE -> ' '
            is HJInputKey.Other if inputKey.code == KeyEvent.VK_ENTER -> '\n'
            else -> return@onNextKey
        }
        val syntax = cx.hjEditor.syntax

        val changePos =
            HJSurround.getSurroundPos(syntax, cx.doc, cx.hjEditor.selection, surroundCh, cx.count)
                ?: return@onNextKey
        val selection = cx.hjEditor.selection.clone()
        val ranges = changePos.map { p -> HJRange.point(p) }
        cx.hjEditor.setNewSelection(HJSelection(ranges, selection.primaryIndex * 2))

        HJInputHandler.onNextKey { inputKey ->
            val to = when (inputKey) {
                is HJInputKey.Character -> inputKey.char
                is HJInputKey.Other if inputKey.code == KeyEvent.VK_SPACE -> ' '
                is HJInputKey.Other if inputKey.code == KeyEvent.VK_ENTER -> '\n'
                else -> {
                    cx.hjEditor.setNewSelection(selection)
                    return@onNextKey
                }
            }
            val (open, close) = HJMatchBrackets.getPair(to)

            // the changeset has to be sorted to allow nested surrounds TODO: Is this needed? Since apply sorts anyway
            val sortedPos = mutableListOf<Pair<Int, Char>>()
            for (p in changePos.chunked(2)) {
                sortedPos.add(Pair(p[0], open))
                sortedPos.add(Pair(p[1], close))
            }
            sortedPos.sortBy { (i, _) -> i }

            val transaction = HJTransaction(sortedPos.map { pos ->
                HJChange.Replace(pos.first, pos.first + 1, pos.second.toString())
            })
            cx.hjEditor.setNewSelection(selection)
            cx.hjEditor.apply(transaction)
            switchToMode(cx, HJEditorMode.Normal)
        }

        HJState.getOrCreateHelpWidget(cx.hjEditor.editor)
            .setHelpContent("Replace with a pair of", surroundHelpTextLines.drop(1))
    }

    HJState.getOrCreateHelpWidget(cx.hjEditor.editor)
        .setHelpContent("Replace surrounding pair of", surroundHelpTextLines)
}

private fun surroundDelete(cx: HJActionContext) {
    HJInputHandler.onNextKey { inputKey ->
        val surroundCh = when (inputKey) {
            is HJInputKey.Character if inputKey.char == 'm' -> null
            is HJInputKey.Character -> inputKey.char
            is HJInputKey.Other if inputKey.code == KeyEvent.VK_SPACE -> ' '
            is HJInputKey.Other if inputKey.code == KeyEvent.VK_ENTER -> '\n'
            else -> return@onNextKey
        }
        val syntax = cx.hjEditor.syntax

        val changePos =
            HJSurround.getSurroundPos(syntax, cx.doc, cx.hjEditor.selection, surroundCh, cx.count)
                ?: return@onNextKey

        val transaction = HJTransaction(changePos.map { HJChange.Delete(it, it + 1) })
        cx.hjEditor.apply(transaction)
        switchToMode(cx, HJEditorMode.Normal)
    }

    HJState.getOrCreateHelpWidget(cx.hjEditor.editor)
        .setHelpContent("Delete surrounding pair of", surroundHelpTextLines)
}

private fun searchImpl(
    cx: HJActionContext,
    snapshot: HJSelection,
    regex: Regex,
    extend: Boolean,
    direction: HJDirection,
    showWarnings: Boolean,
) {
    val wrapAround = HJState.config.searchWrapAround
    val doc = cx.doc.text

    // get the right side of the primary block cursor for forward search, or the grapheme before the start of the selection for reverse
    val start = when (direction) {
        HJDirection.Forward -> snapshot.primary.to
        HJDirection.Backward -> snapshot.primary.from
    }

    var mat = when (direction) {
        HJDirection.Forward -> regex.find(doc, start)
        HJDirection.Backward -> regex.findAll(doc.take(start)).lastOrNull()
    }

    if (mat == null) {
        if (wrapAround) {
            mat = when (direction) {
                HJDirection.Forward -> regex.find(doc)
                HJDirection.Backward -> regex.findAll(doc, start).lastOrNull()
            }
        }
        if (showWarnings) {
            if (wrapAround && mat != null) {
                cx.hjEditor.setStatus("Wrapped around document")
            } else {
                cx.hjEditor.setError("No more matches")
            }
        }
    }

    if (mat != null) {
        val start = mat.range.first
        val end = mat.range.last + 1

        if (end == 0) {
            // skip empty matches that don't make sense
            return
        }

        // determine range direction based on the primary range
        val primary = snapshot.primary
        val range = HJRange(start, end).withDirection(primary.direction)

        val selection = if (extend) {
            snapshot.clone().push(range)
        } else {
            snapshot.clone().replace(snapshot.primaryIndex, range)
        }

        cx.hjEditor.setNewSelection(selection)
    }
}

private fun searcher(cx: HJActionContext, direction: HJDirection) {
    val extend = HJState.mode == HJEditorMode.Select
    val snapshot = cx.hjEditor.selection.clone()

    val onEvent = onEvent@{ e: HJPromptEvent, pattern: String? ->
        when (e) {
            HJPromptEvent.Abort -> {
                cx.hjEditor.setNewSelection(snapshot)
            }

            HJPromptEvent.Update if pattern != null -> {
                val regex = regex(pattern)
                if (regex == null) {
                    cx.hjEditor.setError("Invalid regex: $pattern")
                } else {
                    searchImpl(cx, snapshot, regex, extend, direction, false)
                }
            }

            else -> {}
        }
    }
    val commandBar = HJPrompt(cx.hjEditor, onEvent, "search:", HJRegister.LastSearch)
    commandBar.show()
}

private fun searchNextOrPrevImpl(cx: HJActionContext, extend: Boolean, direction: HJDirection) {
    val register = HJRegister.LastSearch

    HJState.registers.first(register, cx.hjEditor)?.let { pattern ->
        val regex = regex(pattern)
        if (regex == null) {
            cx.hjEditor.setError("Invalid regex: $pattern")
        } else {
            repeat(cx.count) {
                searchImpl(cx, cx.hjEditor.selection, regex, extend, direction, true)
            }
        }
    }
}

private fun selectTextObject(cx: HJActionContext, objtype: HJTextObject.TextObject) {
    HJInputHandler.onNextKey { inputKey ->
        if (inputKey !is HJInputKey.Character) return@onNextKey
        cx.hjEditor.applyMotion { hjEditor ->
            val textobjectTreesitter = textobjectTreesitter@{ objName: String, range: HJRange ->
                val syntax = hjEditor.syntax ?: return@textobjectTreesitter range
                HJTextObject.textobjectTreesitter(cx.doc, range, objtype, objName, syntax)
            }

            val textObjectChange = textObjectChange@{ range: HJRange ->
                val textRange = HJVCSHandling.getHunk(cx.hjEditor, range) ?: return@textObjectChange range
                HJRange(textRange.startOffset, textRange.endOffset).withDirection(range.direction)
            }

            val selection = hjEditor.selection.clone().transform { range ->
                when (inputKey.char) {
                    'w' -> HJTextObject.textObjectWord(cx.doc, range, objtype, false)
                    'W' -> HJTextObject.textObjectWord(cx.doc, range, objtype, true)
                    't' -> textobjectTreesitter("class", range)
                    'f' -> textobjectTreesitter("function", range)
                    'a' -> textobjectTreesitter("parameter", range)
                    'c' -> textobjectTreesitter("comment", range)
                    'e' -> textobjectTreesitter("test", range)
                    'x' -> textobjectTreesitter("xml-element", range)
                    'p' -> HJTextObject.textObjectParagraph(cx.doc, range, objtype, cx.count)
                    'm' -> HJTextObject.textObjectPairSurroundClosest(
                        cx.hjEditor.syntax, cx.doc, range, objtype, cx.count
                    )

                    'g' -> textObjectChange(range)
                    else if !inputKey.char.isLetterOrDigit() -> HJTextObject.textObjectPairSurround(
                        cx.hjEditor.syntax, cx.doc, range, objtype, inputKey.char, cx.count
                    )

                    else -> range
                }
            }

            hjEditor.setNewSelection(selection)
        }
    }

    val title = when (objtype) {
        HJTextObject.TextObject.Inside -> "Match inside"
        HJTextObject.TextObject.Around -> "Match around"
        else -> return
    }
    HJState.getOrCreateHelpWidget(cx.hjEditor.editor).setHelpContent(
        title, listOf(
            Pair("w", "Word"),
            Pair("W", "WORD"),
            Pair("p", "Paragraph"),
            Pair("t", "Type definition"),
            Pair("f", "Function"),
            Pair("a", "Argument/parameter"),
            Pair("c", "Comment"),
            Pair("T", "Test"),
            Pair("e", "Data structure entry"),
            Pair("m", "Closest surrounding pair"),
            Pair("g", "Change"),
            Pair("x", "(X)HTML element"),
            Pair(" ", "... or any character acting as a pair"),
        )
    )
}

private fun filePickerImpl(cx: HJActionContext, title: String, directory: VirtualFile) {
    val project = cx.hjEditor.editor.project!!

    val data = getAllProjectFiles(project, directory)

    val directoryPathLength = directory.path.length
    val model = HJTableModel(data, listOf("File")) { file, _ -> file.path.drop(directoryPathLength) }

    val previewSelector = { rowIdx: Int -> Pair(data[rowIdx], null) }

    HJPickerWithPreview(cx.hjEditor.editor, title, model, previewSelector) { rowIdx ->
        val file = data[rowIdx]
        openFile(cx, file)
    }.show()
}

private fun changedFilePicker(cx: HJActionContext) {
    val project = cx.hjEditor.editor.project!!

    val changeListManager = ChangeListManager.getInstance(project)
    val data = changeListManager.allChanges.distinctBy { it.virtualFile }

    val projectRoot = project.guessProjectDir()?.path ?: ""
    val model = HJTableModel(data, listOf("Change", "Path")) { change, col ->
        when (col) {
            0 -> when (change.type) {
                Change.Type.NEW -> "Added"
                Change.Type.DELETED -> "Deleted"
                Change.Type.MODIFICATION -> "Modified"
                Change.Type.MOVED -> "Moved"
            }

            1 -> change.virtualFile?.path?.removePrefix(projectRoot) ?: ""
            else -> ""
        }
    }

    val columnColors = listOf(
        Pair(0) { value: String ->
            when (value) {
                "Added" -> JBColor.green
                "Deleted" -> JBColor.red
                "Modified", "Moved" -> JBColor.blue
                else -> null
            }
        }
    )

    HJPicker(cx.hjEditor.editor, "Changed File Picker", model, columnColors) callback@{ rowIdx ->
        val file = data[rowIdx].virtualFile ?: return@callback
        openFile(cx, file)
    }.show()
}

private fun filePickerProjectRoot(cx: HJActionContext) {
    val projectDir = cx.hjEditor.editor.project!!.guessProjectDir()
    if (projectDir == null) {
        cx.hjEditor.setError("Could not find project directory")
    } else {
        filePickerImpl(cx, "File Picker (root)", projectDir)
    }
}

private fun insertWithIndent(cx: HJActionContext, fallbackLineStart: Boolean) {
    insertMode(cx, false)

    val psiFile = cx.hjEditor.psiFile

    val ranges = mutableListOf<HJRange>()
    var offs = 0

    var transaction = cx.hjEditor.changeBySelection { range ->
        val cursorLine = range.cursorLine(cx.doc)
        val cursorLineStart = cx.doc.getLineStartOffset(cursorLine)

        if (cx.doc.getLineEndOffset(cursorLine) == cursorLineStart) {
            // line is empty -> auto indent
            val lineEndIndex = cursorLineStart

            val indent = if (psiFile != null) getIndentForLine(psiFile, cursorLineStart) else ""

            // calculate new selection ranges
            val pos = offs + cursorLineStart
            val indentWidth = indent.length
            ranges.add(HJRange.point(pos + indentWidth))
            offs += indentWidth

            HJChange.Insert(lineEndIndex, indent)
        } else {
            // move cursor to the fallback position
            val pos = if (fallbackLineStart) {
                cx.doc.getLine(cursorLine)?.let { firstNonWhitespaceChar(it, cursorLineStart) } ?: cursorLineStart
            } else {
                cx.doc.getLineEndOffset(cursorLine)
            }

            ranges.add(range.putCursor(cx.doc, pos + offs, false))

            null
        }
    }

    transaction = transaction.copy(selection = HJSelection(ranges, cx.hjEditor.selection.primaryIndex))
    cx.hjEditor.apply(transaction)
}

private fun searchSelectionImpl(cx: HJActionContext, detectWordBoundaries: Boolean) {
    fun isAtWordStart(doc: Document, index: Int): Boolean {
        if (index == doc.textLength) return false

        val ch = doc.charsSequence[index]
        if (index == 0) return charIsWord(ch)

        val prevCh = doc.charsSequence[index - 1]
        return !charIsWord(prevCh) && charIsWord(ch)
    }

    fun isAtWordEnd(doc: Document, index: Int): Boolean {
        if (index == 0 || index == doc.textLength) return false

        val ch = doc.charsSequence[index]
        val prevCh = doc.charsSequence[index - 1]

        return charIsWord(prevCh) && !charIsWord(ch)
    }

    val register = cx.register ?: HJRegister.LastSearch

    val regex = cx.hjEditor.selection.ranges.map { range ->
        val addBoundaryPrefix = detectWordBoundaries && isAtWordStart(cx.doc, range.from)
        val addBoundarySuffix = detectWordBoundaries && isAtWordEnd(cx.doc, range.to)

        val prefix = if (addBoundaryPrefix) "\\b" else ""
        val suffix = if (addBoundarySuffix) "\\b" else ""

        val word = Regex.escape(range.slice(cx.doc) ?: "")
        "$prefix$word$suffix"
    }.toHashSet()
        .toList()
        .joinToString(separator = "|")

    if (HJState.registers.push(register, regex, cx.hjEditor)) {
        cx.hjEditor.setStatus("register '$register' set to '$regex'")
    }
}

private fun makeSearchWordBounded(cx: HJActionContext) {
    val register = cx.register ?: HJRegister.LastSearch
    val regex = HJState.registers.first(register, cx.hjEditor) ?: return

    val startAnchored = regex.startsWith("\\b")
    val endAnchored = regex.endsWith("\\b")

    if (startAnchored && endAnchored) return

    var newRegex = ""
    if (!startAnchored) newRegex += "\\b"
    newRegex += regex
    if (!endAnchored) newRegex += "\\b"

    if (HJState.registers.push(register, newRegex, cx.hjEditor)) {
        cx.hjEditor.setStatus("register '$register' set to '$newRegex'")
    }
}

private fun selectLineImpl(cx: HJActionContext, above: Boolean) {
    var count = cx.count
    val saturatingAdd = { a: Int, b: Int -> (a + b).coerceAtMost(cx.doc.lineCount) }

    val selection = cx.hjEditor.selection.clone().transform { range ->
        val (startLine, endLine) = range.lineRange(cx.doc)
        val start = cx.doc.getLineStartOffset(startLine)
        val end = 1 + cx.doc.getLineEndOffset(endLine)
        val direction = range.direction

        // Extending to line bounds is counted as one step
        if (range.from != start || range.to != end) {
            count = (count - 1).coerceAtLeast(0)
        }
        val (anchorLine, headLine) = when (Pair(above, direction)) {
            Pair(true, HJDirection.Forward) -> Pair(startLine, (endLine - count).coerceAtLeast(0))
            Pair(true, HJDirection.Backward) -> Pair(endLine, (startLine - count).coerceAtLeast(0))
            Pair(false, HJDirection.Forward) -> Pair(startLine, saturatingAdd(endLine, count))
            Pair(false, HJDirection.Backward) -> Pair(endLine, saturatingAdd(startLine, count))
            else -> Pair(0, 0) // unreachable
        }
        val (anchor, head) = when (anchorLine.compareTo(headLine)) {
            in Int.MIN_VALUE..-1 /* LESS THAN */ -> Pair(
                cx.doc.getLineStartOffset(anchorLine),
                1 + cx.doc.getLineEndOffset(headLine)
            )

            0 /* EQUAL */ -> if (above) Pair(
                1 + cx.doc.getLineEndOffset(anchorLine),
                cx.doc.getLineStartOffset(headLine)
            ) else Pair(
                cx.doc.getLineStartOffset(headLine),
                1 + cx.doc.getLineEndOffset(anchorLine)
            )

            in 1..Int.MAX_VALUE /* GREATER THAN */ -> Pair(
                1 + cx.doc.getLineEndOffset(anchorLine),
                cx.doc.getLineStartOffset(headLine)
            )

            else -> Pair(0, 0) // unreachable
        }

        HJRange(anchor, head)
    }

    cx.hjEditor.setNewSelection(selection)
}

private fun extendToLineBounds(cx: HJActionContext) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val (startLine, endLine) = range.lineRange(cx.doc)
        val start = cx.doc.getLineStartOffset(startLine)
        val end = 1 + cx.doc.getLineEndOffset(endLine)

        HJRange(start, end).withDirection(range.direction)
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun shrinkToLineBounds(cx: HJActionContext) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val (startLine, endLine) = range.lineRange(cx.doc)

        // Do nothing if the selection is within one line to prevent conditional logic for the behaviour of this command
        if (startLine == endLine) return@transform range

        var start = cx.doc.getLineStartOffset(startLine)
        var end = 1 + cx.doc.getLineEndOffset(endLine)

        if (start != range.from) {
            start = 1 + cx.doc.getLineEndOffset(startLine)
        }
        if (end != range.to) {
            end = cx.doc.getLineStartOffset(endLine)
        }
        HJRange(start, end).withDirection(range.direction)
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun flipSelections(cx: HJActionContext) {
    cx.hjEditor.setNewSelection(cx.hjEditor.selection.clone().transform(HJRange::flip))
}

private fun ensureSelectionsForward(cx: HJActionContext) {
    cx.hjEditor.setNewSelection(cx.hjEditor.selection.clone().transform { it.withDirection(HJDirection.Forward) })
}

private fun bufferPicker(cx: HJActionContext) {
    val project = cx.hjEditor.editor.project!!

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    val data = fileEditorManager.allEditors.toList()
    val rootDir = project.guessProjectDir()?.path ?: ""

    val model = HJTableModel(data, listOf("Flags", "Path")) { fileEditor, col ->
        when (col) {
            0 -> if (fileEditor.isModified) "*" else ""
            1 -> fileEditor.file.path.removePrefix(rootDir)
            else -> ""
        }
    }

    val previewSelector = { rowIdx: Int -> Pair(data[rowIdx].file, null) }

    HJPickerWithPreview(cx.hjEditor.editor, "Buffer Picker", model, previewSelector) callback@{ rowIdx ->
        val fileEditor = data[rowIdx]
        var composite: EditorComposite? = null

        val window = fileEditorManager.windows.find { window ->
            window.allComposites.any {
                if (it.allEditors.contains(fileEditor)) {
                    composite = it
                    true
                } else false
            }
        } ?: return@callback

        window.setSelectedComposite(composite!!, true)
    }.show()
}

private fun symbolPicker(cx: HJActionContext) {
    val psiFile = cx.hjEditor.psiFile ?: return

    getStructureElements(cx.hjEditor.editor, psiFile) { data ->
        val model = HJTableModel(data, listOf("Kind", "Name", "Container")) { tag, col ->
            when (col) {
                0 -> tag.kind
                1 -> tag.name
                2 -> tag.container
                else -> ""
            }
        }

        val previewSelector = { rowIdx: Int ->
            val tag = data[rowIdx]
            Pair(tag.file, Pair(tag.startOffset, tag.endOffset))
        }

        HJPickerWithPreview(cx.hjEditor.editor, "Symbol Picker", model, previewSelector) callback@{ rowIdx ->
            val tag = data[rowIdx]
            val start = tag.startOffset
            val end = tag.endOffset
            openFile(cx, cx.hjEditor.editor.virtualFile) // necessary in case the picker is reopened from elsewhere
            cx.hjEditor.setNewSelection(HJSelection(HJRange(start, end)))
        }.show()
    }
}

private fun diagnosticPicker(cx: HJActionContext) {
    val data = getDiagnostics(cx.hjEditor.editor)

    val model = HJTableModel(data, listOf("Severity", "Message")) { highlightInfo, col ->
        when (col) {
            0 -> when (val severity = highlightInfo.severity) {
                HighlightSeverity.ERROR -> "Error"
                HighlightSeverity.WARNING -> "Warning"
                HighlightSeverity.INFORMATION -> "Info"
                HighlightSeverity.WEAK_WARNING -> "Hint"
                HighlightSeverity.TEXT_ATTRIBUTES -> "Style"
                else if severity.name == "TYPO" -> "Typo"
                else -> ""
            }

            1 -> highlightInfo.description
            else -> ""
        }
    }

    val previewSelector =
        { rowIdx: Int -> Pair(cx.hjEditor.editor.virtualFile, Pair(data[rowIdx].startOffset, data[rowIdx].endOffset)) }

    HJPickerWithPreview(cx.hjEditor.editor, "Diagnostic Picker", model, previewSelector) { rowIdx ->
        val info = data[rowIdx]
        openFile(cx, cx.hjEditor.editor.virtualFile) // necessary in case the picker is reopened from elsewhere
        cx.hjEditor.setNewSelection(HJSelection(HJRange(info.startOffset, info.endOffset)))
    }.show()
}

private fun diagnosticPickerWorkspace(cx: HJActionContext) {
    val project = cx.hjEditor.editor.project!!

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    val data = fileEditorManager.allEditors
        .filterIsInstance<TextEditor>()
        .flatMap { textEditor ->
            getDiagnostics(textEditor.editor).map { Pair(textEditor.editor, it) }
        }

    val projectRoot = project.guessProjectDir()?.path ?: ""
    val model = HJTableModel(data, listOf("Severity", "Path", "Message")) { (editor, highlightInfo), col ->
        when (col) {
            0 -> when (val severity = highlightInfo.severity) {
                HighlightSeverity.ERROR -> "Error"
                HighlightSeverity.WARNING -> "Warning"
                HighlightSeverity.INFORMATION -> "Info"
                HighlightSeverity.WEAK_WARNING -> "Hint"
                HighlightSeverity.TEXT_ATTRIBUTES -> "Style"
                else if severity.name == "TYPO" -> "Typo"
                else -> ""
            }

            1 -> editor.virtualFile.path.removePrefix(projectRoot)

            2 -> highlightInfo.description
            else -> ""
        }
    }

    val previewSelector = previewSelector@{ rowIdx: Int ->
        val (editor, hlInfo) = data.getOrNull(rowIdx) ?: return@previewSelector null
        Pair(editor.virtualFile, Pair(hlInfo.startOffset, hlInfo.endOffset))
    }

    HJPickerWithPreview(
        cx.hjEditor.editor, "Workspace Diagnostic Picker (Opened and Analyzed Files)", model, previewSelector
    ) { rowIdx ->
        val (editor, info) = data[rowIdx]

        // select buffer
        openFile(cx, editor.virtualFile)

        // set selection
        val hjEditor = HJState.getHJEditor(editor)
        hjEditor?.setNewSelection(HJSelection(HJRange(info.startOffset, info.endOffset)))
    }.show()
}

private fun addNewlineImpl(cx: HJActionContext, above: Boolean) {
    val transaction = cx.hjEditor.changeBySelection { range ->
        val (start, end) = range.lineRange(cx.doc)
        val pos = if (above) cx.doc.getLineStartOffset(start)
        else cx.doc.getLineEndOffset(end)
        HJChange.Insert(pos, "\n".repeat(cx.count))
    }

    cx.hjEditor.apply(transaction)
}

private fun gotoColumnImpl(cx: HJActionContext, extend: Boolean) {
    val selection = cx.hjEditor.selection.clone().transform { range ->
        val line = range.cursorLine(cx.doc)
        val lineStart = cx.doc.getLineStartOffset(line)
        val lineEnd = cx.doc.getLineEndOffset(line)
        val pos = (lineStart + cx.count).coerceAtMost(lineEnd)
        range.putCursor(cx.doc, pos, extend)
    }
    pushJump(cx.hjEditor)
    cx.hjEditor.setNewSelection(selection)
}

private fun trimSelections(cx: HJActionContext) {
    val ranges = cx.hjEditor.selection.ranges.filterNot { range ->
        range.isEmpty || (range.slice(cx.doc)?.all { it.isWhitespace() } ?: false)
    }.map { range ->
        var start = range.from
        var end = range.to
        start = skipWhile(cx.doc.text, start) { it.isWhitespace() } ?: start
        end = backwardsSkipWhile(cx.doc.text, end) { it.isWhitespace() } ?: end
        HJRange(start, end).withDirection(range.direction)
    }

    if (ranges.isNotEmpty()) {
        val primary = cx.hjEditor.selection.primary
        val idx = ranges.indexOfFirst { it.overlaps(primary) }
        val primaryIndex = if (idx == -1) ranges.size - 1 else idx
        cx.hjEditor.setNewSelection(HJSelection(ranges, primaryIndex))
    } else {
        collapseSelection(cx)
        keepPrimarySelection(cx)
    }
}

private fun joinSelectionsImpl(cx: HJActionContext, selectSpace: Boolean) {
    val commentTokens = mutableListOf<String>()
    cx.hjEditor.psiFile?.language?.let { language ->
        val commenter = LanguageCommenters.INSTANCE.forLanguage(language)
        commenter?.lineCommentPrefixes?.let { commentTokens.addAll(it) }
    }
    commentTokens.sortByDescending { it.length }

    var changes = mutableListOf<HJChange>()

    for (selection in cx.hjEditor.selection.ranges) {
        var (start, end) = selection.lineRange(cx.doc)
        if (start == end) {
            end = (end + 1).coerceAtMost(cx.doc.textLength - 1)
        }
        val lines = start until end

        var firstLineIdx = cx.doc.getLineStartOffset(start)
        firstLineIdx = skipWhile(cx.doc.text, firstLineIdx) { it == ' ' || it == '\t' } ?: firstLineIdx
        val firstLine = cx.doc.text.substring(firstLineIdx)
        var currentCommentToken = commentTokens.find { firstLine.startsWith(it) }

        for (line in lines) {
            val start = cx.doc.getLineEndOffset(line)
            var end = cx.doc.getLineStartOffset(line + 1)
            end = skipWhile(cx.doc.text, end) { it == ' ' || it == '\t' } ?: end
            val sliceFromEnd = cx.doc.text.substring(end)
            commentTokens.find { sliceFromEnd.startsWith(it) }?.let { token ->
                if (currentCommentToken == token) {
                    end += token.count()
                    end = skipWhile(cx.doc.text, end) { it == ' ' || it == '\t' } ?: end
                } else {
                    // update current token, but don't delete this one
                    currentCommentToken = token
                }
            }

            if (end == cx.doc.getLineEndOffset(line + 1)) {
                // the joining line contains only space-characters => don't include a whitespace when joining
                changes.add(HJChange.Delete(start, end))
            } else {
                changes.add(HJChange.Replace(start, end, " "))
            }
        }
    }

    // nothing to do, bail out early
    if (changes.isEmpty()) return

    changes = changes.sortedBy { it.fromRank }.distinct().toMutableList()

    // select inserted spaces
    val transaction = if (selectSpace) {
        var offset = 0
        val ranges = changes.map {
            if (it is HJChange.Delete) {
                offset += it.to - it.from
                null
            } else {
                val range = HJRange.point(it.fromRank - offset)
                offset += it.toRank - it.fromRank - 1 // -1 adjusts for the replacement of the range by a space
                range
            }
        }.filterNotNull()
        val t = HJTransaction(changes)
        if (ranges.isEmpty()) {
            t
        } else {
            t.copy(selection = HJSelection(ranges, 0))
        }
    } else {
        HJTransaction(changes)
    }

    cx.hjEditor.apply(transaction)
}

private fun keepOrRemoveSelectionsImpl(cx: HJActionContext, remove: Boolean) {
    val reg = cx.register ?: HJRegister.LastSearch
    val snapshot = cx.hjEditor.selection.clone()

    val onEvent = onEvent@{ e: HJPromptEvent, regex: String? ->
        when (e) {
            HJPromptEvent.Abort -> {
                cx.hjEditor.setNewSelection(snapshot)
            }

            HJPromptEvent.Update -> {
                if (regex.isNullOrEmpty()) return@onEvent
                val regex = regex(regex) ?: return@onEvent
                val selection = keepOrRemoveMatches(cx.doc.text, snapshot, regex, remove)
                if (selection != null) {
                    cx.hjEditor.setNewSelection(selection)
                } else {
                    cx.hjEditor.setError("No selections remaining")
                }
            }

            else -> {}
        }
    }
    val commandBar = HJPrompt(cx.hjEditor, onEvent, if (remove) "remove:" else "keep:", reg)
    commandBar.show()
}

private fun alignSelections(cx: HJActionContext) {
    val columnWidths = mutableListOf<MutableList<Pair<Int, Int>>>()
    var lastLine = cx.doc.lineCount + 1
    var col = 0

    for (range in cx.hjEditor.selection.ranges) {
        val coords = cx.hjEditor.editor.offsetToVisualPosition(range.head)
        val anchorCoords = cx.hjEditor.editor.offsetToVisualPosition(range.anchor)

        if (coords.line != anchorCoords.line) {
            cx.hjEditor.setError("Align cannot work with multi line selections")
            return
        }

        col = if (coords.line == lastLine) col + 1 else 0

        if (col >= columnWidths.size) {
            columnWidths.add(mutableListOf())
        }
        columnWidths[col].add(Pair(range.from, coords.column))

        lastLine = coords.line
    }

    val changes = mutableListOf<HJChange>()

    // account for changes on each row
    val len = columnWidths.firstOrNull()?.size ?: 0
    val offs = IntArray(len)

    for (col in columnWidths) {
        val maxCol = col.withIndex().map { (row, cursor) -> cursor.second + offs[row] }.maxOrNull() ?: 0

        for ((row, pair) in col.withIndex()) {
            val (insertPos, lastCol) = pair
            val insCount = maxCol - (lastCol + offs[row])

            if (insCount == 0) continue

            offs[row] += insCount

            changes.add(HJChange.Insert(insertPos, " ".repeat(insCount)))
        }
    }

    // The changeset has to be sorted
    changes.sortBy { it.fromRank }

    val transaction = HJTransaction(changes)
    cx.hjEditor.apply(transaction)
    exitSelectMode(cx)
}

private fun rotateSelections(cx: HJActionContext, direction: HJDirection) {
    val selection = cx.hjEditor.selection.clone()
    val index = selection.primaryIndex
    val len = selection.len
    selection.primaryIndex = when (direction) {
        HJDirection.Forward -> (index + cx.count) % len
        HJDirection.Backward -> (index + ((len - cx.count).coerceAtLeast(0) % len)) % len
    }
    cx.hjEditor.setNewSelection(selection)
}

private fun rotateSelectionsFirstLast(cx: HJActionContext, direction: HJDirection) {
    val selection = cx.hjEditor.selection.clone()
    selection.primaryIndex = if (direction == HJDirection.Backward) 0 else selection.len - 1
    cx.hjEditor.setNewSelection(selection)
}

private enum class HJReorderStrategy {
    RotateForward, RotateBackward, Reverse,
}

private fun reorderSelectionContents(cx: HJActionContext, strategy: HJReorderStrategy) {
    val selection = cx.hjEditor.selection
    var ranges = selection.slices(cx.doc).filterNotNull().toMutableList()
    val rotateBy = cx.rawCount?.coerceAtMost(ranges.size) ?: 1

    val primaryIndex = when (strategy) {
        HJReorderStrategy.RotateForward -> {
            Collections.rotate(ranges, rotateBy)
            (selection.primaryIndex + rotateBy) % ranges.size
        }

        HJReorderStrategy.RotateBackward -> {
            Collections.rotate(ranges, -rotateBy)
            (selection.primaryIndex - rotateBy) % ranges.size
        }

        HJReorderStrategy.Reverse -> {
            if (rotateBy % 2 == 0) {
                return
            }
            ranges = ranges.reversed().toMutableList()
            // -1 to turn 1-based size into 0-based index
            (ranges.size - 1) - selection.primaryIndex
        }
    }

    val transaction = HJTransaction(selection.ranges.zip(ranges).map { (range, fragment) ->
        HJChange.Replace(range.from, range.to, fragment)
    })

    cx.hjEditor.setNewSelection(HJSelection(selection.ranges, primaryIndex))
    cx.hjEditor.apply(transaction)
}

private fun getWindowInDirection(cx: HJActionContext, direction: HJNavDirection): EditorWindow? {
    val project = cx.hjEditor.editor.project!!
    val manager = FileEditorManagerEx.getInstanceEx(project)
    val currentWindow = manager.currentWindow ?: return null

    val getScreenBounds = getScreenBounds@{ window: EditorWindow ->
        val component = window.selectedComposite?.component ?: return@getScreenBounds null
        val location = component.locationOnScreen
        Rectangle(location.x, location.y, component.width, component.height)
    }

    val currentBounds = getScreenBounds(currentWindow) ?: return null

    // find minimum by (dist, bounds.x, bounds.y) instead of just dist so that we have the first (topmost, leftmost)
    // window when switching to a split. First we compute the bounds, then find min by above criteria
    val result = manager.windows
        .mapNotNull { window ->
            val bounds = getScreenBounds(window) ?: return@mapNotNull null
            Pair(window, bounds)
        }
        .minWithOrNull(
            compareBy<Pair<EditorWindow, Rectangle>> { (window, bounds) ->
                when (direction) {
                    else if (window == currentWindow) -> Int.MAX_VALUE - 1 // set currentWindow as fallback
                    HJNavDirection.Left -> if (bounds.x < currentBounds.x) currentBounds.x - bounds.x else Int.MAX_VALUE
                    HJNavDirection.Right -> if (bounds.x > currentBounds.x) bounds.x - currentBounds.x else Int.MAX_VALUE
                    HJNavDirection.Up -> if (bounds.y < currentBounds.y) currentBounds.y - bounds.y else Int.MAX_VALUE
                    HJNavDirection.Down -> if (bounds.y > currentBounds.y) bounds.y - currentBounds.y else Int.MAX_VALUE
                }
            }
                .thenBy { (_, bounds) -> bounds.x }
                .thenBy { (_, bounds) -> bounds.y }
        )?.first

    return if (result == currentWindow) null else result
}

private fun jumpViewImpl(cx: HJActionContext, direction: HJNavDirection) {
    val targetWindow = getWindowInDirection(cx, direction) ?: return

    targetWindow.setAsCurrentWindow(true)
    targetWindow.selectedComposite?.selectedEditor?.let { fileEditor ->
        (fileEditor as? TextEditor)?.editor?.contentComponent?.requestFocusInWindow()
    }
}

private fun swapViewImpl(cx: HJActionContext, direction: HJNavDirection) {
    val project = cx.hjEditor.editor.project!!
    val manager = FileEditorManagerEx.getInstanceEx(project)
    val current = manager.currentWindow?.selectedComposite?.component?.parent as? JComponent ?: return
    val target = getWindowInDirection(cx, direction)?.selectedComposite?.component?.parent as? JComponent ?: return

    val currentParent = current.parent
    val targetParent = target.parent

    if (!(currentParent is Splitter && targetParent is Splitter)) {
        HJActions.logger.error("Error in swapViewImpl: one of the parent components is not a splitter.")
        return
    }

    if (currentParent == targetParent) {
        // same splitter, just swap first and second components
        val first = currentParent.firstComponent
        val second = currentParent.secondComponent
        currentParent.firstComponent = null
        currentParent.secondComponent = null
        currentParent.firstComponent = second
        currentParent.secondComponent = first
        currentParent.revalidate()
        currentParent.repaint()

        current.requestFocusInWindow()
    } else {
        // different splitters, swap across
        val currentIsFirst = currentParent.firstComponent == current
        val targetIsFirst = targetParent.firstComponent == target

        // clear
        if (currentIsFirst) {
            currentParent.firstComponent = null
        } else {
            currentParent.secondComponent = null
        }
        if (targetIsFirst) {
            targetParent.firstComponent = null
        } else {
            targetParent.secondComponent = null
        }

        // set
        if (currentIsFirst) {
            currentParent.firstComponent = target
        } else {
            currentParent.secondComponent = target
        }
        if (targetIsFirst) {
            targetParent.firstComponent = current
        } else {
            targetParent.secondComponent = current
        }

        current.requestFocusInWindow()
    }
}

private fun transposeSplit(cx: HJActionContext) {
    val project = cx.hjEditor.editor.project!!
    val manager = FileEditorManagerEx.getInstanceEx(project)
    val current = manager.currentWindow?.selectedComposite?.component?.parent as? JComponent ?: return

    val currentParent = current.parent as? Splitter ?: return
    currentParent.orientation = !currentParent.orientation
    currentParent.revalidate()
    currentParent.repaint()
}

private fun rotateViewImpl(cx: HJActionContext, direction: HJDirection) {
    val project = cx.hjEditor.editor.project!!
    val manager = FileEditorManagerEx.getInstanceEx(project)
    val currentWindow = manager.currentWindow ?: return

    val currentIndex = manager.windows.indexOf(currentWindow)
    val targetIndex = (currentIndex + direction.value).mod(manager.windows.size)
    val targetWindow = manager.windows[targetIndex]

    targetWindow.setAsCurrentWindow(true)
    targetWindow.selectedComposite?.selectedEditor?.let { fileEditor ->
        (fileEditor as? TextEditor)?.editor?.contentComponent?.requestFocusInWindow()
    }
}

private fun splitBufferImpl(cx: HJActionContext, location: HJFileOpenLocation, openScratch: Boolean) {
    val file = if (openScratch) {
        val project = cx.hjEditor.editor.project!!
        ScratchRootType.getInstance().createScratchFile(
            project, "scratch", PlainTextLanguage.INSTANCE, "", ScratchFileService.Option.create_new_always
        )
    } else {
        cx.hjEditor.editor.virtualFile
    } ?: return

    openFile(cx, file, location)
}

private fun closeWindow(cx: HJActionContext) {
    quitWindows(cx) { manager ->
        for (window in manager.windows) {
            println(window)
        }

        manager.currentWindow?.let { listOf(it) } ?: emptyList()
    }
}

private fun closeOtherWindows(cx: HJActionContext) {
    quitWindows(cx) { manager ->
        val windows = manager.windows.toMutableList()
        manager.currentWindow?.let { windows.remove(it) }
        windows
    }
}

private fun registerHelpText(): List<Pair<String, String>> {
    val helpText = mutableListOf<Pair<HJRegister, String>>()
    val registers = HJState.registers.rawRegisters

    // order:
    // macro
    // last yank
    // any general purpose
    // last command
    // others
    registers[HJRegister.LastMacro]?.let { helpText.add(Pair(HJRegister.LastMacro, it.first())) }
    registers[HJRegister.LastYank]?.let { helpText.add(Pair(HJRegister.LastYank, it.first())) }
    helpText.addAll(
        registers
            .filter { (reg, _) -> reg is HJRegister.GeneralPurpose }
            .map { (reg, contents) -> Pair(reg, contents.first()) }
            .toList()
    )
    registers[HJRegister.LastCmd]?.let { helpText.add(Pair(HJRegister.LastCmd, it.first())) }

    helpText.addAll(
        listOf(
            Pair(HJRegister.Void, "<empty>"),
            Pair(HJRegister.SelIdx, "<selection indices>"),
            Pair(HJRegister.CurrSel, "<selection contents>"),
            Pair(HJRegister.FileName, "<document path>"),
            Pair(HJRegister.Clipboard, "<system clipboard>"),
        )
    )

    return helpText.map { (reg, cont) -> Pair(reg.toString(), cont) }
}

private fun selectRegister(cx: HJActionContext) {
    HJInputHandler.onNextKey { inputKey ->
        if (inputKey is HJInputKey.Character) {
            HJState.selectedRegister = HJRegister.fromChar(inputKey.char)
        }
    }

    HJState.getOrCreateHelpWidget(cx.hjEditor.editor)
        .setHelpContent("Select register", registerHelpText())
}

private fun insertRegister(cx: HJActionContext) {
    HJInputHandler.onNextKey { inputKey ->
        if (inputKey is HJInputKey.Character) {
            val reg = HJRegister.fromChar(inputKey.char)
            pasteImpl(cx, false, reg)
        }
    }

    HJState.getOrCreateHelpWidget(cx.hjEditor.editor)
        .setHelpContent("Insert register", registerHelpText())
}

private fun copyBetweenRegisters(cx: HJActionContext) {
    HJInputHandler.onNextKey { inputKey ->
        if (inputKey is HJInputKey.Character) {
            val sourceReg = HJRegister.fromChar(inputKey.char)

            val values = HJState.registers.read(sourceReg, cx.hjEditor)
            if (values == null) {
                cx.hjEditor.setError("Register $sourceReg is empty")
                return@onNextKey
            }

            HJInputHandler.onNextKey { inputKey ->
                if (inputKey is HJInputKey.Character) {
                    val destReg = HJRegister.fromChar(inputKey.char)

                    val nValues = values.size
                    if (HJState.registers.write(destReg, values, cx.hjEditor)) {
                        val plural = if (nValues == 1) "" else "s"
                        cx.hjEditor.setStatus("Yanked $nValues value$plural from register $sourceReg to $destReg")
                    }
                }
            }

            HJState.getOrCreateHelpWidget(cx.hjEditor.editor)
                .setHelpContent("Copy into register", registerHelpText())
        }
    }

    HJState.getOrCreateHelpWidget(cx.hjEditor.editor)
        .setHelpContent("Copy from register", registerHelpText())
}

private fun incrementImpl(cx: HJActionContext, direction: HJDirection) {
    val sign = direction.value
    var amount = sign * cx.count
    // If the register is '#'/ selIdx, then increase or decrease the 'amount' by 1 per element
    val increaseBy = if (cx.register is HJRegister.SelIdx) sign else 0

    val selection = cx.hjEditor.selection

    val newSelectionRanges = mutableListOf<HJRange>()
    var cumulativeLengthDiff = 0
    val changes = mutableListOf<HJChange>()

    for (range in selection.ranges) {
        val selectedText = range.slice(cx.doc) ?: continue
        val newFrom = range.from + cumulativeLengthDiff
        val incremented = listOf(::incrementInteger, ::incrementDatetime, ::incrementBool)
            .firstNotNullOfOrNull { it(selectedText, amount) }

        amount += increaseBy

        if (incremented == null) {
            val newRange = HJRange(newFrom, range.to + cumulativeLengthDiff)
            newSelectionRanges.add(newRange)
        } else {
            val newRange = HJRange(newFrom, newFrom + incremented.length)
            cumulativeLengthDiff += incremented.length - selectedText.length
            newSelectionRanges.add(newRange)
            changes.add(HJChange.Replace(range.from, range.to, incremented))
        }
    }

    if (changes.isNotEmpty()) {
        val newSelection = HJSelection(newSelectionRanges, selection.primaryIndex)
        val transaction = HJTransaction(changes, newSelection)
        cx.hjEditor.apply(transaction)
        exitSelectMode(cx)
    }
}

private fun recordMacro(cx: HJActionContext) {
    if (HJState.macroRecording != null) {
        val (reg, keys) = HJState.macroRecording!!
        // Remove the keypress which ends the recording
        keys.removeLast()
        val s = keys.joinToString(separator = "") { it.toString() }
        if (HJState.registers.write(reg, listOf(s), cx.hjEditor)) {
            cx.hjEditor.setStatus("Recorded to register [$reg]")
        }
    } else {
        val reg = cx.register ?: HJRegister.LastMacro
        HJState.macroRecording = Pair(reg, mutableListOf())
        cx.hjEditor.setStatus("Recording to register [$reg]")
    }
}

private fun replayMacro(cx: HJActionContext) {
    val reg = cx.register ?: HJRegister.LastMacro

    if (HJState.macroReplaying.contains(reg)) {
        cx.hjEditor.setError("Cannot replay from register [$reg] because already replaying from same register.")
        return
    }

    val registerString = HJState.registers.read(reg, cx.hjEditor)?.let { if (it.size == 1) it.first() else null }
    val keys = if (registerString != null) {
        HJInputKey.parseMacro(cx.hjEditor, registerString) ?: return
    } else {
        cx.hjEditor.setError("Register [$reg] empty")
        return
    }

    // Once the macro has been fully validated, it's marked as being under replay to ensure we don't fall into infinite recursion
    HJState.macroReplaying.add(reg)

    ApplicationManager.getApplication().invokeLater {
        repeat(cx.count) {
            for (key in keys) {
                HJInputHandler.sendInputKeyKeyEvent(cx.hjEditor, key)
            }
        }
    }

    HJState.macroReplaying.removeLast()
}

// TODO: does this consider all motions?
private fun repeatLastMotion(cx: HJActionContext) {
    val lastMotion = cx.hjEditor.lastMotion ?: return
    repeat(cx.count) {
        lastMotion.invoke(cx.hjEditor)
    }
}

private fun repeatLastInsert(cx: HJActionContext) {
    val lastInsertKeystrokes = mutableListOf<HJInputKey>()
    lastInsertKeystrokes.addAll(HJState.lastInsertKeystrokes)

    ApplicationManager.getApplication().invokeLater {
        insertMode(cx, false)

        repeat(cx.count) {
            for (key in lastInsertKeystrokes) {
                HJInputHandler.sendInputKeyKeyEvent(cx.hjEditor, key)
            }
        }

        switchToMode(cx, HJEditorMode.Normal)
        HJState.lastInsertKeystrokes.clear()
        HJState.lastInsertKeystrokes.addAll(lastInsertKeystrokes)
    }
}

private fun jumpForward(cx: HJActionContext) {
    HJState.jumpForward(cx.hjEditor, cx.count)
}

private fun jumpBackward(cx: HJActionContext) {
    HJState.jumpBackward(cx.hjEditor, cx.count)
}

private fun pushJump(hjEditor: HJEditor) {
    val jump = HJJump(hjEditor.editor.virtualFile, hjEditor.selection.clone())
    HJState.jumps.push(jump)
}

private fun saveSelection(cx: HJActionContext) {
    pushJump(cx.hjEditor)
    cx.hjEditor.setStatus("Selection saved to jumplist")
}

private fun jumplistPicker(cx: HJActionContext) {
    val project = cx.hjEditor.editor.project!!

    val data = HJState.jumps.jumps.reversed()

    val projectRoot = project.guessProjectDir()?.path ?: ""
    val model = HJTableModel(data, listOf("Path")) { jump, _ -> jump.file.path.removePrefix(projectRoot) }

    val previewSelector = previewSelector@{ rowIdx: Int ->
        val (file, selection) = data.getOrNull(rowIdx) ?: return@previewSelector null
        Pair(file, Pair(selection.primary.from, selection.primary.to))
    }

    HJPickerWithPreview(
        cx.hjEditor.editor, "Jumplist Picker", model, previewSelector
    ) { rowIdx ->
        val (file, selection) = data[rowIdx]

        openFile(cx, file, HJFileOpenLocation.InPlace, false)?.let { fileEditor ->
            val editor = (fileEditor as TextEditor).editor
            HJState.getHJEditor(editor)?.setNewSelection(selection)
        }
    }.show()
}

@Suppress("unused")
private fun lastPicker(cx: HJActionContext) {
    HJState.lastPicker?.show()
}