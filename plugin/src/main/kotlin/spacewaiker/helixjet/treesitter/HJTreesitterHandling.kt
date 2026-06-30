package spacewaiker.helixjet.treesitter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.vfs.VirtualFile
import io.github.treesitter.ktreesitter.*
import spacewaiker.helixjet.actions.HJActionContext
import spacewaiker.helixjet.actions.HJDirection
import spacewaiker.helixjet.actions.HJTextObject
import spacewaiker.helixjet.editor.HJRange
import spacewaiker.helixjet.editor.HJSelection
import spacewaiker.helixjet.ui.HJPickerWithPreview
import javax.swing.table.AbstractTableModel

data class HJTag(
    val kind: String,
    val name: String,
    val container: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val endLine: Int,
    val file: VirtualFile,
)

val Point.logicalPosition: LogicalPosition
    get() = LogicalPosition(row.toInt(), column.toInt())

fun Node.getStartOffset(editor: Editor) = editor.logicalPositionToOffset(startPoint.logicalPosition)
fun Node.getEndOffset(editor: Editor) = editor.logicalPositionToOffset(endPoint.logicalPosition)

val LogicalPosition.point: Point
    get() = Point(line.toUInt(), column.toUInt())

fun offsetToPoint(editor: Editor, offset: Int) = editor.offsetToLogicalPosition(offset).point

fun TreeCursor.gotoParentWith(predicate: (Node) -> Boolean): Boolean {
    while (gotoParent()) {
        if (predicate(currentNode))
            return true
    }
    return false
}

fun TreeCursor.resetToPointRange(tree: Tree, start: Point, end: Point) {
    reset(tree.walk())

    while (true) {
        if (start < currentNode.startPoint || end > currentNode.endPoint) {
            gotoParent()
            break
        }
        if (gotoFirstChildForPoint(start) == null) break
    }
}

object HJTreesitterHandling {
    fun gotoTreesitterObject(
        editor: Editor,
        range: HJRange,
        objectName: String,
        dir: HJDirection,
        sliceTree: Node,
        syntax: HJLanguageSyntax,
        count: Int,
    ): HJRange {
        val query = syntax.textobjectQuery
        val getRange: (HJRange) -> HJRange? = getRange@{ range ->
            val pos = range.cursor

            val capName = { t: HJTextObject.TextObject -> "$objectName.$t" }
            val nodes = captureNodesAny(
                query,
                listOf(
                    capName(HJTextObject.TextObject.Movement),
                    capName(HJTextObject.TextObject.Around),
                    capName(HJTextObject.TextObject.Inside),
                ),
                sliceTree
            )

            val node = when (dir) {
                HJDirection.Forward -> nodes
                    .filter { it.getStartOffset(editor) > pos }
                    .minWithOrNull(compareBy<Node> { n -> n.startByte }.thenByDescending { n -> n.endByte })

                HJDirection.Backward -> nodes
                    .filter { it.getEndOffset(editor) < pos }
                    .maxWithOrNull(compareBy<Node> { n -> n.endByte }.thenByDescending { n -> n.startByte })
            } ?: return@getRange null

            val len = editor.document.textLength
            val startOffset = node.getStartOffset(editor)
            val endOffset = node.getEndOffset(editor)
            if (startOffset >= len || endOffset >= len) return@getRange null

            HJRange(startOffset, endOffset)
        }
        var lastRange = range
        repeat(count) {
            val r = getRange(lastRange)
            if (r != null) {
                if (r != lastRange) {
                    lastRange = r
                }
            } else return@repeat
        }
        return lastRange
    }

    fun moveParentNodeEnd(
        syntax: HJLanguageSyntax,
        editor: Editor,
        selection: HJSelection,
        direction: HJDirection,
        extend: Boolean,
    ): HJSelection {
        return selection.transform { range ->
            val startFromPoint = offsetToPoint(editor, range.from)
            val startToPoint = offsetToPoint(editor, range.to)

            var node = namedDescendantForPointRange(syntax, startFromPoint, startToPoint) ?: return@transform range

            var endHead = when (direction) {
                // moving forward, we always want to move one past the end of the current node, so use the end of the current
                // node, which is an exclusive end of the range
                HJDirection.Forward -> node.getEndOffset(editor)

                // moving backward, we want the cursor to land on the start char of the current node, or if it is already
                // at the start of a node, to traverse up to the parent
                HJDirection.Backward -> {
                    val endHead = node.getStartOffset(editor)

                    // if we're already at the beginning, look up to the parent
                    if (endHead == range.cursor) {
                        node = findParentStart(node) ?: node
                        node.getStartOffset(editor)
                    } else endHead
                }
            }

            if (extend) {
                // if we end up with a forward range, then adjust it to be one past where we want
                if (endHead >= range.anchor)
                    endHead++

                HJRange(range.anchor, endHead)
            } else {
                // preserve direction of original range
                HJRange(endHead, endHead + 1).withDirection(range.direction)
            }
        }
    }

    fun syntaxSymbolPicker(cx: HJActionContext) {
        val syntax = cx.hjEditor.syntax
        if (syntax == null) {
            cx.hjEditor.setError("TS syntax tree is not available on this buffer")
            return
        }

        val data = tagsIter(syntax, cx.hjEditor.editor).toList()

        val model = object : AbstractTableModel() {
            override fun getRowCount() = data.size
            override fun getColumnCount() = 3

            override fun getValueAt(rowIndex: Int, columnIndex: Int) = when (columnIndex) {
                0 -> data[rowIndex].kind
                1 -> data[rowIndex].name
                2 -> data[rowIndex].container
                else -> ""
            }

            override fun getColumnName(column: Int) = when (column) {
                0 -> "Kind"
                1 -> "Name"
                2 -> "Container"
                else -> ""
            }
        }

        val previewSelector = { rowIdx: Int ->
            val tag = data[rowIdx]
            Pair(tag.file, Pair(tag.startOffset, tag.endOffset))
        }

        HJPickerWithPreview(cx.hjEditor.editor, "Syntax Symbol Picker", model, previewSelector) callback@{ rowIdx ->
            val tag = data[rowIdx]
            cx.hjEditor.setNewSelection(HJSelection(HJRange(tag.startOffset, tag.endOffset)))
        }.show()
    }

    fun expandSelection(syntax: HJLanguageSyntax, editor: Editor, selection: HJSelection): HJSelection {
        return selectNodeImpl(syntax, editor, selection, null) { cursor ->
            val range = cursor.currentNode.range
            while (cursor.currentNode.range == range) {
                if (!cursor.gotoParent()) break
            }
        }
    }

    fun shrinkSelection(syntax: HJLanguageSyntax, editor: Editor, selection: HJSelection): HJSelection {
        return selectNodeImpl(syntax, editor, selection, null) { it.gotoFirstChild() }
    }

    fun selectNextSibling(syntax: HJLanguageSyntax, editor: Editor, selection: HJSelection): HJSelection {
        return selectNodeImpl(syntax, editor, selection, HJDirection.Forward) { cursor ->
            while (!cursor.gotoNextSibling()) {
                if (!cursor.gotoParent()) break
            }
        }
    }

    fun selectAllSiblings(syntax: HJLanguageSyntax, editor: Editor, selection: HJSelection): HJSelection {
        val cursor = syntax.tree.walk()
        return selection.transformIter { range ->
            val fromPoint = offsetToPoint(editor, range.from)
            val toPoint = offsetToPoint(editor, range.to)

            cursor.resetToPointRange(syntax.tree, fromPoint, toPoint)

            if (!cursor.gotoParentWith { it.childCount > 1u }) {
                return@transformIter listOf(range)
            }

            selectChildren(cursor, editor, range)
        }
    }

    fun selectAllChildren(syntax: HJLanguageSyntax, editor: Editor, selection: HJSelection): HJSelection {
        val cursor = syntax.tree.walk()
        return selection.transformIter { range ->
            val fromPoint = offsetToPoint(editor, range.from)
            val toPoint = offsetToPoint(editor, range.to)

            cursor.resetToPointRange(syntax.tree, fromPoint, toPoint)

            selectChildren(cursor, editor, range)
        }
    }

    fun selectPrevSibling(syntax: HJLanguageSyntax, editor: Editor, selection: HJSelection): HJSelection {
        return selectNodeImpl(syntax, editor, selection, HJDirection.Forward) { cursor ->
            while (!cursor.gotoPreviousSibling()) {
                if (!cursor.gotoParent()) break
            }
        }
    }

    fun captureNodes(query: Query, captureName: String, node: Node): Sequence<Node> {
        return captureNodesAny(query, listOf(captureName), node)
    }

    fun captureNodesAny(query: Query, captureNames: List<String>, node: Node): Sequence<Node> {
        return query(node).captures()
            .mapNotNull { (idx, match) ->
                val capture = match.captures[idx.toInt()]
                if (captureNames.contains(capture.name)) {
                    capture.node
                } else null
            }
    }

    private fun namedDescendantForPointRange(syntax: HJLanguageSyntax, start: Point, end: Point): Node? {
        return syntax.tree.rootNode.namedDescendant(start, end)
    }

    private fun findParentStart(node: Node): Node? {
        val start = node.startByte
        var node = node

        while (node.startByte >= start || !node.isNamed) {
            node = node.parent ?: return null
        }

        return node
    }

    private fun tagsIter(syntax: HJLanguageSyntax, editor: Editor): Sequence<HJTag> {
        return syntax.tagQuery(syntax.tree.rootNode).captures()
            .map { (idx, match) ->
                val capture = match.captures[idx.toInt()]

                val kind = capture.name.removePrefix("definition.")

                val startOffset = capture.node.getStartOffset(editor)
                val endOffset = capture.node.getEndOffset(editor)
                val name = editor.document.text.substring(startOffset, endOffset)

                HJTag(
                    kind,
                    name,
                    "",
                    startOffset,
                    endOffset,
                    editor.document.getLineNumber(startOffset),
                    editor.document.getLineNumber(endOffset),
                    editor.virtualFile,
                )
            }
    }

    private fun selectNodeImpl(
        syntax: HJLanguageSyntax,
        editor: Editor,
        selection: HJSelection,
        direction: HJDirection?,
        motion: (TreeCursor) -> Unit,
    ): HJSelection {
        val cursor = syntax.tree.walk()

        return selection.transform { range ->
            val fromPoint = offsetToPoint(editor, range.from)
            val toPoint = offsetToPoint(editor, range.to)

            cursor.resetToPointRange(syntax.tree, fromPoint, toPoint)

            motion(cursor)

            val node = cursor.currentNode
            val from = node.getStartOffset(editor)
            val to = node.getEndOffset(editor)

            HJRange(to, from).withDirection(direction ?: range.direction)
        }
    }

    private fun selectChildren(cursor: TreeCursor, editor: Editor, range: HJRange): List<HJRange> {
        val children = cursor.currentNode.children
            .filter(Node::isNamed)
            .map { HJRange.fromNode(it, editor, range.direction) }

        return children.ifEmpty { listOf(range) }
    }
}

