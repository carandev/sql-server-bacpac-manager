package me.carandev.bacpac.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import me.carandev.bacpac.services.SqlPackageDetector
import me.carandev.bacpac.settings.BacpacSettings
import java.awt.BorderLayout
import java.io.File
import javax.swing.*

class SqlPackageNotFoundDialog(
    private val project: Project?
) : DialogWrapper(project) {
    
    private val detector = SqlPackageDetector.getInstance()
    private val isDotNetInstalled = detector.isDotNetSdkInstalled()
    private val manualPathField = TextFieldWithBrowseButton()
    
    enum class Result {
        CANCELLED,
        INSTALLED,
        MANUAL_PATH_SET
    }
    
    var result: Result = Result.CANCELLED
        private set
    
    init {
        title = "SqlPackage no encontrado"
        setOKButtonText("Configurar ruta")
        setCancelButtonText("Cancelar")
        
        manualPathField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptor(true, false, false, false, false, false)
                    .withTitle("Seleccionar SqlPackage")
                    .withDescription("Selecciona el ejecutable de SqlPackage"),
                project
            )
        )
        
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, JBUI.scale(10)))
        
        val messagePanel = panel {
            row {
                icon(Messages.getWarningIcon())
                label("<html><b>SqlPackage no está instalado</b></html>")
            }
            row {
                text("""
                    SqlPackage es necesario para trabajar con archivos .bacpac.
                    Puedes instalarlo automáticamente o configurar la ruta manualmente.
                """.trimIndent())
            }
            
            separator()
            
            if (isDotNetInstalled) {
                row {
                    button("Instalar automáticamente") {
                        installAutomatically()
                    }.comment("Ejecutará: dotnet tool install -g microsoft.sqlpackage")
                }
            } else {
                row {
                    text("""
                        <html><b>Instalación automática no disponible</b><br>
                        .NET SDK no está instalado. Puedes:<br>
                        - Instalar .NET SDK desde <a href="https://dotnet.microsoft.com/download">dotnet.microsoft.com</a><br>
                        - Descargar SqlPackage manualmente desde <a href="https://aka.ms/sqlpackage">aka.ms/sqlpackage</a></html>
                    """.trimIndent())
                }
            }
            
            separator()
            
            row {
                label("O especifica la ruta manualmente:")
            }
            row("Ruta de SqlPackage:") {
                cell(manualPathField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
        }
        
        mainPanel.add(messagePanel, BorderLayout.CENTER)
        mainPanel.preferredSize = java.awt.Dimension(500, 280)
        
        return mainPanel
    }
    
    private fun installAutomatically() {
        val progressDialog = object : DialogWrapper(project, false) {
            private val textArea = JTextArea(10, 50).apply {
                isEditable = false
                font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
            }
            
            init {
                title = "Instalando SqlPackage"
                setOKButtonText("Cerrar")
                isOKActionEnabled = false
                init()
            }
            
            override fun createCenterPanel(): JComponent {
                return JScrollPane(textArea)
            }
            
            fun appendText(text: String) {
                SwingUtilities.invokeLater {
                    textArea.append(text + "\n")
                    textArea.caretPosition = textArea.document.length
                }
            }
            
            fun enableClose() {
                SwingUtilities.invokeLater {
                    isOKActionEnabled = true
                }
            }
        }
        
        Thread {
            val installResult = detector.installSqlPackage { progress ->
                progressDialog.appendText(progress)
            }
            
            progressDialog.appendText("\n" + if (installResult.success) {
                "Instalación completada exitosamente."
            } else {
                "Error: ${installResult.message}"
            })
            
            progressDialog.enableClose()
            
            if (installResult.success) {
                SwingUtilities.invokeLater {
                    result = Result.INSTALLED
                }
            }
        }.start()
        
        progressDialog.show()
        
        if (result == Result.INSTALLED) {
            close(OK_EXIT_CODE)
        }
    }
    
    override fun doOKAction() {
        val path = manualPathField.text.trim()
        
        if (path.isBlank()) {
            setErrorText("Debes especificar la ruta de SqlPackage")
            return
        }
        
        val file = File(path)
        if (!file.exists()) {
            setErrorText("El archivo especificado no existe")
            return
        }
        
        if (!file.canExecute() && !path.endsWith(".exe", ignoreCase = true)) {
            setErrorText("El archivo no parece ser ejecutable")
            return
        }
        
        // Guardar la ruta en configuración
        BacpacSettings.getInstance().state.sqlPackagePath = path
        result = Result.MANUAL_PATH_SET
        
        super.doOKAction()
    }
}
