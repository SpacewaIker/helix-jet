package spacewaiker.helixjet.treesitter

import io.github.treesitter.ktreesitter.Language
import io.github.treesitter.ktreesitter.Query
import io.github.treesitter.ktreesitter.cpp.TreeSitterCpp
import io.github.treesitter.ktreesitter.java.TreeSitterJava
import io.github.treesitter.ktreesitter.kotlin.TreeSitterKotlin
import io.github.treesitter.ktreesitter.rust.TreeSitterRust

data class HJLanguageSyntax(
    val name: String,
    val language: Language,
    val query: Query,
)

object HJSyntaxLoader {
    private val languages = mutableMapOf<String, HJLanguageSyntax>()

    private val languageMap = mapOf(
        "kotlin" to TreeSitterKotlin.language(),
        "java" to TreeSitterJava.language(),
        "cpp" to TreeSitterCpp.language(),
        "rust" to TreeSitterRust.language(),
    )

    fun getLanguageSyntax(name: String): HJLanguageSyntax? {
        if (languages.containsKey(name)) {
            return languages[name]
        }

        val language = Language(languageMap[name]!!)
        val queryFile = javaClass.getResource("/runtime/queries/$name/textobjects.scm")?.readText()
            ?: return null
        val query = Query(language, queryFile)

        val syntax = HJLanguageSyntax(name, language, query)
        languages[name] = syntax

        return syntax
    }
}