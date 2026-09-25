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
            "/opt/homebrew/bin/sqlpackage",
            "/usr/local/bin/sqlpackage",
            "/opt/sqlpackage/sqlpackage"
        ).filterNotNull()

        private val DOTNET_WINDOWS_PATHS = listOf(
            System.getenv("ProgramFiles")?.let { "$it\\dotnet\\dotnet.exe" },
            System.getenv("ProgramFiles(x86)")?.let { "$it\\dotnet\\dotnet.exe" },
            System.getenv("USERPROFILE")?.let { "$it\\.dotnet\\dotnet.exe" }
        ).filterNotNull()

        private val DOTNET_UNIX_PATHS = listOf(
            "/usr/local/share/dotnet/dotnet",
            "/opt/homebrew/bin/dotnet",
            "/usr/local/bin/dotnet",
            System.getenv("HOME")?.let { "$it/.dotnet/dotnet" }
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
    
    fun findDotNetExecutable(): String? {
        // Primero buscar si dotnet responde directamente desde PATH
        try {
            val command = if (isWindows()) listOf("cmd", "/c", "dotnet", "--version")
                          else listOf("dotnet", "--version")
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (completed && process.exitValue() == 0) {
                return if (isWindows()) "dotnet.exe" else "dotnet"
            }
        } catch (e: Exception) {
            log.debug("dotnet no encontrado en PATH: ${e.message}")
        }

        // Buscar en ubicaciones conocidas del sistema (útil para macOS donde DataGrip no hereda PATH de la shell)
        val candidatePaths = if (isWindows()) DOTNET_WINDOWS_PATHS else DOTNET_UNIX_PATHS
        for (path in candidatePaths) {
            val file = File(path)
            if (file.exists() && (isWindows() || file.canExecute())) {
                try {
                    val process = ProcessBuilder(listOf(path, "--version"))
                        .redirectErrorStream(true)
                        .start()
                    val completed = process.waitFor(5, TimeUnit.SECONDS)
                    if (completed && process.exitValue() == 0) {
                        log.info("dotnet SDK encontrado en ruta conocida: $path")
                        return path
                    }
                } catch (e: Exception) {
                    log.debug("Error probando dotnet en $path: ${e.message}")
                }
            }
        }
        
        return null
    }

    fun isDotNetSdkInstalled(): Boolean = findDotNetExecutable() != null
    
    data class InstallResult(
        val success: Boolean,
        val message: String
    )
    
    fun installSqlPackage(onProgress: (String) -> Unit = {}): InstallResult {
        val dotnetExecutable = findDotNetExecutable()
        if (dotnetExecutable == null) {
            return InstallResult(
                success = false,
                message = ".NET SDK no está instalado. Por favor, instálalo desde https://dotnet.microsoft.com/download"
            )
        }
        
        return try {
            onProgress("Instalando SqlPackage...")
            
            val command = if (isWindows() && !dotnetExecutable.contains("\\")) {
                listOf("cmd", "/c", "dotnet", "tool", "install", "-g", "microsoft.sqlpackage")
            } else {
                listOf(dotnetExecutable, "tool", "install", "-g", "microsoft.sqlpackage")
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
