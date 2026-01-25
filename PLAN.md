# SQL Server Bacpac Manager - Plan de Desarrollo

Plugin para JetBrains DataGrip que permite importar y exportar archivos .bacpac de bases de datos SQL Server.

## Información del Proyecto

| Campo | Valor |
|-------|-------|
| **Nombre** | SQL Server Bacpac Manager |
| **ID del plugin** | `me.carandev.bacpac` |
| **Lenguaje** | Kotlin |
| **Build System** | Gradle (Kotlin DSL) |
| **Target IDE** | DataGrip 2024.1+ |
| **Licencia** | Gratuito |
| **Idioma UI** | Español |

---

## Estructura del Proyecto

```
sql-server-bacpac-manager/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── PLAN.md
└── src/main/
    ├── kotlin/me/carandev/bacpac/
    │   ├── actions/
    │   │   ├── ExportBacpacAction.kt
    │   │   └── ImportBacpacAction.kt
    │   ├── services/
    │   │   ├── SqlPackageService.kt
    │   │   └── SqlPackageDetector.kt
    │   ├── ui/
    │   │   ├── ExportBacpacDialog.kt
    │   │   ├── ImportBacpacDialog.kt
    │   │   └── SqlPackageNotFoundDialog.kt
    │   └── settings/
    │       └── BacpacSettings.kt
    └── resources/META-INF/
        └── plugin.xml
```

---

## Componentes Implementados

### 1. Configuración del Proyecto

#### `settings.gradle.kts`
- Define el nombre del proyecto: `sql-server-bacpac-manager`

#### `build.gradle.kts`
- Plugin: `org.jetbrains.intellij.platform` versión 2.3.0
- Kotlin JVM 1.9.25
- Target: DataGrip 2024.1
- Dependencia: `com.intellij.database` (bundled plugin)
- JVM Target: 17

#### `gradle.properties`
- Versiones de Kotlin, IntelliJ Platform Plugin
- Grupo: `me.carandev.bacpac`
- Versión: `1.0.0`

---

### 2. plugin.xml

Define:
- ID: `me.carandev.bacpac`
- Nombre: "SQL Server Bacpac Manager"
- Dependencias: `com.intellij.modules.platform`, `com.intellij.database`
- Grupo de acciones "Bacpac" en `DatabaseViewPopupMenu`
- Servicio de aplicación: `BacpacSettings`
- Grupo de notificaciones: `Bacpac Notifications`

---

### 3. Acciones

#### `ExportBacpacAction.kt`
- Visible solo en conexiones SQL Server
- Verifica disponibilidad de SqlPackage
- Obtiene connection string de DataGrip
- Solicita contraseña si no está guardada
- Muestra diálogo para elegir ubicación
- Ejecuta exportación en background con progreso
- Muestra notificación de resultado

#### `ImportBacpacAction.kt`
- Visible solo en conexiones SQL Server
- Verifica disponibilidad de SqlPackage
- Muestra diálogo para seleccionar archivo y nombre de BD
- Solicita confirmación antes de importar
- Ejecuta importación en background con progreso
- Muestra notificación de resultado

---

### 4. Servicios

#### `SqlPackageService.kt`
- Ejecuta `sqlpackage.exe` como proceso externo
- Métodos: `exportBacpac()`, `importBacpac()`
- Captura stdout/stderr en tiempo real
- Soporta timeout configurable
- Retorna `ExecutionResult` con estado, output y errores

#### `SqlPackageDetector.kt`
- Busca SqlPackage en PATH y ubicaciones conocidas
- Detecta si .NET SDK está instalado
- Método `installSqlPackage()` para instalación automática
- Soporta Windows, macOS y Linux

---

### 5. Interfaz de Usuario

#### `ExportBacpacDialog.kt`
- Campo para seleccionar ubicación del archivo
- Valida extensión .bacpac
- Recuerda último directorio usado

#### `ImportBacpacDialog.kt`
- Campo para seleccionar archivo .bacpac
- Campo para nombre de BD destino (auto-rellena desde nombre del archivo)
- Valida existencia del archivo y nombre de BD válido

#### `SqlPackageNotFoundDialog.kt`
- Muestra advertencia cuando SqlPackage no está instalado
- Botón "Instalar automáticamente" (si .NET SDK disponible)
- Campo para configurar ruta manualmente
- Links a documentación de instalación

---

### 6. Configuración Persistente

#### `BacpacSettings.kt`
- `PersistentStateComponent` para guardar:
  - `sqlPackagePath`: Ruta personalizada de SqlPackage
  - `lastExportDirectory`: Último directorio de exportación
  - `lastImportDirectory`: Último directorio de importación
  - `commandTimeout`: Timeout de comandos SQL

---

## Flujos de Usuario

### Exportar Base de Datos

```
Usuario: Click derecho en BD SQL Server
    ↓
Menú: "Bacpac" → "Exportar a .bacpac"
    ↓
¿SqlPackage instalado?
    NO → Diálogo de instalación
    SÍ → Continuar
    ↓
¿Contraseña guardada?
    NO → Pedir contraseña
    SÍ → Continuar
    ↓
Diálogo: Elegir ubicación archivo
    ↓
Exportar en background con progreso
    ↓
Notificación de resultado
```

### Importar .bacpac

```
Usuario: Click derecho en conexión SQL Server
    ↓
Menú: "Bacpac" → "Importar .bacpac"
    ↓
¿SqlPackage instalado? (igual que export)
    ↓
¿Contraseña guardada? (igual que export)
    ↓
Diálogo: Seleccionar archivo + nombre BD
    ↓
Confirmación del usuario
    ↓
Importar en background con progreso
    ↓
Notificación de resultado
```

---

## Textos de UI (Español)

| Contexto | Texto |
|----------|-------|
| Grupo menú | Bacpac |
| Acción exportar | Exportar a .bacpac |
| Acción importar | Importar .bacpac |
| Título diálogo exportar | Exportar base de datos a .bacpac |
| Título diálogo importar | Importar archivo .bacpac |
| Éxito exportar | Exportación completada |
| Éxito importar | Importación completada |
| Error | Error en la exportación/importación |
| SqlPackage no encontrado | SqlPackage no está instalado |
| Instalar auto | Instalar automáticamente |
| Contraseña requerida | Contraseña requerida |

---

## Requisitos del Usuario Final

1. **DataGrip 2024.1** o superior
2. **SqlPackage** instalado:
   - Automático: Requiere .NET SDK 6.0+
   - Manual: Descargar desde [aka.ms/sqlpackage](https://aka.ms/sqlpackage)
3. Conexión configurada a SQL Server o Azure SQL Database

---

## Comandos de Desarrollo

```bash
# Ejecutar DataGrip con el plugin (para desarrollo)
./gradlew runIde

# Construir plugin (genera ZIP)
./gradlew buildPlugin

# Verificar compatibilidad
./gradlew verifyPlugin

# Publicar en Marketplace
./gradlew publishPlugin
```

---

## Próximos Pasos

- [x] Crear estructura del proyecto
- [x] Configurar build.gradle.kts
- [x] Crear plugin.xml
- [x] Implementar SqlPackageService
- [x] Implementar SqlPackageDetector
- [x] Implementar BacpacSettings
- [x] Implementar diálogos de UI
- [x] Implementar ExportBacpacAction
- [x] Implementar ImportBacpacAction
- [ ] Probar en DataGrip
- [ ] Preparar para publicación en JetBrains Marketplace
