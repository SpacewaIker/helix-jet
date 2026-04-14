package spacewaiker.helixjet.actions

import com.intellij.openapi.diagnostic.Logger
import spacewaiker.helixjet.editor.HJConfiguration
import spacewaiker.helixjet.editor.HJState

class HJCommand(
    val name: String,
    val aliases: List<String>,
    val doc: String = "",
    val flagsDoc: List<String> = listOf(),
    private val action: ((HJActionContext, List<String>, List<String>) -> Unit)? = null,
) {
    fun execute(cx: HJActionContext, args: List<String>, flags: List<String>) {
        if (action == null) {
            HJCommands.logger.warn("Command $name is not yet defined")
        } else {
            HJCommands.logger.debug("Executing command $name")
            action(cx, args, flags)
        }
    }
}

object HJCommands {
    val logger = Logger.getInstance(this::class.java)

    private val commands: Map<String, HJCommand> = createCommandsMap()
    val allCommands = commandsRaw // we don't use the map because we don't want the aliases to duplicate commands

    fun getCommand(name: String): HJCommand? {
        val command = commands[name]
        if (command == null) logger.debug("HJCommands: No command found for $name")
        return command
    }

    private fun createCommandsMap(): Map<String, HJCommand> {
        val map = mutableMapOf<String, HJCommand>()

        for (command in commandsRaw) {
            val names = command.aliases + command.name
            for (name in names) {
                if (map.containsKey(name)) {
                    logger.error("Command $name is already defined in commands.")
                    continue
                }
                map[name] = command
            }
        }

        return map
    }
}

private val commandsRaw = arrayOf(
    HJCommand("write", listOf("w"), "Save current file") { cx, _, _ ->
        executeIdeAction(cx, "SaveDocument")
    },
    HJCommand("write-all", listOf("wa"), "Save all files") { cx, _, _ ->
        executeIdeAction(cx, "SaveAll")
    },
    HJCommand("config-reload", listOf(), "Reload user config") { _, _, _ ->
        HJState.config = HJConfiguration()
    },

    // non-helix commands
    HJCommand("test", listOf("t"), "") { _, args, flags ->
        println("Test:")
        for (arg in args) {
            println("Arg: $arg")
        }
        for (flag in flags) {
            println("Flag: $flag")
        }
    }
)
