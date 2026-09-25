package me.carandev.bacpac.services

import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

@Service
class DatabaseCleanupService {
    
    private val log = logger<DatabaseCleanupService>()
    
    companion object {
        fun getInstance(): DatabaseCleanupService = service()
    }
    
    data class CleanupResult(
        val success: Boolean,
        val message: String
    )
    
    fun dropDatabase(project: Project, dataSource: LocalDataSource, databaseName: String): CleanupResult {
        log.info("Iniciando auto-drop para la base de datos '$databaseName'...")
        
        // Escapar corchetes de cierre para seguridad sintáctica de identificadores SQL Server
        val sanitizedDbName = databaseName.replace("]", "]]")
        
        return try {
            val connectionManager = DatabaseConnectionManager.getInstance()
            val connectionRef = connectionManager.build(project, dataSource)
                .setAskPassword(false)
                .createBlocking()
                ?: return CleanupResult(
                    success = false,
                    message = "No se pudo establecer conexión con el servidor de base de datos."
                )
            
            try {
                val connection = connectionRef.get()
                val remoteConn = connection.remoteConnection
                val statement = remoteConn.createStatement()
                try {
                    // Forzar cierre de conexiones activas antes de ejecutar DROP DATABASE
                    val sql = """
                        IF EXISTS (SELECT name FROM sys.databases WHERE name = N'$sanitizedDbName')
                        BEGIN
                            ALTER DATABASE [$sanitizedDbName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
                            DROP DATABASE [$sanitizedDbName];
                        END
                    """.trimIndent()
                    
                    log.info("Ejecutando limpieza SQL: $sql")
                    statement.execute(sql)
                    log.info("Base de datos '$databaseName' eliminada exitosamente.")
                    CleanupResult(
                        success = true,
                        message = "Base de datos '$databaseName' eliminada del servidor."
                    )
                } finally {
                    statement.close()
                }
            } finally {
                connectionRef.close()
            }
        } catch (e: Exception) {
            log.warn("Error al intentar eliminar la base de datos '$databaseName': ${e.message}", e)
            CleanupResult(
                success = false,
                message = e.message ?: "Error desconocido al intentar eliminar la base de datos"
            )
        }
    }
}
