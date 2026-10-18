package io.github.spacewaiker.helixjet.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorFontType
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

class HJJumpLabelOverlay(private val editor: Editor) : JComponent() {
    private val labels = mutableListOf<Pair<Int, String>>()

    init {
        isOpaque = false
        setBounds(0, 0, editor.contentComponent.width, editor.contentComponent.height)

        editor.contentComponent.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                setBounds(0, 0, editor.contentComponent.width, editor.contentComponent.height)
            }
        })
    }

    fun setLabels(newLabels: List<Pair<Int, String>>) {
        labels.clear()
        labels.addAll(newLabels)
        repaint()
    }

    override fun paintComponent(g: Graphics?) {
        val g2 = g as? Graphics2D ?: return
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val labelFont = editor.colorsScheme.getFont(EditorFontType.BOLD)
        val textFont = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        val labelFontMetrics = g2.getFontMetrics(labelFont)
        val textFontMetrics = g2.getFontMetrics(textFont)
        g2.font = labelFont

        for ((offset, label) in labels) {
            if (offset < 0 || offset >= editor.document.textLength) continue

            val point = editor.offsetToXY(offset)

            val x = point.x
            val y = point.y

            if (y !in 0..height) continue

            g2.color = editor.colorsScheme.defaultBackground
            g2.fillRect(x, y, labelFontMetrics.stringWidth(label), editor.lineHeight)
            g2.color = editor.colorsScheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES).foregroundColor
            // removing 2 additional pixels from y seems to align better
            g2.drawString(label, x, y + editor.lineHeight - textFontMetrics.descent - 2)
        }
    }
}