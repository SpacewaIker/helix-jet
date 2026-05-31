package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Editor
import spacewaiker.helixjet.editor.HJState.allEditors
import spacewaiker.helixjet.editor.HJState.config
import spacewaiker.helixjet.editor.HJState.focusedEditor
import spacewaiker.helixjet.editor.HJState.mode
import spacewaiker.helixjet.editor.HJState.typedHandlerBlock
import spacewaiker.helixjet.ui.HJHelpWidget
import spacewaiker.helixjet.ui.HJPrompt

enum class HJEditorMode {
    Normal, Insert, Select,
}

/**
 * Main state of the plugin / container of data for the plugin
 *
 * @property mode The current HJEditorMode of the plugin
 * @property config The HJConfiguration in use
 * @property allEditors Get list of all Editors
 * @property focusedEditor Get the currently focused Editor, if there is one
 * @property typedHandlerBlock If the typedHandler should be blocked from processing keys. Used during the transition to insert mode
 */
object HJState {
    var keymappingEnabled = true
    var mode: HJEditorMode = HJEditorMode.Normal
    var config: HJConfiguration = HJConfiguration()
    private val editors: MutableMap<Editor, HJEditor> = mutableMapOf()
    val registers = HJRegisters()
    val allRegisterContents get() = registers.rawRegisters.toList()
    val allEditors get() = editors.keys
    var activePrompt: HJPrompt? = null
    var helpWidget: HJHelpWidget? = null

    private var cachedFocusedEditor: Editor? = null
    val focusedEditor: Editor?
        get() {
            // try to use cached editor
            if (cachedFocusedEditor != null && cachedFocusedEditor!!.contentComponent.hasFocus()) return cachedFocusedEditor

            for (editor in allEditors) {
                if (editor.contentComponent.hasFocus()) {
                    cachedFocusedEditor = editor
                    return editor
                }
            }
            cachedFocusedEditor = null
            return null
        }
    var typedHandlerBlock = false

    fun getHJEditor(editor: Editor) = editors.getOrPut(editor) { HJEditor(editor) }

    fun releaseEditor(editor: Editor) = editors.remove(editor) != null

    fun forAllEditors(f: (HJEditor) -> Unit) {
        editors.values.forEach(f)
    }

    fun getOrCreateHelpWidget(editor: Editor): HJHelpWidget {
        if (helpWidget == null) {
            helpWidget = HJHelpWidget(editor)
            helpWidget!!.show()
        }

        return helpWidget!!
    }
}