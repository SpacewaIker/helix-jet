package spacewaiker.helixjet.editor

enum class HJEditorMode {
    NORMAL,
    INSERT,
    SELECT,
}

class HJEditorState {
    var mode: HJEditorMode = HJEditorMode.NORMAL
    var config: HJConfiguration = HJConfiguration()
}