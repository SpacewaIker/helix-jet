package io.github.spacewaiker.helixjet.ui

import com.intellij.ide.IdeEventQueue
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.spacewaiker.helixjet.actions.HJDirection
import io.github.spacewaiker.helixjet.editor.HJState
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import javax.swing.AbstractAction
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.RowFilter
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

    fun addRow(row: T) {
        ApplicationManager.getApplication().invokeLater {
            val startIndex = data.size
            data.add(row)
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

        addMappings()

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
        addMappings()

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

    private var asyncTask: Future<*>? = null

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

                    HJState.getInstance(editor.project!!).lastPicker = lastPicker

                    asyncTask?.cancel(true)
                }
            })
        }
    }


    private fun selectFirstRow() {
        if (table.rowCount > 0)
            table.setRowSelectionInterval(0, 0)
    }

    private fun addMappings() {
        ApplicationManager.getApplication().invokeLater {
            val dispatcher = IdeEventQueue.EventDispatcher { event ->
                event is KeyEvent && event.id == KeyEvent.KEY_PRESSED && popup.isVisible && mappingsEventProcessor(event)?.let { action ->
                    action.actionPerformed(ActionEvent(popup, ActionEvent.ACTION_PERFORMED, action.toString()))
                    true
                } ?: false
            }
            val disposable = Disposer.newDisposable()
            IdeEventQueue.getInstance().addDispatcher(dispatcher, disposable)
            popup.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    Disposer.dispose(disposable)
                }
            })
        }
    }

    protected open fun mappingsEventProcessor(event: KeyEvent): AbstractAction? {
        return if (event.keyCode == KeyEvent.VK_ENTER) confirmAction
        else if (event.keyCode == KeyEvent.VK_UP || (event.keyCode == KeyEvent.VK_P && event.isControlDown))
            selectUpAction(1)
        else if (event.keyCode == KeyEvent.VK_DOWN || (event.keyCode == KeyEvent.VK_N && event.isControlDown))
            selectDownAction(1)
        else if (event.keyCode == KeyEvent.VK_PAGE_UP || (event.keyCode == KeyEvent.VK_U && event.isControlDown)) {
            val scrollAmount = (computeSize().height - 80) / 22
            selectUpAction(scrollAmount)
        } else if (event.keyCode == KeyEvent.VK_PAGE_DOWN || (event.keyCode == KeyEvent.VK_D && event.isControlDown)) {
            val scrollAmount = (computeSize().height - 80) / 22
            selectDownAction(scrollAmount)
        } else null
    }

    fun populateRowsAsync(f: () -> Unit): HJPicker {
        asyncTask = AppExecutorUtil.getAppExecutorService().submit(f)
        return this
    }

    fun show() {
        HJState.getInstance(editor.project!!).typedHandlerBlock = true
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

        ApplicationManager.getApplication().invokeLater {
            updatePreviewForRow(0, 0)
        }
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

    private var previewUpdateTask: Future<*>? = null
    private val previewUpdateGeneration = AtomicLong(0)

    init {
        table.selectionModel.addListSelectionListener { selectionEvent ->
            if (selectionEvent.valueIsAdjusting) return@addListSelectionListener
            val row = table.selectedRow.takeIf { it >= 0 } ?: return@addListSelectionListener
            val modelRow = table.convertRowIndexToModel(row)

            val generation = previewUpdateGeneration.incrementAndGet()
            previewUpdateTask?.cancel(true)
            previewUpdateTask = AppExecutorUtil.getAppExecutorService().submit {
                updatePreviewForRow(modelRow, generation)
            }
        }
    }

    override fun mappingsEventProcessor(event: KeyEvent): AbstractAction? {
        // @formatter:off
        return if ((event.keyCode == KeyEvent.VK_PAGE_UP && event.isShiftDown) ||
                   (event.keyCode == KeyEvent.VK_U && event.isControlDown && event.isShiftDown))
            scrollPreviewAction(HJDirection.Backward)
        else if ((event.keyCode == KeyEvent.VK_PAGE_DOWN && event.isShiftDown) ||
                 (event.keyCode == KeyEvent.VK_D && event.isControlDown && event.isShiftDown))
            scrollPreviewAction(HJDirection.Forward)
        else super.mappingsEventProcessor(event)
        // @formatter:on
    }

    private fun fileContents(file: VirtualFile): String {
        // remove ZWNBSP from start of string and windows newlines
        return String(file.contentsToByteArray()).trimStart('\uFEFF').replace("\r", "")
    }

    private fun updatePreviewForRow(rowIdx: Int, generation: Long) {
        if (rowIdx > table.model.rowCount - 1) return
        val (file, offsets) = previewSelector(rowIdx) ?: return
        if (previewEditor?.isDisposed ?: true) return

        val content = ApplicationManager.getApplication().runReadAction<String> {
            fileContents(file)
        }

        if (previewUpdateGeneration.get() != generation) return

        ApplicationManager.getApplication().invokeLater {
            if (previewUpdateGeneration.get() != generation) return@invokeLater

            if (previewEditor == null || previewEditor.isDisposed) return@invokeLater
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