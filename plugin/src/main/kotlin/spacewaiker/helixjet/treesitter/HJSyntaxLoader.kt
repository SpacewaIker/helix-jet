package spacewaiker.helixjet.treesitter

import io.github.treesitter.ktreesitter.Language
import io.github.treesitter.ktreesitter.Parser
import io.github.treesitter.ktreesitter.Query
import io.github.treesitter.ktreesitter.Tree
import io.github.treesitter.ktreesitter.bash.TreeSitterBash
import io.github.treesitter.ktreesitter.c.TreeSitterC
import io.github.treesitter.ktreesitter.c_sharp.TreeSitterC_sharp
import io.github.treesitter.ktreesitter.cmake.TreeSitterCmake
import io.github.treesitter.ktreesitter.cpp.TreeSitterCpp
import io.github.treesitter.ktreesitter.css.TreeSitterCss
import io.github.treesitter.ktreesitter.gdscript.TreeSitterGdscript
import io.github.treesitter.ktreesitter.go.TreeSitterGo
import io.github.treesitter.ktreesitter.html.TreeSitterHtml
import io.github.treesitter.ktreesitter.ini.TreeSitterIni
import io.github.treesitter.ktreesitter.java.TreeSitterJava
import io.github.treesitter.ktreesitter.javascript.TreeSitterJavascript
import io.github.treesitter.ktreesitter.json.TreeSitterJson
import io.github.treesitter.ktreesitter.kotlin.TreeSitterKotlin
import io.github.treesitter.ktreesitter.lua.TreeSitterLua
import io.github.treesitter.ktreesitter.markdown.TreeSitterMarkdown
import io.github.treesitter.ktreesitter.php.TreeSitterPhp
import io.github.treesitter.ktreesitter.python.TreeSitterPython
import io.github.treesitter.ktreesitter.ruby.TreeSitterRuby
import io.github.treesitter.ktreesitter.rust.TreeSitterRust
import io.github.treesitter.ktreesitter.sql.TreeSitterSql
import io.github.treesitter.ktreesitter.toml.TreeSitterToml
import io.github.treesitter.ktreesitter.typescript.TreeSitterTypescript
import io.github.treesitter.ktreesitter.yaml.TreeSitterYaml

data class HJLanguageSyntax(
    val name: String,
    val language: Language,
    val textobjectQuery: Query?,
    val tagQuery: Query?,
    var tree: Tree,
)

object HJSyntaxLoader {
    private val languages = mapOf(
        "bash" to lazy { Language(TreeSitterBash.language()) },
        "c" to lazy { Language(TreeSitterC.language()) },
        "c_sharp" to lazy { Language(TreeSitterC_sharp.language()) },
        "cmake" to lazy { Language(TreeSitterCmake.language()) },
        "cpp" to lazy { Language(TreeSitterCpp.language()) },
        "css" to lazy { Language(TreeSitterCss.language()) },
        "gdscript" to lazy { Language(TreeSitterGdscript.language()) },
        "go" to lazy { Language(TreeSitterGo.language()) },
        "html" to lazy { Language(TreeSitterHtml.language()) },
        "ini" to lazy { Language(TreeSitterIni.language()) },
        "java" to lazy { Language(TreeSitterJava.language()) },
        "javascript" to lazy { Language(TreeSitterJavascript.language()) },
        "json" to lazy { Language(TreeSitterJson.language()) },
        "kotlin" to lazy { Language(TreeSitterKotlin.language()) },
        "lua" to lazy { Language(TreeSitterLua.language()) },
        "markdown" to lazy { Language(TreeSitterMarkdown.language()) },
        "php" to lazy { Language(TreeSitterPhp.language()) },
        "python" to lazy { Language(TreeSitterPython.language()) },
        "ruby" to lazy { Language(TreeSitterRuby.language()) },
        "rust" to lazy { Language(TreeSitterRust.language()) },
        "sql" to lazy { Language(TreeSitterSql.language()) },
        "toml" to lazy { Language(TreeSitterToml.language()) },
        "typescript" to lazy { Language(TreeSitterTypescript.language()) },
        "yaml" to lazy { Language(TreeSitterYaml.language()) },
    )
    private val textobjectQueries = languages.keys.associateWith { language -> lazyQuery(language, "textobjects") }
    private val tagQueries = languages.keys.associateWith { language -> lazyQuery(language, "tags") }

    fun getLanguageSyntax(name: String, source: String): HJLanguageSyntax? {
        val language = languages[name]?.value ?: return null
        val textobjectQuery = textobjectQueries[name]?.value
        val tagQuery = tagQueries[name]?.value

        val tree = Parser(language).parse(source) // TODO: do not do this

        return HJLanguageSyntax(name, language, textobjectQuery, tagQuery, tree)
    }

    fun loadAll() {
        for (language in languages.keys) {
            languages[language]!!.value
            textobjectQueries[language]!!.value
            tagQueries[language]!!.value
        }
    }

    private fun lazyQuery(name: String, queryName: String) = lazy {
        val language = languages[name]!!.value
        javaClass.getResource("/runtime/queries/$name/$queryName.scm")?.readText()?.let { queryFile ->
            Query(language, queryFile)
        }
    }
}