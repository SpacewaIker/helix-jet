package spacewaiker.helixjet.actions

import spacewaiker.helixjet.editor.HJEditor
import spacewaiker.helixjet.editor.HJRegister
import spacewaiker.helixjet.input.HJInputKey

/**
 * Represents an editor action triggered by a key. Either an outright HJAction, or a Minor Mode
 *
 * @property name The name of the key action
 * @property doc A documentation string for the key action
 */
sealed class HJKeyAction(val name: String, val doc: String) {
    class HJAction(
        name: String,
        doc: String = "",
        private val action: ((HJActionContext) -> Unit)? = null,
    ) : HJKeyAction(name, doc) {
        fun execute(cx: HJActionContext) {
            if (action == null) {
                HJActions.logger.warn("Action $name is not yet defined")
            } else {
                HJActions.logger.debug("Executing action $name")
                action(cx)
            }
        }
    }

    class HJMinorMode(
        name: String,
        doc: String,
        val mapping: MutableMap<HJInputKey, HJKeyAction>,
    ) : HJKeyAction(name, doc) {

    }

    object HJPassthroughAction : HJKeyAction("Passthrough", "")

    override fun toString(): String {
        return when (this) {
            is HJAction -> "HJAction($name)"
            is HJMinorMode -> "HJMinorMode($name)"
            is HJPassthroughAction -> "HJPassthroughAction"
        }
    }
}

data class HJActionContext(
    val hjEditor: HJEditor,
    val rawCount: Int? = null,
    val register: HJRegister? = null,
) {
    val count get() = rawCount ?: 1
    val doc get() = hjEditor.editor.document
}