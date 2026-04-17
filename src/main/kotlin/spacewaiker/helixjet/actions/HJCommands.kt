package spacewaiker.helixjet.actions

import com.intellij.openapi.diagnostic.Logger
import spacewaiker.helixjet.editor.HJConfiguration
import spacewaiker.helixjet.editor.HJRegister
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

private val helixCommands = arrayOf(
    HJCommand("write", listOf("w"), "Save current file") { cx, _, _ ->
        executeIdeAction(cx, "SaveDocument")
    },
    HJCommand("write-all", listOf("wa"), "Save all files") { cx, _, _ ->
        executeIdeAction(cx, "SaveAll")
    },
    HJCommand("config-reload", listOf(), "Reload user config") { _, _, _ ->
        HJState.config = HJConfiguration()
    },
)

private val debugCommands = arrayOf(
    HJCommand("test", listOf("t"), "Test command. Prints all args and flags given.") { _, args, flags ->
        println("Test:")
        for (arg in args) {
            println("Arg: $arg")
        }
        for (flag in flags) {
            println("Flag: $flag")
        }
    },
    HJCommand("pushRegister", listOf(), "Push values into specified register") { _, args, _ ->
        val registerName = args.first()
        val register = HJRegister.GeneralPurpose(registerName[0])
        for (arg in args.drop(1)) {
            HJState.pushRegister(register, arg)
        }
    },
    HJCommand("printRegisters", listOf(), "Print all register contents") { _, _, _ ->
        println("All registers:")
        for ((register, contents) in HJState.allRegisterContents) {
            println("$register: $contents")
        }
    },
    HJCommand("print-config", listOf(), "Print all config contents (not mappings)") { _, _, _ ->
        println("Config:")
        println("scrolloff: ${HJState.config.scrolloff}")
        println("continueComments: ${HJState.config.continueComments}")
        println("searchSmartCase: ${HJState.config.searchSmartCase}")
        println("searchWrapAround: ${HJState.config.searchWrapAround}")
    },
)

private val commandsRaw =
    if (System.getProperty("idea.plugin.in.sandbox.mode") == "true") helixCommands + debugCommands else helixCommands