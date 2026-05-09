package spacewaiker.helixjet.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

class HJPicker(
    title: String,
    model: AbstractTableModel,
    tableModifier: ((JBTable) -> Unit)? = null,
    val callback: (Int) -> Unit,
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
    private val table: JBTable = JBTable(model).apply {
        setShowGrid(false)
        rowSorter = sorter

        addMappings(inputMap, actionMap)

        tableModifier?.invoke(this)
    }
    private val searchField = SearchTextField(false).apply {
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
    private val panel = JPanel(BorderLayout()).apply {
        add(searchField, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        preferredSize = Dimension(800, 600)
    }

    private val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, searchField)
        .setTitle(title)
        .setRequestFocus(true)
        .createPopup()

    init {
        selectFirstRow()
    }

    private fun selectFirstRow() {
        if (table.rowCount > 0)
            table.setRowSelectionInterval(0, 0)
    }

    private fun addMappings(inputMap: InputMap, actionMap: ActionMap) {
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

        // scroll up
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "scroll_up")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK), "scroll_up")
        actionMap.put("scroll_up", selectUpAction(20))

        // scroll down
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "scroll_down")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "scroll_down")
        actionMap.put("scroll_down", selectDownAction(20))
    }

    fun show(project: Project) {
        popup.showCenteredInCurrentWindow(project)
    }
}