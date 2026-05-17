package spacewaiker.helixjet.editor

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.structureView.StructureView
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.tree.util.children
import com.intellij.lang.tree.util.parents
import com.intellij.lang.tree.util.siblings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil

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

fun getStructureElements(project: Project, psiFile: PsiFile, onComplete: (List<HJSymbolData>) -> Unit) {
    val readAction = readAction@{
        val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile)
            ?: return@readAction Pair(null, emptyList<HJSymbolData>())
        val structureView = builder.createStructureView(null, project)
        val results = mutableListOf<HJSymbolData>()

        val stack = mutableListOf<Pair<TreeElement, TreeElement?>>(Pair(structureView.treeModel.root, null))
        while (stack.isNotEmpty()) {
            val (node, parent) = stack.removeLast()
            if (node is StructureViewTreeElement && parent != null) {
                val psiElement = node.value as? PsiElement ?: continue
                results.add(
                    HJSymbolData(
                        psiElement,
                        psiElement.node.elementType.toString().lowercase().replaceFirstChar { it.uppercase() },
                        node.presentation.presentableText ?: "",
                        parent.presentation.presentableText ?: "",
                    )
                )
            }
            node.children.forEach { stack.add(Pair(it, node)) }
        }

        Pair(structureView, results)
    }

    AppExecutorUtil.getAppExecutorService().submit {
        val (structureView, results) =
            ApplicationManager.getApplication().runReadAction<Pair<StructureView?, List<HJSymbolData>>>(readAction)

        ApplicationManager.getApplication().invokeLater {
            structureView?.let { Disposer.dispose(it) }
            onComplete(results)
        }
    }
}

fun getDiagnostics(editor: Editor): List<HighlightInfo> {
    val project = editor.project ?: return emptyList()

    return ApplicationManager.getApplication().runReadAction<List<HighlightInfo>> {
        val results = mutableListOf<HighlightInfo>()

        DaemonCodeAnalyzerEx.processHighlights(
            editor.document,
            project,
            null,
            0,
            editor.document.textLength,
        ) { info ->
            if (info.severity in listOf(
                    HighlightSeverity.ERROR, HighlightSeverity.WARNING, HighlightSeverity.WEAK_WARNING,
                    HighlightSeverity.INFORMATION, HighlightSeverity.TEXT_ATTRIBUTES
                ) || info.severity.name == "TYPO"
            ) {
                results.add(info)
            }

            true
        }

        results
    }
}