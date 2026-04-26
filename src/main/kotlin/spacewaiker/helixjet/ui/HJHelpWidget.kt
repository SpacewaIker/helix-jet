package spacewaiker.helixjet.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import spacewaiker.helixjet.actions.HJKeyAction
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.*

private const val MARGIN = 8
private const val MIN_WIDTH = 400
private const val MAX_WIDTH = 700

class HJHelpWidget(editor: Editor) {
    private val logger = Logger.getInstance(javaClass)
    private val editorsComponent = (FileEditorManager.getInstance(editor.project!!) as FileEditorManagerEx).splitters

    init {
        editorsComponent.addComponentListener(object : ComponentListener {
            override fun componentResized(e: ComponentEvent?) {
                recomputeElement()
            }

            override fun componentMoved(e: ComponentEvent?) {
                recomputeElement()
            }

            override fun componentShown(e: ComponentEvent?) {
                logger.error("componentShown not implemented. If encountered, please report!")
            }

            override fun componentHidden(e: ComponentEvent?) {
                logger.error("componentHidden not implemented. If encountered, please report!")
            }
        })
    }

    private val textArea = JTextArea().apply {
        font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
        isOpaque = true
        lineWrap = true
        wrapStyleWord = true
    }

    private val panel = JPanel(BorderLayout()).apply {
        add(textArea, BorderLayout.CENTER)
        border = BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground"))
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        isOpaque = true
    }

    fun setNewMinorMode(newMinorMode: HJKeyAction.HJMinorMode) {
        val lines = formatMinorMode(newMinorMode)
        setHelpContent(newMinorMode.name, lines)

    }

    fun setHelpContent(title: String, lines: List<Pair<String, String>>) {
        textArea.text = formatHelpContent(title, lines)
        recomputeElement()
    }

    private fun panelHeight(): Int {
        // calculate height based on text content
        val lineCount = textArea.text.lines().size
        val lineHeight = textArea.getFontMetrics(textArea.font).height
        return lineCount * lineHeight + 16
    }

    private fun panelWidth(): Int {
        val numChars = textArea.text.lines().maxOf { it.length }
        // for some reason, maxAdvance doesn't seem to include the space between chars... So add 1 as an estimate
        val charWidth = textArea.getFontMetrics(textArea.font).maxAdvance + 1
        return (numChars * charWidth).coerceIn(MIN_WIDTH, MAX_WIDTH) - 10
    }

    private fun getRootLayeredPane(): JLayeredPane? {
        return SwingUtilities.getRootPane(editorsComponent)?.layeredPane
    }

    private fun toRootCoordinates(x: Int, y: Int): Point {
        return SwingUtilities.convertPoint(editorsComponent, x, y, getRootLayeredPane())
    }

    fun show() {
        val layeredPane = getRootLayeredPane() ?: return
        layeredPane.add(panel, JLayeredPane.POPUP_LAYER as Any)
        layeredPane.revalidate()
        layeredPane.repaint()
    }

    fun hide() {
        val layeredPane = getRootLayeredPane() ?: return
        layeredPane.remove(panel)
        layeredPane.revalidate()
        layeredPane.repaint()
    }

    private fun recomputeElement() {
        val layeredPane = getRootLayeredPane() ?: return

        val h = panelHeight()
        val w = panelWidth()
        val bottomInEditors = editorsComponent.height - MARGIN
        val rightInEditors = editorsComponent.width - MARGIN
        val origin = toRootCoordinates(rightInEditors - w, bottomInEditors - h)
        panel.setBounds(origin.x, origin.y, w, h)

        layeredPane.revalidate()
        layeredPane.repaint()
    }

    private fun formatMinorMode(minorMode: HJKeyAction.HJMinorMode): List<Pair<String, String>> {
        return minorMode.groupedMapping
            .filterNot { (action, _) -> action.name == "no_op" || action is HJKeyAction.HJPassthroughAction }
            .map { (action, keys) ->
                val keys = keys.joinToString(separator = ", ") { it.toString(false) }
                val action = when (action) {
                    is HJKeyAction.HJMinorMode -> action.name
                    is HJKeyAction.HJAction -> action.doc
                    else -> "" // filtered out
                }
                Pair(keys, action)
            }
    }

    private fun formatHelpContent(title: String, lines: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        val maxCharsPerLine =
            MAX_WIDTH / textArea.getFontMetrics(textArea.font).charWidth('a') // font should be monospace

        // title
        sb.append(title).append("\n\n")

        // lines
        // compute max length of first column to compute padding length
        val totalPadding = (lines.maxOfOrNull { it.first.length } ?: 0) + 2
        for ((keys, line) in lines) {
            // add keys and padding
            sb.append(keys)
            sb.repeat(" ", totalPadding - keys.length)

            // add line with necessary wrapping
            var line = line
            var addPadding = false
            while (line.isNotEmpty()) {
                if (addPadding) sb.repeat(" ", totalPadding)
                addPadding = true
                sb.appendLine(line.take(maxCharsPerLine - totalPadding))
                line = line.drop(maxCharsPerLine - totalPadding)
            }
        }

        // remove empty line at the end
        return sb.removeSuffix("\n").toString()
    }
}