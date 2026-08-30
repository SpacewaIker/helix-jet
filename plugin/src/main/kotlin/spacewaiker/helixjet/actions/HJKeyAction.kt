package spacewaiker.helixjet.actions

import spacewaiker.helixjet.editor.HJEditor
import spacewaiker.helixjet.editor.HJRegister
import spacewaiker.helixjet.editor.HJState
import spacewaiker.helixjet.input.HJInputHandler
import spacewaiker.helixjet.input.HJInputKey
import java.awt.event.InputEvent

/**
 * Represents an editor action triggered by a key. Either an outright HJAction, or a Minor Mode
 *
 * @property name The name of the key action
 * @property doc A documentation string for the key action
 */
sealed class HJKeyAction(val name: String, val doc: String) {
    open class HJAction(
        name: String,
        doc: String = "",
        private val action: ((HJActionContext) -> Unit)? = null,
    ) : HJKeyAction(name, doc) {
        val isImplemented = action != null

        fun execute(cx: HJActionContext) {
            if (action == null) {
                cx.hjEditor.setWarning("Action $name is not yet implemented")
                HJActions.logger.warn("Action $name is not yet implemented")
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
        val sticky: Boolean = false,
    ) : HJKeyAction(name, doc) {
        val groupedMapping: List<Pair<HJKeyAction, List<HJInputKey>>>
            get() {
                val list = mutableMapOf<HJKeyAction, MutableList<HJInputKey>>()

                for ((key, action) in mapping) {
                    list.getOrPut(action, { mutableListOf() }).add(key)
                }

                return list.toList()
            }
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
    val hjState: HJState,
    val hjEditor: HJEditor,
    val rawCount: Int? = null,
    val register: HJRegister? = null,
    val inputEvent: InputEvent? = null,
) {
    val count get() = rawCount ?: 1
    val doc get() = hjEditor.editor.document
    val project get() = hjState.project
    val inputHandler get() = HJInputHandler.getInstance(hjState.project)
}