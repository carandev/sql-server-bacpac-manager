package me.carandev.bacpac.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Service
class SqlPackageDetector {
    
    private val log = logger<SqlPackageDetector>()
    
    companion object {
        fun getInstance(): SqlPackageDetector = service()
        
        private val WINDOWS_PATHS = listOf(
            System.getenv("USERPROFILE")?.let { "$it\\.dotnet\\tools\\sqlpackage.exe" },
            "C:\\Program Files\\Microsoft SQL Server\\160\\DAC\\bin\\SqlPackage.exe",
            "C:\\Program Files\\Microsoft SQL Server\\150\\DAC\\bin\\SqlPackage.exe",
            "C:\\Program Files\\Microsoft SQL Server\\140\\DAC\\bin\\SqlPackage.exe",
            "C:\\Program Files (x86)\\Microsoft SQL Server\\160\\DAC\\bin\\SqlPackage.exe",
            "C:\\Program Files (x86)\\Microsoft SQL Server\\150\\DAC\\bin\\SqlPackage.exe",
            "C:\\Program Files (x86)\\Microsoft SQL Server\\140\\DAC\\bin\\SqlPackage.exe",
        ).filterNotNull()
        
        private val UNIX_PATHS = listOf(
            System.getenv("HOME")?.let { "$it/.dotnet/tools/sqlpackage" },
            "/usr/local/bin/sqlpackage",
            "/opt/sqlpackage/sqlpackage"
        ).filterNotNull()
    }
    
    fun findSqlPackagePath(): String? {
        // Primero buscar en PATH
        findInPath()?.let { return it }
        
        // Luego buscar en ubicaciones conocidas
        val paths = if (isWindows()) WINDOWS_PATHS else UNIX_PATHS
        
        for (path in paths) {
            if (File(path).exists()) {
                log.info("SqlPackage encontrado en: $path")
                return path
            }
        }
        
        log.warn("SqlPackage no encontrado en el sistema")
        return null
    }
    
    fun isInstalled(): Boolean = findSqlPackagePath() != null
    
    fun isDotNetSdkInstalled(): Boolean {
        return try {
            val command = if (isWindows()) listOf("cmd", "/c", "dotnet", "--version")
                          else listOf("dotnet", "--version")
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            val completed = process.waitFor(10, TimeUnit.SECONDS)
            completed && process.exitValue() == 0
        } catch (e: Exception) {
            log.info("dotnet SDK no está instalado: ${e.message}")
            false
        }
    }
    
    data class InstallResult(
        val success: Boolean,
        val message: String
    )
    
    fun installSqlPackage(onProgress: (String) -> Unit = {}): InstallResult {
        if (!isDotNetSdkInstalled()) {
            return InstallResult(
                success = false,
                message = ".NET SDK no está instalado. Por favor, instálalo desde https://dotnet.microsoft.com/download"
            )
        }
        
        return try {
            onProgress("Instalando SqlPackage...")
            
            val command = if (isWindows()) {
                listOf("cmd", "/c", "dotnet", "tool", "install", "-g", "microsoft.sqlpackage")
            } else {
                listOf("dotnet", "tool", "install", "-g", "microsoft.sqlpackage")
            }
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    output.appendLine(line)
                    onProgress(line)
                }
            }
            
            val completed = process.waitFor(5, TimeUnit.MINUTES)
            
            if (!completed) {
                process.destroyForcibly()
                return InstallResult(
                    success = false,
                    message = "La instalación excedió el tiempo límite"
                )
            }
            
            val exitCode = process.exitValue()
            
            if (exitCode == 0 || output.contains("already installed", ignoreCase = true)) {
                InstallResult(
                    success = true,
                    message = "SqlPackage instalado correctamente"
                )
            } else {
                InstallResult(
                    success = false,
                    message = "Error durante la instalación:\n$output"
                )
            }
        } catch (e: Exception) {
            log.error("Error instalando SqlPackage", e)
            InstallResult(
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }
    
    private fun findInPath(): String? {
        val executableName = if (isWindows()) "sqlpackage.exe" else "sqlpackage"
        
        return try {
            val command = if (isWindows()) {
                listOf("cmd", "/c", "where", "sqlpackage")
            } else {
                listOf("which", "sqlpackage")
            }
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            
            if (completed && process.exitValue() == 0 && output.isNotEmpty()) {
                val path = output.lines().first().trim()
                if (File(path).exists()) {
                    log.info("SqlPackage encontrado en PATH: $path")
                    return path
                }
            }
            null
        } catch (e: Exception) {
            log.debug("Error buscando en PATH: ${e.message}")
            null
        }
    }
    
    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }
}
