package spacewaiker.helixjet.editor

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.lang.tree.util.children
import com.intellij.lang.tree.util.parents
import com.intellij.lang.tree.util.siblings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Get the smallest node within this node that spans the given range.
 */
fun ASTNode.descendantForRange(start: Int, end: Int): ASTNode? {
    if (startOffset > start || startOffset + textLength - 1 < end) return null

    for (child in getChildren(null)) {
        val result = child.descendantForRange(start, end)
        if (result != null) return result
    }

    return this
}

/**
 * Get the smallest named node within this node that spans the given range.
 */
fun ASTNode.namedDescendantForRange(start: Int, end: Int): ASTNode? {
    if (!isNamed || startOffset > start || startOffset + textLength < end) return null

    for (child in getChildren(null)) {
        val result = child.namedDescendantForRange(start, end)
        if (result != null) return result
    }

    return this
}

/**
 * Get the first descendant that matches the predicate
 */
fun ASTNode.findDescendant(predicate: (ASTNode) -> Boolean): ASTNode? {
    val queue = ArrayDeque<ASTNode>()
    queue.add(this)

    while (!queue.isEmpty()) {
        val node = queue.removeFirst()
        if (predicate(node)) return node
        queue.addAll(node.children())
    }
    return null
}

val ASTNode.isNamed get() = firstChildNode != null

val ASTNode.nextSibling get() = siblings(true, false).firstOrNull()

val ASTNode.prevSibling get() = siblings(false, false).firstOrNull()

val ASTNode.parent get() = parents(false).firstOrNull()

val ASTNode.endOffset get() = startOffset + textLength

data class HJSymbolData(
    val psiElement: PsiElement,
    val kind: String,
    val name: String,
    val container: String,
)

fun getStructureElements(project: Project, psiFile: PsiFile): List<HJSymbolData> {
    return ApplicationManager.getApplication().runReadAction<List<HJSymbolData>> {
        val builder =
            LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile)
                ?: return@runReadAction emptyList()
        val structureView = builder.createStructureView(null, project)
        val results = mutableListOf<HJSymbolData>()

        val stack = mutableListOf<Pair<TreeElement, TreeElement?>>(Pair(structureView.treeModel.root, null))
        while (stack.isNotEmpty()) {
            val (node, parent) = stack.removeLast()
            if (node is StructureViewTreeElement) {
                val psiElement = node.value as? PsiElement ?: continue
                results.add(
                    HJSymbolData(
                        psiElement,
                        psiElement.node.elementType.toString().lowercase().replaceFirstChar { it.uppercase() },
                        node.presentation.presentableText ?: "",
                        parent?.presentation?.presentableText ?: "",
                    )
                )
            }
            node.children.forEach { stack.add(Pair(it, node)) }
        }

        Disposer.dispose(structureView)

        results
    }
}