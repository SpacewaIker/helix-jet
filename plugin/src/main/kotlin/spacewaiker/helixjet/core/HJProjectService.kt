package spacewaiker.helixjet.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class HJProjectService : Disposable {
    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): HJProjectService = project.getService(HJProjectService::class.java)
    }
}