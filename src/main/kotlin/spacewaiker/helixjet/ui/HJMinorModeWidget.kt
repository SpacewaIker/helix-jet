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
import kotlin.math.max

private const val MARGIN = 8
private const val MIN_WIDTH = 400
private const val MAX_WIDTH = 700

class HJMinorModeWidget(editor: Editor) {
    private val logger = Logger.getInstance(this::class.java)
    private val editorsComponent = (FileEditorManager.getInstance(editor.project!!) as FileEditorManagerEx).splitters
    var minorMode: HJKeyAction.HJMinorMode? = null

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

    fun setNewMinorMode(newMinorMode: HJKeyAction.HJMinorMode?) {
        minorMode = newMinorMode

        if (newMinorMode != null) {
            textArea.text = format(newMinorMode)
        }

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

        if (minorMode == null) {
            panel.isVisible = false
        } else {
            panel.isVisible = true
            val h = panelHeight()
            val w = panelWidth()
            val bottomInEditors = editorsComponent.height - MARGIN
            val rightInEditors = editorsComponent.width - MARGIN
            val origin = toRootCoordinates(rightInEditors - w, bottomInEditors - h)
            panel.setBounds(origin.x, origin.y, w, h)
        }

        layeredPane.revalidate()
        layeredPane.repaint()
    }

    private fun format(minorMode: HJKeyAction.HJMinorMode): String {
        val sb = StringBuilder()
        val maxCharsPerLine =
            MAX_WIDTH / textArea.getFontMetrics(textArea.font).charWidth('a') // font should be monospace

        // title
        sb.append(minorMode.name).append("\n\n")

        // keys
        var maxMappingsLength = 0
        // map list of keymaps to one string a, b, c and compute max length of those
        // map action to either doc or name
        val keys = minorMode.groupedMapping
            .filterNot { (action, _) -> action.name == "no_op" || action is HJKeyAction.HJPassthroughAction }
            .map { (action, keys) ->
                val keys = keys.joinToString(separator = ", ") { it.toString(false) }
                maxMappingsLength = max(maxMappingsLength, keys.length)
                val action = when (action) {
                    is HJKeyAction.HJMinorMode -> action.name
                    is HJKeyAction.HJAction -> action.doc
                    else -> "" // filtered out
                }
                Pair(keys, action)
            }
        maxMappingsLength += 2 // add 2 spaces of padding

        for ((keys, action) in keys) {
            // add keys and padding
            sb.append(keys)
            sb.repeat(" ", maxMappingsLength - keys.length)

            // add action text with necessary wrapping
            var action = action
            var addPadding = false
            while (action.isNotEmpty()) {
                if (addPadding) sb.append(" ".repeat(maxMappingsLength))
                addPadding = true
                sb.append(action.take(maxCharsPerLine - maxMappingsLength))
                sb.append("\n")
                action = action.drop(maxCharsPerLine - maxMappingsLength)
            }
        }

        // remove empty line at the end
        return sb.removeSuffix("\n").toString()
    }
}