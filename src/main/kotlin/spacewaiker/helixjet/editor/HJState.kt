package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

enum class HJEditorMode {
    NORMAL, INSERT, SELECT,
}

object HJState {
    var mode: HJEditorMode = HJEditorMode.NORMAL
    var config: HJConfiguration = HJConfiguration()
    private val editors: MutableMap<Editor, HJEditor> = mutableMapOf()
    private val registers: MutableMap<HJRegister, List<String>> = mutableMapOf()
    val allRegisterContents get() = registers.toList()

    fun getHJEditor(editor: Editor) = editors.getOrPut(editor) { HJEditor(editor) }

    fun copyToRegister(doc: Document, selection: HJSelection, register: HJRegister) {
        HJRegister.selectionToRegister(doc, selection, register)?.let {
            if (register == HJRegister.Clipboard) {
                CopyPasteManager.getInstance().setContents(StringSelection(it[0]))
            } else {
                registers[register] = it
            }
        }
    }

    fun getRegisterContents(register: HJRegister) =
        if (register == HJRegister.Clipboard) listOf(CopyPasteManager.getInstance().contents.toString()) else registers[register]

    fun forAllEditors(f: (HJEditor) -> Unit) {
        editors.values.forEach(f)
    }
}