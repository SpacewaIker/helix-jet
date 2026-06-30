package spacewaiker.helixjet.editor

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.structureView.StructureView
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.util.concurrency.AppExecutorUtil
import spacewaiker.helixjet.treesitter.HJTag

fun getStructureElements(editor: Editor, psiFile: PsiFile, onComplete: (List<HJTag>) -> Unit) {
    val readAction = readAction@{
        val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile)
            ?: return@readAction Pair(null, emptyList<HJTag>())
        val structureView = builder.createStructureView(null, editor.project!!)
        val results = mutableListOf<HJTag>()

        val stack = mutableListOf<Pair<TreeElement, TreeElement?>>(Pair(structureView.treeModel.root, null))
        while (stack.isNotEmpty()) {
            val (node, parent) = stack.removeLast()
            if (node is StructureViewTreeElement && parent != null) {
                val psiElement = node.value as? PsiElement ?: continue
                results.add(
                    HJTag(
                        psiElement.node.elementType.toString().lowercase().replaceFirstChar { it.uppercase() },
                        node.presentation.presentableText ?: "",
                        parent.presentation.presentableText ?: "",
                        psiElement.startOffset,
                        psiElement.endOffset,
                        editor.document.getLineNumber(psiElement.startOffset),
                        editor.document.getLineNumber(psiElement.endOffset),
                        psiFile.virtualFile,
                    )
                )
            }
            node.children.forEach { stack.add(Pair(it, node)) }
        }

        Pair(structureView, results)
    }

    AppExecutorUtil.getAppExecutorService().submit {
        val (structureView, results) =
            ApplicationManager.getApplication().runReadAction<Pair<StructureView?, List<HJTag>>>(readAction)

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