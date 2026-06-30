package spacewaiker.helixjet.treesitter

import io.github.treesitter.ktreesitter.Language
import io.github.treesitter.ktreesitter.Parser
import io.github.treesitter.ktreesitter.Query
import io.github.treesitter.ktreesitter.Tree
import io.github.treesitter.ktreesitter.cpp.TreeSitterCpp
import io.github.treesitter.ktreesitter.java.TreeSitterJava
import io.github.treesitter.ktreesitter.kotlin.TreeSitterKotlin
import io.github.treesitter.ktreesitter.rust.TreeSitterRust
import spacewaiker.helixjet.utils.Lazy

data class HJLanguageSyntax(
    val name: String,
    val language: Language,
    val textobjectQuery: Query,
    val tagQuery: Query,
    var tree: Tree,
)

object HJSyntaxLoader {
    private val languages = mapOf(
        "kotlin" to Lazy { Language(TreeSitterKotlin.language()) },
        "java" to Lazy { Language(TreeSitterJava.language()) },
        "cpp" to Lazy { Language(TreeSitterCpp.language()) },
        "rust" to Lazy { Language(TreeSitterRust.language()) },
    )
    private val textobjectQueries = languages.keys.associateWith { language -> lazyQuery(language, "textobjects") }
    private val tagQueries = languages.keys.associateWith { language -> lazyQuery(language, "tags") }

    fun getLanguageSyntax(name: String, source: String): HJLanguageSyntax? {
        val language = languages[name]?.invoke() ?: return null
        val textobjectQuery = textobjectQueries[name]?.invoke() ?: return null
        val tagQuery = tagQueries[name]?.invoke() ?: return null

        val tree = Parser(language).parse(source) // TODO: do not do this

        return HJLanguageSyntax(name, language, textobjectQuery, tagQuery, tree)
    }

    fun loadAll() {
        for (language in languages.keys) {
            languages[language]!!.invoke()
            textobjectQueries[language]!!.invoke()
            tagQueries[language]!!.invoke()
        }
    }

    private fun lazyQuery(name: String, queryName: String) = Lazy {
        val language = languages[name]!!()
        val queryFile = javaClass.getResource("/runtime/queries/$name/$queryName.scm")?.readText()!!
        val query = Query(language, queryFile)
        query
    }
}