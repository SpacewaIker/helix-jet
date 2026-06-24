package spacewaiker.helixjet.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.tree.util.children
import com.intellij.lang.tree.util.parents
import com.intellij.lang.tree.util.siblings
import com.intellij.openapi.diagnostic.Logger

enum class HJAstObject {
    /** Type definition. E.g. class, struct, object. */
    TYPE_DEF,

    /** Functor. E.g. function, method, lambda. */
    FUNCTION,

    /** Argument/parameter in functor definition or call. */
    ARGUMENT,

    /** Line or block comment (incl. documentation). */
    COMMENT,

    /** Entry in data structure. */
    ENTRY,

    /** XML-like element. */
    XML_ELEMENT,

    /** Block. */
    BLOCK, // TODO: Do we need a different BLOCK type for type and function?

    /** Separator for `ARGUMENT`. */
    ARG_SEPARATOR,
}

abstract class HJLanguageSyntax {
    val logger = Logger.getInstance(javaClass)

    abstract fun getAstObject(node: ASTNode): HJAstObject?

    open fun getParentAstObject(node: ASTNode, astObject: HJAstObject) = node.parents(withSelf = true)
        .find { getAstObject(it) == astObject }

    open fun aroundAstObject(node: ASTNode, astObject: HJAstObject): Pair<Int, Int>? = when (astObject) {
        // whole thing + trailing arg separator if any
        HJAstObject.ARGUMENT, HJAstObject.ENTRY -> {
            val end = node.siblings(forward = true, withSelf = false)
                .find { getAstObject(it) == HJAstObject.ARG_SEPARATOR }?.endOffset
                ?: node.endOffset

            Pair(node.startOffset, end)
        }
        // get all line comments above or below
        HJAstObject.COMMENT -> {
            val start = node.siblings(forward = false, withSelf = true)
                .findLast { getAstObject(it) == HJAstObject.COMMENT }!!.startOffset
            val end = node.siblings(forward = true, withSelf = true)
                .findLast { getAstObject(it) == HJAstObject.COMMENT }!!.endOffset

            Pair(start, end)
        }
        // whole thing
        else -> Pair(node.startOffset, node.endOffset)
    }

    open fun insideAstObject(node: ASTNode, astObject: HJAstObject): Pair<Int, Int>? = when (astObject) {
        // block, if any
        HJAstObject.TYPE_DEF, HJAstObject.FUNCTION -> {
            val block = node.findDescendant { getAstObject(it) == HJAstObject.BLOCK }
            block?.let { Pair(it.startOffset, it.endOffset) }
        }
        // just this thing
        else -> Pair(node.startOffset, node.endOffset)
    }

}

object HJLanguageSyntaxProvider {
    val syntaxes = mapOf(
        "kotlin" to HJKotlinSyntax,
        "java" to HJJavaSyntax,
    )
}

object HJKotlinSyntax : HJLanguageSyntax() {
    override fun getAstObject(node: ASTNode) = when (node.elementType.toString()) {
        "CLASS", "OBJECT_DECLARATION" -> HJAstObject.TYPE_DEF
        "FUN", "FUNCTION_LITERAL" -> HJAstObject.FUNCTION
        "BLOCK", "CLASS_BODY" -> HJAstObject.BLOCK
        "VALUE_PARAMETER", "VALUE_ARGUMENT" -> HJAstObject.ARGUMENT
        "COMMA" -> HJAstObject.ARG_SEPARATOR
        "EOL_COMMENT", "KDoc", "BLOCK_COMMENT" -> HJAstObject.COMMENT
        "ENUM_ENTRY" -> HJAstObject.ENTRY
        else -> null
    }

    override fun insideAstObject(node: ASTNode, astObject: HJAstObject) = when (astObject) {
        // entry includes comma so we remove it manually
        HJAstObject.ENTRY -> Pair(node.startOffset, node.endOffset - 1)
        else -> super.insideAstObject(node, astObject)
    }
}

object HJJavaSyntax : HJLanguageSyntax() {
    override fun getAstObject(node: ASTNode) = when (node.elementType.toString()) {
        "CLASS" -> HJAstObject.TYPE_DEF
        "METHOD", "LAMBDA_EXPRESSION" -> HJAstObject.FUNCTION
        "CODE_BLOCK", "CLASS_BODY" -> HJAstObject.BLOCK
        "PARAMETER", "REFERENCE_EXPRESSION", "LITERAL_EXPRESSION" -> HJAstObject.ARGUMENT
        "COMMA" -> HJAstObject.ARG_SEPARATOR
        "END_OF_LINE_COMMENT", "DOC_COMMENT", "C_STYLE_COMMENT" -> HJAstObject.COMMENT
        "ENUM_CONSTANT" -> HJAstObject.ENTRY
        else -> null
    }

    override fun insideAstObject(node: ASTNode, astObject: HJAstObject) = when (astObject) {
        // classes don't have an explicit block. Instead, select everything inside braces manually
        HJAstObject.TYPE_DEF -> {
            val start = node.children().find { it.elementType.toString() == "LBRACE" }?.startOffset
            val end = node.children().find { it.elementType.toString() == "RBRACE" }?.endOffset

            if (start != null && end != null) Pair(start, end) else null
        }

        else -> super.insideAstObject(node, astObject)
    }
}
