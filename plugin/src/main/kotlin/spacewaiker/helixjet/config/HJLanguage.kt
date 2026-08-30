package spacewaiker.helixjet.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HJLanguages(
    @SerialName("language")
    val languages: List<HJLanguage>,
)

@Serializable
data class HJLanguage(
    val name: String,
    @SerialName("file-types-str")
    val fileTypesStr: List<String>? = null,
    @SerialName("file-types-glob")
    val fileTypesGlob: List<String>? = null,
    val indent: Indent? = null,
    @SerialName("comment-tokens")
    val commentTokens: List<String>? = null,
    @SerialName("block-comment-tokens")
    val blockCommentTokens: List<BlockCommentToken>? = null,
    val grammar: String? = null,
) {
    val grammarOrName = grammar ?: name

    @Serializable
    data class Indent(
        @SerialName("tab-width")
        val tabWidth: Int,
        val unit: String,
    )

    @Serializable
    data class BlockCommentToken(
        val start: String,
        val end: String,
    ) {
        companion object {
            val DEFAULT = BlockCommentToken("/*", "*/")
        }
    }
}