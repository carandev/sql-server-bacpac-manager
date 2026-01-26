package me.carandev.bacpac.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import me.carandev.bacpac.settings.BacpacSettings
import java.io.File
import javax.swing.JComponent

class ExportBacpacDialog(
    private val project: Project,
    private val databaseName: String
) : DialogWrapper(project) {
    
    private val fileField = TextFieldWithBrowseButton()
    
    val targetFile: String
        get() = fileField.text
    
    init {
        title = "Exportar base de datos a .bacpac"
        
        val settings = BacpacSettings.getInstance()
        val defaultDir = settings.state.lastExportDirectory 
            ?: System.getProperty("user.home")
        val defaultFile = File(defaultDir, "$databaseName.bacpac").absolutePath
        
        fileField.text = defaultFile
        
        // Crear descriptor para guardar archivo (no requiere que exista)
        val descriptor = FileChooserDescriptor(
            true,   // chooseFiles
            false,  // chooseFolders
            false,  // chooseJars
            false,  // chooseJarsAsFiles
            false,  // chooseJarContents
            false   // chooseMultiple
        ).withFileFilter { it.extension?.lowercase() == "bacpac" || it.isDirectory }
         .withTitle("Seleccionar ubicación")
         .withDescription("Selecciona dónde guardar el archivo .bacpac")
        
        fileField.addBrowseFolderListener(TextBrowseFolderListener(descriptor, project))
        
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row("Base de datos:") {
                label(databaseName)
            }
            row("Guardar como:") {
                cell(fileField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
            row {
                comment("Escribe el nombre del archivo directamente o usa el botón para navegar")
            }
        }.apply {
            preferredSize = java.awt.Dimension(500, 120)
        }
    }
    
    override fun doOKAction() {
        var filePath = targetFile.trim()
        
        if (filePath.isBlank()) {
            setErrorText("Debes especificar una ubicación para el archivo")
            return
        }
        
        // Agregar extensión si no la tiene
        if (!filePath.endsWith(".bacpac", ignoreCase = true)) {
            filePath = "$filePath.bacpac"
            fileField.text = filePath
        }
        
        val file = File(filePath)
        val parentDir = file.parentFile
        
        if (parentDir != null && !parentDir.exists()) {
            setErrorText("El directorio destino no existe: ${parentDir.absolutePath}")
            return
        }
        
        if (parentDir != null && !parentDir.canWrite()) {
            setErrorText("No tienes permisos de escritura en: ${parentDir.absolutePath}")
            return
        }
        
        // Guardar el directorio usado
        BacpacSettings.getInstance().state.lastExportDirectory = parentDir?.absolutePath
        
        super.doOKAction()
    }
    
    override fun getPreferredFocusedComponent(): JComponent = fileField.textField
}
