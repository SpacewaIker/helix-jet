package spacewaiker.helixjet.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import spacewaiker.helixjet.actions.HJDirection
import spacewaiker.helixjet.editor.HJState
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import kotlin.math.max

private val DefaultSize = Dimension(800, 600)
private const val WindowPadding = 200

open class HJTableModel<T>(
    data: List<T>,
    val columns: List<String>,
    private val selector: (T, Int) -> String,
) : AbstractTableModel() {
    protected val data = data.toMutableList()
    val tableData
        get() = this.data.toList()

    override fun getRowCount() = data.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(column: Int) = columns[column]
    override fun getValueAt(rowIndex: Int, columnIndex: Int) = selector(data[rowIndex], columnIndex)
}

class HJStreamingTableModel<T>(
    columns: List<String>,
    selector: (T, Int) -> String,
) : HJTableModel<T>(emptyList(), columns, selector) {
    fun addRows(rows: List<T>) {
        if (rows.isEmpty()) return
        ApplicationManager.getApplication().invokeLater {
            val startIndex = data.size
            data.addAll(rows)
            fireTableRowsInserted(startIndex, data.size - 1)
        }
    }
}

open class HJPicker(
    protected val editor: Editor,
    private val title: String,
    private val model: AbstractTableModel,
    private val columnColors: List<Pair<Int, (String) -> JBColor?>> = listOf(),
    private val callback: (Int) -> Unit,
) {
    private val confirmAction = object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            val row = table.selectedRow.takeIf { it >= 0 } ?: return
            val modelRow = table.convertRowIndexToModel(row)
            popup.cancel()
            callback(modelRow)
        }
    }
    private val selectUpAction = { count: Int ->
        object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val next = (table.selectedRow - count).coerceAtLeast(0)
                table.setRowSelectionInterval(next, next)
                table.scrollRectToVisible(table.getCellRect(next, 0, true))
            }
        }
    }
    private val selectDownAction = { count: Int ->
        object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val next = (table.selectedRow + count).coerceAtMost(table.rowCount - 1)
                table.setRowSelectionInterval(next, next)
                table.scrollRectToVisible(table.getCellRect(next, 0, true))
            }
        }
    }

    private val sorter = TableRowSorter(model)
    protected val table: JBTable = JBTable(model).apply {
        setShowGrid(false)
        rowSorter = sorter

        addMappings(inputMap, actionMap)

        for ((colIdx, colorer) in columnColors) {
            val renderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
                ): Component? {
                    val baseComp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (!isSelected) {
                        value?.toString()?.let { foreground = colorer(it) ?: table.foreground }
                    }
                    return baseComp
                }
            }
            columnModel.getColumn(colIdx).cellRenderer = renderer
        }
    }
    private val tableScrollPane = JBScrollPane(table).apply {
        font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
    }
    protected val searchField = SearchTextField(false).apply {
        addMappings(textEditor.getInputMap(JComponent.WHEN_FOCUSED), textEditor.actionMap)

        addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(p0: DocumentEvent) {
                val query = text.trim()
                if (query.isEmpty()) {
                    sorter.rowFilter = null
                    return
                }

                val words = query.split(Regex("\\s+"))
                val cols =
                    words.filter { it.startsWith("%") }.map { NameUtil.buildMatcher("*${it.trimStart('%')}*").build() }
                val pattern = words.filterNot { it.startsWith("%") }.joinToString("*")
                val matcher = NameUtil.buildMatcher("*$pattern*").build()
                sorter.rowFilter = object : RowFilter<AbstractTableModel, Int>() {
                    override fun include(entry: Entry<out AbstractTableModel, out Int>): Boolean {
                        val combinedString = (0 until entry.valueCount)
                            .filter { colIdx -> cols.isEmpty() || cols.any { it.matches(model.getColumnName(colIdx)) } }
                            .joinToString(separator = " ") { colIdx -> entry.getValue(colIdx).toString() }

                        return matcher.matches(combinedString)
                    }
                }

                selectFirstRow()
            }
        })
    }
    protected val panel = JPanel(BorderLayout()).apply {
        add(searchField, BorderLayout.NORTH)
        add(tableScrollPane, BorderLayout.CENTER)
        preferredSize = computeSize()
    }

    protected open val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, searchField)
        .setTitle(title)
        .setRequestFocus(true)
        .createPopup()

    init {
        selectFirstRow()

        ApplicationManager.getApplication().invokeLater {
            val thisPicker = this
            popup.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    val lastPicker = if (thisPicker is HJPickerWithPreview)
                        HJPickerWithPreview(editor, title, model, previewSelector, columnColors, callback)
                    else HJPicker(editor, title, model, columnColors, callback)

                    val row = thisPicker.table.selectedRow
                    if (row >= 0) {
                        lastPicker.table.setRowSelectionInterval(row, row)
                        lastPicker.table.scrollRectToVisible(table.getCellRect(row, 0, true))
                    }

                    HJState.lastPicker = lastPicker
                }
            })
        }
    }


    private fun selectFirstRow() {
        if (table.rowCount > 0)
            table.setRowSelectionInterval(0, 0)
    }

    protected open fun addMappings(inputMap: InputMap, actionMap: ActionMap) {
        // confirm
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "confirm")
        actionMap.put("confirm", confirmAction)

        // select up
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "select_up")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK), "select_up")
        actionMap.put("select_up", selectUpAction(1))

        // select down
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "select_down")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "select_down")
        actionMap.put("select_down", selectDownAction(1))

        // scroll
        val scrollAmount = (computeSize().height - 80) / 22

        // scroll up
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "scroll_up")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK), "scroll_up")
        actionMap.put("scroll_up", selectUpAction(scrollAmount))

        // scroll down
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "scroll_down")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "scroll_down")
        actionMap.put("scroll_down", selectDownAction(scrollAmount))
    }

    fun show() {
        popup.showCenteredInCurrentWindow(editor.project!!)

        resizeColumnsToFit(table, table.model)
    }

    protected fun computeSize() = WindowManager.getInstance().getIdeFrame(editor.project!!)?.component?.size?.let {
        Dimension(it.width - 2 * WindowPadding, it.height - 2 * WindowPadding)
    } ?: DefaultSize
}

class HJPickerWithPreview(
    editor: Editor,
    title: String,
    model: AbstractTableModel,
    val previewSelector: (Int) -> Pair<VirtualFile, Pair<Int, Int>?>?,
    columnColors: List<Pair<Int, (String) -> JBColor?>> = listOf(),
    callback: (Int) -> Unit,
) : HJPicker(editor, title, model, columnColors, callback) {
    private val scrollPreviewAction = { direction: HJDirection ->
        object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val scrollAmount = previewEditor.scrollingModel.visibleArea.height
                val newOffset = previewEditor.scrollingModel.verticalScrollOffset + scrollAmount * direction.value
                previewEditor.scrollingModel.scrollVertically(newOffset)
            }
        }
    }

    private val previewDocument = EditorFactory.getInstance().createDocument("")
    private val previewEditor = EditorFactory.getInstance().createEditor(previewDocument, editor.project!!).apply {
        settings.apply {
            isLineNumbersShown = true
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
            isRightMarginShown = false
        }

        updatePreviewForRow(0)
    }

    private val splitPane = OnePixelSplitter(false, 0.5f).apply {
        firstComponent = panel
        secondComponent = previewEditor.component
        isFocusCycleRoot = false
    }

    private val panePanel = JPanel(BorderLayout()).apply {
        add(splitPane, BorderLayout.CENTER)
        preferredSize = computeSize()
    }

    override val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panePanel, searchField)
        .setTitle(title)
        .setRequestFocus(true)
        .createPopup()
        .apply {
            addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    EditorFactory.getInstance().releaseEditor(previewEditor)
                }
            })
        }

    init {
        table.selectionModel.addListSelectionListener { selectionEvent ->
            if (selectionEvent.valueIsAdjusting) return@addListSelectionListener
            val row = table.selectedRow.takeIf { it >= 0 } ?: return@addListSelectionListener
            val modelRow = table.convertRowIndexToModel(row)
            updatePreviewForRow(modelRow)
        }
    }

    override fun addMappings(inputMap: InputMap, actionMap: ActionMap) {
        super.addMappings(inputMap, actionMap)

        ApplicationManager.getApplication().invokeLater {
            val ctrlShiftDownMask = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK
            // scroll up preview
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.SHIFT_DOWN_MASK), "scroll_preview_up")
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, ctrlShiftDownMask), "scroll_preview_up")
            actionMap.put("scroll_preview_up", scrollPreviewAction(HJDirection.Backward))

            // scroll down preview
            inputMap.put(
                KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.SHIFT_DOWN_MASK),
                "scroll_preview_down"
            )
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, ctrlShiftDownMask), "scroll_preview_down")
            actionMap.put("scroll_preview_down", scrollPreviewAction(HJDirection.Forward))
        }
    }

    private fun fileContents(file: VirtualFile): String {
        // remove ZWNBSP from start of string and windows newlines
        return String(file.contentsToByteArray()).trimStart('\uFEFF').replace("\r", "")
    }

    private fun updatePreviewForRow(rowIdx: Int) {
        if (rowIdx > table.model.rowCount - 1) return
        val (file, offsets) = previewSelector(rowIdx) ?: return

        val content = ApplicationManager.getApplication().runReadAction<String> {
            fileContents(file)
        }

        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                previewDocument.setText(content)
            }

            // set syntax highlighting / file type
            val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(editor.project!!, file)
            (previewEditor as? EditorEx)?.highlighter = highlighter

            // set scroll
            val scrollOffset = offsets?.first ?: 0
            previewEditor.scrollingModel.scrollTo(
                previewEditor.offsetToLogicalPosition(scrollOffset),
                ScrollType.CENTER
            )

            // set highlight / selection
            if (offsets != null) {
                previewEditor.selectionModel.setSelection(offsets.first, offsets.second)
            }
        }
    }
}

private const val ColumnPadding = 8

private fun resizeColumnsToFit(table: JBTable, model: TableModel) {
    val columnModel = table.columnModel
    val columnWidths = IntArray(model.columnCount)
    val tableWidth = table.width.takeIf { it > 0 } ?: return
    val maxColumnWidth = (tableWidth * 0.4).toInt()  // no column takes more than 40% of table width

    for (col in 0 until model.columnCount) {
        val headerRenderer = table.tableHeader?.defaultRenderer
        val headerComponent = headerRenderer?.getTableCellRendererComponent(
            table, columnModel.getColumn(col).headerValue, false, false, -1, col
        )
        var maxWidth = headerComponent?.preferredSize?.width ?: 0

        for (row in 0 until table.rowCount) {
            val renderer = table.getCellRenderer(row, col)
            val component = table.prepareRenderer(renderer, row, col)
            maxWidth = max(maxWidth, component.preferredSize.width)
        }

        columnWidths[col] = (maxWidth + ColumnPadding).coerceAtMost(maxColumnWidth)
    }

    val totalContentWidth = columnWidths.sum()

    if (totalContentWidth < tableWidth) {
        // distribute remaining space proportionally to capped columns
        val remaining = tableWidth - totalContentWidth
        val cappedColumns = columnWidths.indices.filter { columnWidths[it] == maxColumnWidth }
        val expandTargets = cappedColumns.ifEmpty { columnWidths.indices.toList() }
        val extraPerColumn = remaining / expandTargets.size

        for (col in 0 until model.columnCount) {
            val extra = if (col in expandTargets) extraPerColumn else 0
            columnModel.getColumn(col).minWidth = 0
            columnModel.getColumn(col).preferredWidth = columnWidths[col] + extra
        }
    } else {
        for (col in 0 until model.columnCount) {
            columnModel.getColumn(col).minWidth = 0
            columnModel.getColumn(col).preferredWidth = columnWidths[col]
        }
    }

    table.doLayout()
}