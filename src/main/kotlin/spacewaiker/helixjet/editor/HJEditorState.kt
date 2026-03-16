package spacewaiker.helixjet.editor

enum class HJEditorMode {
    NORMAL,
    INSERT,
    SELECT,
}

object HJEditorState {
    var mode: HJEditorMode = HJEditorMode.NORMAL
    var config: HJConfiguration = HJConfiguration()
}