package me.carandev.bacpac.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import me.carandev.bacpac.settings.BacpacSettings
import java.io.File
import javax.swing.JComponent
import javax.swing.JTextField

class ImportBacpacDialog(
    private val project: Project
) : DialogWrapper(project) {
    
    private val fileField = TextFieldWithBrowseButton()
    private val databaseNameField = JTextField()
    
    val sourceFile: String
        get() = fileField.text
    
    val databaseName: String
        get() = databaseNameField.text.trim()
    
    init {
        title = "Importar archivo .bacpac"
        
        val settings = BacpacSettings.getInstance()
        val defaultDir = settings.state.lastImportDirectory 
            ?: System.getProperty("user.home")
        
        fileField.text = defaultDir
        fileField.addBrowseFolderListener(
            "Seleccionar archivo .bacpac",
            "Selecciona el archivo .bacpac a importar",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("bacpac")
        )
        
        // Actualizar nombre de BD cuando se selecciona un archivo
        fileField.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateDatabaseName()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateDatabaseName()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateDatabaseName()
        })
        
        init()
    }
    
    private fun updateDatabaseName() {
        val file = File(fileField.text)
        if (file.exists() && file.extension.equals("bacpac", ignoreCase = true)) {
            if (databaseNameField.text.isBlank()) {
                databaseNameField.text = file.nameWithoutExtension
            }
        }
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row("Archivo .bacpac:") {
                cell(fileField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
            row("Nombre de la base de datos:") {
                cell(databaseNameField)
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("Nombre de la nueva base de datos a crear")
            }
        }.apply {
            preferredSize = java.awt.Dimension(500, 120)
        }
    }
    
    override fun doOKAction() {
        if (sourceFile.isBlank()) {
            setErrorText("Debes seleccionar un archivo .bacpac")
            return
        }
        
        val file = File(sourceFile)
        if (!file.exists()) {
            setErrorText("El archivo seleccionado no existe")
            return
        }
        
        if (!file.extension.equals("bacpac", ignoreCase = true)) {
            setErrorText("El archivo debe tener extensión .bacpac")
            return
        }
        
        if (databaseName.isBlank()) {
            setErrorText("Debes especificar un nombre para la base de datos")
            return
        }
        
        if (!isValidDatabaseName(databaseName)) {
            setErrorText("El nombre de la base de datos contiene caracteres no válidos")
            return
        }
        
        // Guardar el directorio usado
        BacpacSettings.getInstance().state.lastImportDirectory = file.parent
        
        super.doOKAction()
    }
    
    private fun isValidDatabaseName(name: String): Boolean {
        // SQL Server database naming rules (básico)
        val invalidChars = listOf('\\', '/', ':', '*', '?', '"', '<', '>', '|', '[', ']')
        return name.none { it in invalidChars } && name.length <= 128
    }
    
    override fun getPreferredFocusedComponent(): JComponent = fileField.textField
}
