package spacewaiker.helixjet.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import spacewaiker.helixjet.actions.HJCommand
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.*

private const val MARGIN = 8
private const val WIDTH = 700
private const val PROMPT_HEIGHT = 38

class HJCommandPopup(val editor: Editor) {
    private val logger = Logger.getInstance(this::class.java)
    var command: HJCommand? = null
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

    fun setNewCommand(newCommand: HJCommand?) {
        command = newCommand

        if (newCommand != null) {
            textArea.text = format(newCommand)
        }

        recomputeElement()
    }

    private fun panelHeight(): Int {
        // calculate height based on text content
        val lineCount = textArea.text.lines().size
        val lineHeight = textArea.getFontMetrics(textArea.font).height
        return lineCount * lineHeight + 16
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

        if (command == null) {
            panel.isVisible = false
        } else {
            panel.isVisible = true
            val h = panelHeight()
            val bottomInEditors = editorsComponent.height - PROMPT_HEIGHT - MARGIN
            val origin = toRootCoordinates(MARGIN, bottomInEditors - h)
            panel.setBounds(origin.x, origin.y, WIDTH, h)
        }

        layeredPane.revalidate()
        layeredPane.repaint()
    }

    private fun format(command: HJCommand): String {
        val sb = StringBuilder()

        // description
        sb.append(command.doc)

        // aliases
        if (command.aliases.isNotEmpty()) {
            sb.append("\nAliases: ")
            sb.append(command.aliases.joinToString(separator = ", "))
        }

        return sb.toString()
    }
}