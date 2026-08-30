package spacewaiker.helixjet.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import spacewaiker.helixjet.editor.HJState
import java.nio.file.FileSystems
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class HJFileGlobMatcher(project: Project) {
    val globPatterns = HJState.getInstance(project).config.languagesToml.values.flatMap { language ->
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

    companion object {
        fun getInstance(project: Project): HJFileGlobMatcher = project.getService(HJFileGlobMatcher::class.java)
    }
}