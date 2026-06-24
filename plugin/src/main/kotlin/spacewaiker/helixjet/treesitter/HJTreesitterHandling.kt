package spacewaiker.helixjet.treesitter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import io.github.treesitter.ktreesitter.Node
import io.github.treesitter.ktreesitter.Point
import io.github.treesitter.ktreesitter.Query
import spacewaiker.helixjet.actions.HJDirection
import spacewaiker.helixjet.actions.HJTextObject
import spacewaiker.helixjet.editor.HJRange

val Point.logicalPosition: LogicalPosition
    get() = LogicalPosition(row.toInt(), column.toInt())

fun Node.getStartOffset(editor: Editor) = editor.logicalPositionToOffset(startPoint.logicalPosition)
fun Node.getEndOffset(editor: Editor) = editor.logicalPositionToOffset(endPoint.logicalPosition)

fun gotoTreesitterObject(
    editor: Editor,
    range: HJRange,
    objectName: String,
    dir: HJDirection,
    sliceTree: Node,
    syntax: HJLanguageSyntax,
    count: Int,
): HJRange {
    val query = syntax.query
    val getRange: (HJRange) -> HJRange? = getRange@{ range ->
        val pos = range.cursor

        val capName = { t: HJTextObject.TextObject -> "$objectName.$t" }
        val nodess = captureNodesAny(
            query,
            listOf(
                capName(HJTextObject.TextObject.Movement),
                capName(HJTextObject.TextObject.Around),
                capName(HJTextObject.TextObject.Inside),
            ),
            sliceTree
        )
        val nodes = nodess.toList()

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

private fun captureNodes(query: Query, captureName: String, node: Node): Sequence<Node> {
    return captureNodesAny(query, listOf(captureName), node)
}

private fun captureNodesAny(query: Query, captureNames: List<String>, node: Node): Sequence<Node> {
    return query(node).captures()
        .mapNotNull { (idx, match) ->
            val capture = match.captures[idx.toInt()]
            if (captureNames.contains(capture.name)) {
                capture.node
            } else null
        }
}