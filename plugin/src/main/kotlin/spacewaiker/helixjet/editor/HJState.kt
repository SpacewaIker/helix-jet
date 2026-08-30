package spacewaiker.helixjet.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import spacewaiker.helixjet.config.HJConfiguration
import spacewaiker.helixjet.input.HJInputKey
import spacewaiker.helixjet.ui.HJHelpWidget
import spacewaiker.helixjet.ui.HJPicker
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
 * @property typedHandlerBlock If the typedHandler should be blocked from processing keys. Used during the transition to insert mode
 */
@Service(Service.Level.PROJECT)
class HJState(val project: Project) : Disposable {
    var keymappingEnabled = true
    var isMouseButtonDown = false

    var mode: HJEditorMode = HJEditorMode.Normal
    private var configInner: HJConfiguration? = null
    var config: HJConfiguration
        get() = configInner ?: HJConfiguration(project).also { configInner = it }
        set(value) {
            configInner = value
        }
    private val editors: MutableMap<Editor, HJEditor> = mutableMapOf()
    val allEditors get() = editors.values.toList()
    val registers = HJRegisters()
    val allRegisterContents get() = registers.rawRegisters.toList()
    var activePrompt: HJPrompt? = null
    var helpWidget: HJHelpWidget? = null
    var typedHandlerBlock = false
    var selectedRegister: HJRegister? = null
        set(value) {
            field = value
            HJStatusBarInputWidget.getInstance(project).updateInputWidget()
        }
    var macroRecording: Pair<HJRegister, MutableList<HJInputKey>>? = null
    val macroReplaying: MutableList<HJRegister> = mutableListOf()
    val lastInsertKeystrokes: MutableList<HJInputKey> = mutableListOf()
    var lastPicker: HJPicker? = null
    var lastModification: Pair<VirtualFile, Int>? = null
    var previousEditor: HJEditor? = null
    var currentEditor: HJEditor? = null

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

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): HJState = project.getService(HJState::class.java)
    }
}