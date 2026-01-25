package me.carandev.bacpac.actions

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.DasNamespace
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbElement
import com.intellij.database.psi.DbPsiFacade
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
import me.carandev.bacpac.ui.ExportBacpacDialog
import me.carandev.bacpac.ui.SqlPackageNotFoundDialog

class ExportBacpacAction : AnAction() {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val context = getExportContext(e)
        e.presentation.isEnabledAndVisible = context != null
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = getExportContext(e) ?: return
        
        // Verificar que SqlPackage está disponible
        if (!SqlPackageDetector.getInstance().isInstalled()) {
            val dialog = SqlPackageNotFoundDialog(project)
            if (!dialog.showAndGet() || dialog.result == SqlPackageNotFoundDialog.Result.CANCELLED) {
                return
            }
        }
        
        // Obtener parámetros de conexión
        val connectionParams = getConnectionParams(context.dataSource, project) ?: return
        
        // Mostrar diálogo de exportación
        val dialog = ExportBacpacDialog(project, context.databaseName)
        if (!dialog.showAndGet()) {
            return
        }
        
        val targetFile = dialog.targetFile
        val databaseName = context.databaseName
        
        // Ejecutar exportación en background
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Exportando base de datos a .bacpac",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Exportando $databaseName..."
                
                val result = SqlPackageService.getInstance().exportBacpac(
                    server = connectionParams.server,
                    databaseName = databaseName,
                    username = connectionParams.username,
                    password = connectionParams.password,
                    targetFile = targetFile,
                    onProgress = { line ->
                        indicator.text2 = line.take(100)
                    }
                )
                
                // Mostrar notificación
                val notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup("Bacpac Notifications")
                
                if (result.success) {
                    notification.createNotification(
                        "Exportación completada",
                        "La base de datos $databaseName se exportó correctamente a:\n$targetFile",
                        NotificationType.INFORMATION
                    ).notify(project)
                } else {
                    notification.createNotification(
                        "Error en la exportación",
                        "Error al exportar $databaseName:\n${result.errorOutput.take(500)}",
                        NotificationType.ERROR
                    ).notify(project)
                }
            }
        })
    }
    
    private data class ExportContext(
        val dataSource: LocalDataSource,
        val databaseName: String
    )
    
    private data class ConnectionParams(
        val server: String,
        val username: String?,
        val password: String?
    )
    
    /**
     * Obtiene el contexto de exportación solo si el usuario hizo click en una base de datos específica.
     * Retorna null si hizo click en el servidor/data source o en otro elemento.
     */
    private fun getExportContext(e: AnActionEvent): ExportContext? {
        val project = e.project ?: return null
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return null
        
        // Verificar si es un DbElement (elemento del árbol de base de datos)
        if (psiElement !is DbElement) return null
        
        val dbElement = psiElement as DbElement
        val dasObject = dbElement.delegate as? DasObject ?: return null
        
        // Verificar si es una base de datos (namespace de tipo DATABASE)
        val databaseName: String
        val dbDataSource: DbDataSource
        
        if (dasObject is DasNamespace && dasObject.kind == ObjectKind.DATABASE) {
            // El usuario hizo click directamente en una base de datos
            databaseName = dasObject.name
            dbDataSource = findDbDataSource(psiElement) ?: return null
        } else {
            // No es una base de datos, no mostrar la acción
            return null
        }
        
        // Obtener el LocalDataSource
        val localDataSource = DbImplUtil.getMaybeLocalDataSource(dbDataSource.delegate) ?: return null
        
        // Verificar que es SQL Server
        if (!isSqlServer(localDataSource)) return null
        
        return ExportContext(localDataSource, databaseName)
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
