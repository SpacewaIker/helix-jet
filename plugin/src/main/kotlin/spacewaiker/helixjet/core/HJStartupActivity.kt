package spacewaiker.helixjet.core

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import spacewaiker.helixjet.editor.HJSelection
import spacewaiker.helixjet.editor.HJState
import spacewaiker.helixjet.input.HJKeyInterceptor
import spacewaiker.helixjet.input.HJMouseListener
import spacewaiker.helixjet.input.HJTypedHandler

class HJStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // key interceptor
        val interceptor = HJKeyInterceptor(project)
        IdeEventQueue.getInstance().addDispatcher(interceptor, null)

        // typed handler
        val typedAction = TypedAction.getInstance()
        val handler = HJTypedHandler(typedAction.rawHandler)
        typedAction.setupRawHandler(handler)

        // handle files already opened
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.allEditors.forEach { fileEditor ->
            val editor = (fileEditor as? TextEditor)?.editor ?: return@forEach
            ApplicationManager.getApplication().invokeLater {
                handleNewEditor(editor)
            }
        }

        val hjState = HJState.getInstance(project)

        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                if (event.editor.virtualFile == null) return
                handleNewEditor(event.editor)
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                hjState.releaseEditor(event.editor)
            }
        }, HJState.getInstance(project))

        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    event.oldEditor?.let {
                        val editor = (it as? TextEditor)?.editor ?: return@let
                        hjState.previousEditor = hjState.getHJEditor(editor) ?: return@let
                    }

                    hjState.currentEditor = event.newEditor?.let {
                        val editor = (it as? TextEditor)?.editor ?: return@let null
                        hjState.getHJEditor(editor)
                    }
                }
            }
        )
    }

    fun handleNewEditor(editor: Editor) {
        val project = editor.project ?: return
        val hjState = HJState.getInstance(project)
        hjState.initEditor(editor)
        editor.addEditorMouseListener(HJMouseListener())

        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                hjState.lastModification = Pair(editor.virtualFile, event.offset)
            }
        })

        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (hjState.isMouseButtonDown) return

                val hjEditor = hjState.getHJEditor(editor) ?: return
                if (hjEditor.isSettingNewSelection) return

                hjEditor.ideSetNewSelection(HJSelection(editor.caretModel))
            }
        })
    }
}