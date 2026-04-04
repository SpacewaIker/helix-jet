package spacewaiker.helixjet.editor

import spacewaiker.helixjet.input.HJInputKey

sealed class HJKeyAction(val name: String) {
    class HJAction(
        name: String,
        private val action: ((HJActionContext) -> Unit)? = null,
    ) : HJKeyAction(name) {
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
        val mapping: MutableMap<HJInputKey, HJKeyAction>,
    ) : HJKeyAction(name) {

    }

    override fun toString(): String {
        return when (this) {
            is HJAction -> "HJAction($name)"
            is HJMinorMode -> "HJMinorMode($name)"
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