package spacewaiker.helixjet.core

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
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

        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                if (event.editor.virtualFile == null) return
                handleNewEditor(event.editor)
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                HJState.releaseEditor(event.editor)
            }
        }, HJProjectService.getInstance(project))
    }

    fun handleNewEditor(editor: Editor) {
        HJState.initEditor(editor)
        editor.addEditorMouseListener(HJMouseListener())

        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                HJState.lastModification = Pair(editor.virtualFile, event.offset)
            }
        })
    }
}