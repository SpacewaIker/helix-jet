package spacewaiker.helixjet.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class HJRegisters {
    private val registers = mutableMapOf<HJRegister, MutableList<String>>()
    val rawRegisters get() = registers.toMap()

    fun write(register: HJRegister, values: List<String>, editor: HJEditor): Boolean {
        when (register) {
            HJRegister.Void -> {}
            HJRegister.SelIdx, HJRegister.CurrSel, HJRegister.FileName -> {
                editor.setError("Register $register does not support writing")
                return false
            }

            HJRegister.Clipboard -> {
                CopyPasteManager.getInstance().setContents(StringSelection(values.joinToString(separator = "\n")))
                registers[register] = values.reversed().toMutableList()
            }

            else -> registers[register] = values.reversed().toMutableList()
        }
        return true
    }

    fun push(register: HJRegister, value: String, editor: HJEditor): Boolean {
        when (register) {
            HJRegister.Void -> {}
            HJRegister.SelIdx, HJRegister.CurrSel, HJRegister.FileName -> {
                editor.setError("Register $register does not support pushing")
                return false
            }

            HJRegister.Clipboard -> {
                val contents =
                    CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor) as? String
                if (contents == null) {
                    editor.setError("Could not get system clipboard contents")
                    return false
                }
                val savedValues = registers.getOrPut(register, ::mutableListOf)

                if (!contentsAreSaved(savedValues, contents)) {
                    editor.setError("Failed to push register $register: clipboard does not match register contents")
                    return false
                }

                savedValues.add(value)
                var value = value
                if (contents.isNotEmpty()) {
                    value += "\n"
                }
                value += contents
                CopyPasteManager.getInstance().setContents(StringSelection(value))
            }

            else -> registers.getOrPut(register, ::mutableListOf).add(value)
        }
        return true
    }

    fun read(register: HJRegister, editor: HJEditor): List<String>? {
        return when (register) {
            HJRegister.Void -> emptyList()
            HJRegister.SelIdx -> {
                val selections = editor.selection.ranges.size
                (0 until selections).map { (it + 1).toString() }
            }

            HJRegister.CurrSel -> {
                editor.selection.slices(editor.editor.document).filterNotNull()
            }

            HJRegister.FileName -> {
                val projectRoot = editor.editor.project?.guessProjectDir()?.path ?: ""
                listOf(editor.editor.virtualFile.path.removePrefix(projectRoot))
            }

            HJRegister.Clipboard -> {
                // If we're pasting the same values that we just yanked, re-use the saved values. This allows pasting
                // multiple selections even when yanked to a clipboard
                val contents =
                    CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor) as? String
                if (contents == null) {
                    editor.setError("Could not get system clipboard contents")
                    return null
                }

                val savedValues = registers[register]
                if (savedValues != null && contentsAreSaved(savedValues, contents)) {
                    savedValues.reversed()
                } else {
                    listOf(contents)
                }
            }

            else -> registers[register]?.reversed()
        }
    }

    fun first(register: HJRegister, editor: HJEditor) = read(register, editor)?.first()

    fun last(register: HJRegister, editor: HJEditor) = read(register, editor)?.last()
}

private fun contentsAreSaved(savedValues: List<String>, contents: String): Boolean {
    val values = savedValues.reversed().iterator()
    var contents = contents

    if (values.hasNext()) {
        val first = values.next()
        if (contents.startsWith(first)) {
            contents = contents.substring(first.length)
        } else return false
    } else return contents.isEmpty()

    for (value in values) {
        if (contents.startsWith("\n") && contents.substring(1).startsWith(value)) {
            contents = contents.substring(1 + value.length)
        } else return false
    }

    return true
}

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

        fun fromChar(char: Char): HJRegister {
            return when (char) {
                '/' -> LastSearch
                ':' -> LastCmd
                '"' -> LastYank
                '@' -> LastMacro
                '_' -> Void
                '#' -> SelIdx
                '.' -> CurrSel
                '%' -> FileName
                '+' -> Clipboard
                else -> GeneralPurpose(char)
            }
        }

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

    override fun equals(other: Any?) = other is HJRegister && char == other.char

    override fun hashCode() = char.hashCode()
}