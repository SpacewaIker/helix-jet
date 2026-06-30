package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Editor
import spacewaiker.helixjet.config.HJConfiguration
import spacewaiker.helixjet.editor.HJState.allEditors
import spacewaiker.helixjet.editor.HJState.config
import spacewaiker.helixjet.editor.HJState.mode
import spacewaiker.helixjet.editor.HJState.typedHandlerBlock
import spacewaiker.helixjet.input.HJInputKey
import spacewaiker.helixjet.ui.HJHelpWidget
import spacewaiker.helixjet.ui.HJPrompt
import spacewaiker.helixjet.ui.HJStatusBarInputWidget

enum class HJEditorMode {
    Normal, Insert, Select,
}

/**
 * Main state of the plugin / container of data for the plugin
 *
 * @property mode The current HJEditorMode of the plugin
 * @property config The HJConfiguration in use
 * @property allEditors Get list of all Editors
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
    var typedHandlerBlock = false
    var selectedRegister: HJRegister? = null
        set(value) {
            field = value
            HJStatusBarInputWidget.updateInputWidget()
        }
    var macroRecording: Pair<HJRegister, MutableList<HJInputKey>>? = null
    val macroReplaying: MutableList<HJRegister> = mutableListOf()
    val lastInsertKeystrokes: MutableList<HJInputKey> = mutableListOf()

    fun getHJEditor(editor: Editor) = editors[editor]
    
    fun initEditor(editor: Editor) {
        if (!editors.containsKey(editor))
            editors[editor] = HJEditor(editor)
    }

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