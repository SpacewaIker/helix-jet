package spacewaiker.helixjet.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange

sealed class HJRegister {
    val char: Char
    val readOnly: Boolean

    constructor(char: Char, readOnly: Boolean = false) {
        this.char = char
        this.readOnly = readOnly
    }

    object LastSearch : HJRegister('/')
    object LastCmd : HJRegister(':')
    object LastYank : HJRegister('"')
    object LastMacro : HJRegister('@')
    object Void : HJRegister('_')
    object SelIdx : HJRegister('#', true)
    object CurrSel : HJRegister('.', true)
    object FileName : HJRegister('%', true)
    object Clipboard : HJRegister('+')
    class GeneralPurpose(char: Char) : HJRegister(char)

    companion object {
        val logger = Logger.getInstance(this::class.java)

        fun selectionToRegister(hjEditor: HJEditor, selection: HJSelection, register: HJRegister): List<String>? {
            if (register.readOnly) {
                hjEditor.setError("Register ${register.char} does not support writing")
                logger.debug("Register '${register.char}' is read-only")
                return null
            }

            val doc = hjEditor.editor.document
            return when (register) {
                is Void -> null
                is Clipboard -> {
                    val joinedString = selection.ranges.joinToString(separator = "\n") { range ->
                        doc.getText(TextRange(range.from, range.to))
                    }
                    listOf(joinedString)
                }

                is LastSearch, is LastCmd, is LastMacro -> {
                    val primaryRange = selection.primary
                    val primaryString = doc.getText(TextRange(primaryRange.from, primaryRange.to))
                    listOf(primaryString)
                }

                else -> {
                    selection.ranges.map { range ->
                        doc.getText(TextRange(range.from, range.to))
                    }
                }
            }
        }
    }

    override fun toString() = char.toString()
}