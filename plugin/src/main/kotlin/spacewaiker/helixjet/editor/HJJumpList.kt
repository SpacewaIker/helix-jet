package spacewaiker.helixjet.editor

import com.intellij.openapi.vfs.VirtualFile

private const val JUMP_LIST_CAPACITY = 30

data class HJJump(
    val file: VirtualFile,
    var selection: HJSelection,
)

data class HJJumpList(
    val jumps: ArrayDeque<HJJump>,
    var current: Int,
) {
    constructor() : this(ArrayDeque(), 0)
    constructor(initial: HJJump) : this(ArrayDeque(listOf(initial)), 0)
    constructor(file: VirtualFile, selection: HJSelection) : this(HJJump(file, selection))

    private fun pushImpl(jump: HJJump): Int {
        var numRemovedFromFront = 0

        jumps.subList(current, jumps.size).clear() // keep first `current` elements
        // don't push duplicates
        if (jumps.lastOrNull() != jump) {
            // if the jumplist is full, drop the oldest item
            while (jumps.size >= JUMP_LIST_CAPACITY) {
                jumps.removeFirst()
                numRemovedFromFront++
            }

            jumps.add(jump)
            current = jumps.size
        }

        return numRemovedFromFront
    }

    fun push(jump: HJJump) {
        pushImpl(jump)
    }

    fun forward(count: Int): HJJump? {
        return if (current + count < jumps.size) {
            current += count
            jumps[current]
        } else null
    }

    fun backward(hjEditor: HJEditor, count: Int): HJJump? {
        var curr = if (count > current) null else current - count

        return if (curr != null) {
            if (current == jumps.size) {
                val jump = HJJump(hjEditor.editor.virtualFile, hjEditor.selection.clone())
                val numRemoved = pushImpl(jump)
                curr = (curr - numRemoved).coerceAtLeast(0)
            }
            current = curr

            // Avoid jumping to the current location
            val (file, selection) = jumps.getOrNull(current) ?: return null
            if (hjEditor.editor.virtualFile == file && hjEditor.selection == selection) {
                current = if (current >= 1) current - 1 else return null
            }
            jumps[current]
        } else null
    }

    fun remove(file: VirtualFile) {
        jumps.retainAll { (otherFile, _) -> otherFile == file }
    }

    /**
     * Applies a transaction of changes to the jumplist.
     * This is necessary to ensure that changes to documents to not leave jump-list selections pointing to parts of
     * the text which no longer exist.
     */
    private fun apply(transaction: HJTransaction, hjEditor: HJEditor) {
        for (jump in jumps) {
            if (jump.file == hjEditor.editor.virtualFile) {
                jump.selection = jump.selection.clone()
                    .map(transaction.changes)
                    .ensureInvariants(hjEditor.editor.document)
            }
        }
    }
}