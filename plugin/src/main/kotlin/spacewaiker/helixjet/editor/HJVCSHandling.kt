package spacewaiker.helixjet.editor

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.openapi.util.TextRange

object HJVCSHandling {
    fun getHunk(hjEditor: HJEditor, range: HJRange): TextRange? {
        val psiFile = hjEditor.psiFile ?: return null

        val changedRangesInfo = VcsFacade.getInstance().getChangedRangesInfo(psiFile) ?: return null
        return changedRangesInfo.allChangedRanges.find { it.contains(range.cursor) }
    }
}