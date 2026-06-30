package spacewaiker.helixjet.core

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import spacewaiker.helixjet.input.HJKeyInterceptor
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
                HJEditorListener.handleNewEditor(editor)
            }
        }

        // handle files opened after startup
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            HJEditorListener()
        )
    }
}