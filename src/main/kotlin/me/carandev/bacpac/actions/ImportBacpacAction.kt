package me.carandev.bacpac.actions

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbElement
import com.intellij.database.util.DbImplUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import me.carandev.bacpac.services.SqlPackageDetector
import me.carandev.bacpac.services.SqlPackageService
import me.carandev.bacpac.ui.ImportBacpacDialog
import me.carandev.bacpac.ui.SqlPackageNotFoundDialog

class ImportBacpacAction : AnAction() {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val dataSource = getLocalDataSourceFromServer(e)
        e.presentation.isEnabledAndVisible = dataSource != null && isSqlServer(dataSource)
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dataSource = getLocalDataSourceFromServer(e) ?: return
        
        // Verificar que SqlPackage está disponible
        if (!SqlPackageDetector.getInstance().isInstalled()) {
            val dialog = SqlPackageNotFoundDialog(project)
            if (!dialog.showAndGet() || dialog.result == SqlPackageNotFoundDialog.Result.CANCELLED) {
                return
            }
        }
        
        // Mostrar diálogo de importación
        val dialog = ImportBacpacDialog(project)
        if (!dialog.showAndGet()) {
            return
        }
        
        val sourceFile = dialog.sourceFile
        val databaseName = dialog.databaseName
        
        // Obtener parámetros de conexión
        val connectionParams = getConnectionParams(dataSource, project) ?: return
        
        // Confirmar si la operación puede tomar tiempo
        val confirm = Messages.showYesNoDialog(
            project,
            "Se importará el archivo .bacpac a una nueva base de datos llamada '$databaseName'.\n\n" +
            "Esta operación puede tardar varios minutos dependiendo del tamaño del archivo.\n\n" +
            "¿Deseas continuar?",
            "Confirmar importación",
            Messages.getQuestionIcon()
        )
        
        if (confirm != Messages.YES) {
            return
        }
        
        // Ejecutar importación en background
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Importando archivo .bacpac",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Importando a $databaseName..."
                
                val result = SqlPackageService.getInstance().importBacpac(
                    server = connectionParams.server,
                    databaseName = databaseName,
                    username = connectionParams.username,
                    password = connectionParams.password,
                    sourceFile = sourceFile,
                    onProgress = { line ->
                        indicator.text2 = line.take(100)
                    }
                )
                
                // Mostrar notificación
                val notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("Bacpac Notifications")
                
                if (result.success) {
                    notification.createNotification(
                        "Importación completada",
                        "El archivo .bacpac se importó correctamente a la base de datos '$databaseName'.\n\n" +
                        "Refresca el árbol de bases de datos para ver la nueva base de datos.",
                        NotificationType.INFORMATION
                    ).notify(project)
                } else {
                    notification.createNotification(
                        "Error en la importación",
                        "Error al importar a '$databaseName':\n${result.errorOutput.take(500)}",
                        NotificationType.ERROR
                    ).notify(project)
                }
            }
        })
    }
    
    private data class ConnectionParams(
        val server: String,
        val username: String?,
        val password: String?
    )
    
    /**
     * Obtiene el LocalDataSource solo si el usuario hizo click en el servidor/data source.
     * Retorna null si hizo click en una base de datos u otro elemento hijo.
     */
    private fun getLocalDataSourceFromServer(e: AnActionEvent): LocalDataSource? {
        val project = e.project ?: return null
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return null
        
        // Solo permitir si el click fue directamente en el DbDataSource (servidor)
        if (psiElement is DbDataSource) {
            return DbImplUtil.getMaybeLocalDataSource(psiElement.delegate)
        }
        
        // Si es un DbElement, verificar si es el data source raíz
        if (psiElement is DbElement) {
            // Buscar el DbDataSource padre
            val dbDataSource = findDbDataSource(psiElement)
            // Solo permitir si el elemento seleccionado ES el data source (no un hijo)
            if (dbDataSource != null && psiElement === dbDataSource) {
                return DbImplUtil.getMaybeLocalDataSource(dbDataSource.delegate)
            }
        }
        
        return null
    }
    
    /**
     * Busca el DbDataSource padre en la jerarquía PSI
     */
    private fun findDbDataSource(element: PsiElement): DbDataSource? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is DbDataSource) {
                return current
            }
            current = current.parent
        }
        return null
    }
    
    private fun getConnectionParams(dataSource: LocalDataSource, project: Project): ConnectionParams? {
        val url = dataSource.url ?: return null
        
        // Extraer servidor de la URL JDBC
        val serverRegex = Regex("jdbc:(?:sqlserver|jtds:sqlserver)://([^;/]+)")
        val server = serverRegex.find(url)?.groupValues?.get(1) ?: run {
            Messages.showErrorDialog(project, "No se pudo extraer el servidor de la URL de conexión", "Error")
            return null
        }
        
        val username = dataSource.username
        
        // Siempre pedir la contraseña al usuario para mayor seguridad
        var password: String? = null
        
        if (!username.isNullOrEmpty()) {
            password = Messages.showPasswordDialog(
                project,
                "Ingresa la contraseña para la conexión '${dataSource.name}':",
                "Contraseña requerida",
                null
            )
            if (password == null) return null
        }
        
        return ConnectionParams(server, username, password)
    }
    
    private fun isSqlServer(dataSource: LocalDataSource): Boolean {
        val url = dataSource.url?.lowercase() ?: ""
        val driver = dataSource.driverClass?.lowercase() ?: ""
        
        return url.contains("sqlserver") ||
               url.contains("jdbc:jtds") ||
               driver.contains("sqlserver") ||
               driver.contains("jtds")
    }
}
