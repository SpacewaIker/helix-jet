package spacewaiker.helixjet.config

import spacewaiker.helixjet.editor.HJState
import java.nio.file.FileSystems
import java.nio.file.Paths

object HJFileGlobMatcher {
    val globPatterns = HJState.config.languagesToml.values.flatMap { language ->
        (emptyList<String>() +
                (language.fileTypesGlob ?: emptyList()) +
                (language.fileTypesStr?.map { "*.$it" } ?: emptyList()))
            .map { Pair(it, language) }
    }

    /**
     * Try to match the given file path against one of the glob patterns found in the config's languages.toml
     */
    fun match(path: String): HJLanguage? {
        val fs = FileSystems.getDefault()
        val normalizedPath = path.replace("\\", "/")
        val fileName = Paths.get(normalizedPath).fileName?.toString() ?: return null

        return globPatterns.find { (pattern, _) ->
            val matcher = fs.getPathMatcher("glob:$pattern")
            // match against full path or just filename
            matcher.matches(Paths.get(normalizedPath)) || matcher.matches(Paths.get(fileName))
        }?.second
    }
}