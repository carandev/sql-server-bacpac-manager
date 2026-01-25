package me.carandev.bacpac.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import me.carandev.bacpac.settings.BacpacSettings
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Service
class SqlPackageService {
    
    private val log = logger<SqlPackageService>()
    
    companion object {
        fun getInstance(): SqlPackageService = service()
    }
    
    data class ExecutionResult(
        val success: Boolean,
        val output: String,
        val errorOutput: String,
        val exitCode: Int
    )
    
    fun exportBacpac(
        server: String,
        databaseName: String,
        username: String?,
        password: String?,
        targetFile: String,
        timeoutMinutes: Int = 30,
        onProgress: (String) -> Unit = {}
    ): ExecutionResult {
        val sqlPackagePath = getSqlPackagePath() 
            ?: return ExecutionResult(false, "", "SqlPackage no encontrado", -1)
        
        val command = buildList {
            add(sqlPackagePath)
            add("/Action:Export")
            add("/SourceServerName:$server")
            add("/SourceDatabaseName:$databaseName")
            add("/TargetFile:$targetFile")
            
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                add("/SourceUser:$username")
                add("/SourcePassword:$password")
            } else {
                // Windows Authentication - no necesita parámetros adicionales
            }
            
            add("/SourceTrustServerCertificate:True")
            add("/p:CommandTimeout=120")
            add("/p:VerifyExtraction=true")
        }
        
        return executeCommand(command, timeoutMinutes, onProgress)
    }
    
    fun importBacpac(
        server: String,
        databaseName: String,
        username: String?,
        password: String?,
        sourceFile: String,
        timeoutMinutes: Int = 60,
        onProgress: (String) -> Unit = {}
    ): ExecutionResult {
        val sqlPackagePath = getSqlPackagePath()
            ?: return ExecutionResult(false, "", "SqlPackage no encontrado", -1)
        
        val command = buildList {
            add(sqlPackagePath)
            add("/Action:Import")
            add("/TargetServerName:$server")
            add("/TargetDatabaseName:$databaseName")
            add("/SourceFile:$sourceFile")
            
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                add("/TargetUser:$username")
                add("/TargetPassword:$password")
            } else {
                // Windows Authentication - no necesita parámetros adicionales
            }
            
            add("/TargetTrustServerCertificate:True")
            add("/p:CommandTimeout=300")
        }
        
        return executeCommand(command, timeoutMinutes, onProgress)
    }
    
    private fun executeCommand(
        command: List<String>,
        timeoutMinutes: Int,
        onProgress: (String) -> Unit
    ): ExecutionResult {
        log.info("Ejecutando: ${command.joinToString(" ")}")
        
        return try {
            val processBuilder = ProcessBuilder(command)
                .redirectErrorStream(false)
            
            val process = processBuilder.start()
            
            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()
            
            // Leer stdout en un thread separado
            val outputThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        outputBuilder.appendLine(line)
                        onProgress(line)
                        log.info("SqlPackage: $line")
                    }
                }
            }
            
            // Leer stderr en un thread separado
            val errorThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        errorBuilder.appendLine(line)
                        log.warn("SqlPackage Error: $line")
                    }
                }
            }
            
            outputThread.start()
            errorThread.start()
            
            val completed = process.waitFor(timeoutMinutes.toLong(), TimeUnit.MINUTES)
            
            if (!completed) {
                process.destroyForcibly()
                return ExecutionResult(
                    success = false,
                    output = outputBuilder.toString(),
                    errorOutput = "La operación excedió el tiempo límite de $timeoutMinutes minutos",
                    exitCode = -1
                )
            }
            
            outputThread.join(5000)
            errorThread.join(5000)
            
            val exitCode = process.exitValue()
            
            ExecutionResult(
                success = exitCode == 0,
                output = outputBuilder.toString(),
                errorOutput = errorBuilder.toString(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            log.error("Error ejecutando SqlPackage", e)
            ExecutionResult(
                success = false,
                output = "",
                errorOutput = e.message ?: "Error desconocido",
                exitCode = -1
            )
        }
    }
    
    private fun getSqlPackagePath(): String? {
        val settings = BacpacSettings.getInstance()
        
        // Primero verificar si hay una ruta configurada manualmente
        settings.state.sqlPackagePath?.let { path ->
            if (File(path).exists()) {
                return path
            }
        }
        
        // Si no, usar el detector
        return SqlPackageDetector.getInstance().findSqlPackagePath()
    }
}
