package spacewaiker.helixjet.core

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import spacewaiker.helixjet.input.HJKeyInterceptor
import spacewaiker.helixjet.input.HJTypedHandler

class HJStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // key interceptor
        val interceptor = HJKeyInterceptor()
        IdeEventQueue.getInstance().addDispatcher(interceptor, null)

        // typed handler
        val typedAction = TypedAction.getInstance()
        val handler = HJTypedHandler(typedAction.rawHandler)
        typedAction.setupRawHandler(handler)
    }
}