package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Editor

enum class HJEditorMode {
    NORMAL, INSERT, SELECT,
}

object HJState {
    var mode: HJEditorMode = HJEditorMode.NORMAL
    val config: HJConfiguration = HJConfiguration()
    private var editors: MutableMap<Editor, HJEditor> = mutableMapOf()

    fun getHJEditor(editor: Editor) = editors.getOrPut(editor) { HJEditor(editor) }
}