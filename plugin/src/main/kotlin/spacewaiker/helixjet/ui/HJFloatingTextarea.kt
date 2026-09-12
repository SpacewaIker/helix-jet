package spacewaiker.helixjet.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.rml.dfa.Quadruple
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

abstract class HJFloatingTextarea(editor: Editor) {
    enum class HAlignment {
        Left, Right,
    }

    protected var width: Pair<Int, Int?>? = Pair(400, 700)
    protected var hAlignment: HAlignment = HAlignment.Left
    protected var bottomSpacing: Int = 0
    protected var margin: Int = 8

    private val logger = Logger.getInstance(javaClass)
    private val editorsComponent = (FileEditorManager.getInstance(editor.project!!) as FileEditorManagerEx).splitters

    private val minWidth get() = width?.first ?: 0
    private val maxWidth get() = width?.second ?: editorsComponent.width

    init {
        editorsComponent.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                recomputeElement()
            }

            override fun componentMoved(e: ComponentEvent?) {
                recomputeElement()
            }
        })
    }

    protected val textArea = JTextArea().apply {
        font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
        isOpaque = true
        lineWrap = true
        wrapStyleWord = true
    }

    // for some reason, maxAdvance doesn't seem to include the space between chars... So add 1 as an estimate
    private val charWidth = textArea.getFontMetrics(textArea.font).maxAdvance + 1

    protected val panel = JPanel(BorderLayout()).apply {
        add(textArea, BorderLayout.CENTER)
        border = BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground"))
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        isOpaque = true
    }

    private fun panelHeight(): Int {
        if (textArea.text.isEmpty()) return 0
        
        // calculate height based on text content
        val lineCount = textArea.text.lines().size
        val lineHeight = textArea.getFontMetrics(textArea.font).height
        return lineCount * lineHeight + 16
    }

    private fun panelWidth(lineMaxChars: Int): Int {
        if (width == null) return editorsComponent.width

        return (lineMaxChars * charWidth).coerceIn(minWidth, maxWidth) - 10
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

    /**
     * Format text
     *
     * @param maxChars The maximum number of chars that a line can be
     * @return The formatted text
     */
    abstract fun formatText(maxChars: Int): String

    /**
     * Recomputes the size and location of the element.
     *
     * @return The calculated bounds (x, y, w, h)
     */
    protected fun recomputeElement(): Quadruple<Int, Int, Int, Int>? {
        val layeredPane = getRootLayeredPane() ?: return null

        textArea.text = formatText(maxWidth / charWidth)
        val lineMaxChars = textArea.text.lines().maxOf { it.length }

        val h = panelHeight()
        val w = panelWidth(lineMaxChars)

        val bottomInEditors = editorsComponent.height - margin - bottomSpacing
        val x = if (hAlignment == HAlignment.Left) margin else editorsComponent.width - margin - w
        val origin = toRootCoordinates(x, bottomInEditors - h)
        panel.setBounds(origin.x, origin.y, w, h)

        layeredPane.revalidate()
        layeredPane.repaint()

        return Quadruple(origin.x, origin.y, w, h)
    }
}