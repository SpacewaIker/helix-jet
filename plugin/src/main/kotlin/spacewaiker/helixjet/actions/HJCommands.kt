package spacewaiker.helixjet.actions

import com.intellij.lang.ASTNode
import com.intellij.lang.tree.util.children
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import io.github.treesitter.ktreesitter.Language
import io.github.treesitter.ktreesitter.Parser
import io.github.treesitter.ktreesitter.Query
import io.github.treesitter.ktreesitter.kotlin.TreeSitterKotlin
import spacewaiker.helixjet.editor.HJConfiguration
import spacewaiker.helixjet.editor.HJRegister
import spacewaiker.helixjet.editor.HJState

class HJCommand(
    val name: String,
    val aliases: List<String> = listOf(),
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
    HJCommand("config-open", listOf(), "Open user config") { cx, _, _ ->
        openFile(cx, HJConfiguration.userConfigPath)
    },
    HJCommand(
        "open",
        listOf("o", "edit", "e"),
        "Open a file from disk into a new tab",
        listOf("--bg  Open in background tab")
    ) { cx, args, flags ->
        if (args.isEmpty()) {
            cx.hjEditor.setError("'open': expected at least 1 argument, got 0")
            return@HJCommand
        }

        val openLocation = if (flags.contains("bg")) HJFileOpenLocation.BackgroundTab else HJFileOpenLocation.InPlace

        for (path in args) {
            openFile(cx, path, openLocation)
        }
    },
    HJCommand("hsplit", listOf("hs", "sp"), "Open the file in a horizontal split") { cx, args, _ ->
        if (args.isEmpty()) {
            cx.hjEditor.setError("'hsplit': expected at least 1 argument, got 0")
            return@HJCommand
        }

        for (path in args) {
            openFile(cx, path, HJFileOpenLocation.HSplit)
        }
    },
    HJCommand("vsplit", listOf("vs"), "Open the file in a vertical split") { cx, args, _ ->
        if (args.isEmpty()) {
            cx.hjEditor.setError("'vsplit': expected at least 1 argument, got 0")
            return@HJCommand
        }

        for (path in args) {
            openFile(cx, path, HJFileOpenLocation.VSplit)
        }
    },
    HJCommand("buffer-close", listOf("bc", "bclose"), "Close the current buffer.") { cx, _, _ ->
        // TODO: Does this close a modified but unsaved buffer?
        closeFiles(cx) { it.currentFile?.let { file -> listOf(file) } ?: emptyList() }
    },
    HJCommand(
        "buffer-close-others",
        listOf("bco", "bcloseother"),
        "Close all buffers except the currently focused one.",
        listOf("-s  don't close buffers that are visible")
    ) { cx, _, _ ->
        // TODO: don't close buffers that are visible
        closeFiles(cx) { it.openFiles.filterNot { file -> file == it.currentFile } }
    },
    HJCommand("buffer-close-all", listOf("bca", "bcloseall"), "Close all buffers without quitting.") { cx, _, _ ->
        closeFiles(cx) { it.openFiles.toList() }
    },
    HJCommand("quit", listOf("q"), "Close the current view.") { cx, _, _ ->
        quitWindows(cx) { manager -> manager.currentWindow?.let { listOf(it) } ?: emptyList() }
    },
    HJCommand("quit-all", listOf("qa"), "Close all views") { cx, _, _ ->
        quitWindows(cx) { manager -> manager.windows.toList() }
    },
    HJCommand(
        "yank-join",
        listOf(),
        "Yank joined selections. A separator can be provided as first argument. Default value is newline."
    ) { cx, args, _ ->
        val separator = args.getOrNull(0) ?: "\n"
        yankJoinedImpl(cx, separator, cx.register ?: HJRegister.LastYank)
    },
    HJCommand(
        "clipboard-yank-join",
        listOf(),
        "Yank joined selections into system clipboard. A separator can be provided as first argument. Default value is newline."
    ) { cx, args, _ ->
        val separator = args.getOrNull(0) ?: "\n"
        yankJoinedImpl(cx, separator, HJRegister.Clipboard)
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
    HJCommand(
        "pushRegister",
        listOf(),
        "Push values into specified register",
        listOf("--ow  Overwrite contents")
    ) { cx, args, flags ->
        val registerName = args.first()
        val register = HJRegister.GeneralPurpose(registerName[0])
        if (flags.contains("ow")) {
            HJState.registers.write(register, args, cx.hjEditor)
        } else {
            for (arg in args.drop(1)) {
                HJState.registers.push(register, arg, cx.hjEditor)
            }
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
    HJCommand("print-psi-dot", doc = "Print DOT representation of PsiElement tree") { cx, _, _ ->
        val psi = cx.hjEditor.psiFile ?: return@HJCommand
        val decl = StringBuilder()
        val edges = StringBuilder()

        var counter = 0
        val stack = mutableListOf(Pair("root", psi as PsiElement))
        while (stack.isNotEmpty()) {
            val (parentId, node) = stack.removeLast()
            val nodeId = "node_${counter++}"
            val nodeName = node.toString()
            val nodeBody = node.text.take(25).map { if (it == '\n') ' ' else it }.joinToString(separator = "")
            decl.appendLine("$nodeId [label=\"$nodeName\\n$nodeBody\"];")
            edges.appendLine("$parentId -> $nodeId;")
            stack.addAll(node.children.map { Pair(nodeId, it) }.asReversed())
        }

        println("digraph Psi {\n$decl$edges}")
    },
    HJCommand("print-ast-dot", doc = "Print DOT representation of ASTNode tree") { cx, _, _ ->
        val ast = cx.hjEditor.psiFile?.node ?: return@HJCommand
        val decl = StringBuilder()
        val edges = StringBuilder()

        var counter = 0
        val stack = mutableListOf(Pair("root", ast as ASTNode))
        while (stack.isNotEmpty()) {
            val (parentId, node) = stack.removeLast()
            val nodeId = "node_${counter++}"
            val nodeName = node.toString()
            val nodeType = node.elementType.toString()
            val nodeBody = node.text.take(25)
            val label = "$nodeName\\n$nodeType\\n$nodeBody".replace('\n', ' ').replace("\"", "\\\"")
            decl.appendLine("$nodeId [label=\"$label\"];")
            edges.appendLine("$parentId -> $nodeId;")
            stack.addAll(node.children().map { Pair(nodeId, it) }.toList().asReversed())
        }

        println("digraph AST {\n$decl$edges}")
    },
    HJCommand("print-ts", doc = "Print TreeSitter AST") { cx, _, _ ->
        val language = Language(TreeSitterKotlin.language())
        val parser = Parser(language)
        val tree = parser.parse(cx.doc.text)

        val query = Query(
            language, """
            (function_declaration
              (function_body)? @function.inside) @function.around

            ; Unlike function_body above, the constructor body is does not have its own
            ; symbol in the current grammar.
            (secondary_constructor) @function.around

            (class_declaration
              (class_body)? @class.inside) @class.around

            (class_declaration
              (enum_class_body) @class.inside) @class.around

            [
              (line_comment)
              (multiline_comment)
            ] @comment.inside

            (line_comment)+ @comment.around

            (multiline_comment) @comment.around

            (enum_entry) @entry.around
            (lambda_literal) @entry.around
            (property_declaration) @entry.around
            (object_declaration) @entry.around
            (assignment) @entry.around

            ; TODO: This doesn't work with annotations yet, but fixing it without breaking
            ; the case of multiple parameters is non-trivial.
            (function_value_parameters
              ((_) @parameter.inside . ","? @parameter.around) @parameter.around)

            ; secondary constructor uses function_value_parameters above
            (primary_constructor
              ((_)@parameter.inside . ","? @parameter.around) @parameter.around)

            (function_type_parameters
              ((_)@parameter.inside . ","? @parameter.around) @parameter.around)

            (value_arguments
              ((_)@parameter.inside . ","? @parameter.around) @parameter.around)

            (lambda_parameters
              ((_) @parameter.inside . ","? @parameter.around) @parameter.around)
        """.trimIndent()
        )

        val cursor = query(tree.rootNode)
        val matches = cursor.captures()
            .filter { (idx, match) -> match.captures[idx.toInt()].name == "function.inside" }
            .map { (idx, match) -> match.captures[idx.toInt()].node }
            .toList()

        println(matches.size)
        for (node in matches) {
            println()
            println(node.type)
            println(node.text())
            println(node.startPoint)
        }
    },
    HJCommand("print-ts-dot", doc = "Print DOT representation of TreeSitter tree") { cx, _, _ ->
        val language = Language(TreeSitterKotlin.language())
        val parser = Parser(language)
        val tree = parser.parse(cx.doc.text)
        val decl = StringBuilder()
        val edges = StringBuilder()

        val stack = mutableListOf(Pair(tree.rootNode.id, tree.rootNode))
        while (stack.isNotEmpty()) {
            val (parentId, node) = stack.removeLast()
            val nodeId = node.id
            val nodeType = node.type
            val nodeBody = node.text()?.take(25)
            val label = "$nodeType\\n$nodeBody".replace('\n', ' ').replace("\"", "\\\"")
            decl.appendLine("$nodeId [label=\"$label\"];")
            edges.appendLine("$parentId -> $nodeId;")
            stack.addAll(node.children.map { Pair(nodeId, it) }.toList().asReversed())
        }

        println("digraph AST {\n$decl$edges}")
    },
    HJCommand(
        "notify",
        flagsDoc = listOf("-i  information", "-w  warning", "-e  error"),
        doc = "Send notification to user"
    ) { cx, args, flags ->
        val type = if (flags.contains("e")) NotificationType.ERROR
        else if (flags.contains("w")) NotificationType.WARNING
        else NotificationType.INFORMATION

        cx.hjEditor.notify(args.joinToString(separator = " "), type)
    }
)

private val commandsRaw =
    if (System.getProperty("idea.plugin.in.sandbox.mode") == "true") helixCommands + debugCommands else helixCommands