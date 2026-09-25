# SQL Server Bacpac Manager

[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/me.carandev.bacpac.svg)](https://plugins.jetbrains.com/plugin/XXXXX-sql-server-bacpac-manager)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/me.carandev.bacpac.svg)](https://plugins.jetbrains.com/plugin/XXXXX-sql-server-bacpac-manager)

Import and export SQL Server .bacpac files directly from DataGrip's context menu.

A `.bacpac` file is a portable backup format for SQL Server and Azure SQL databases that includes both schema and data.

## Features

- **Export to .bacpac** - Right-click on any SQL Server database to export it
- **Import from .bacpac** - Right-click on a SQL Server connection to import a .bacpac file as a new database
- **Automatic SqlPackage detection** - Finds SqlPackage in PATH or common installation locations
- **One-click SqlPackage installation** - Install SqlPackage automatically via .NET SDK if not found
- **Background processing** - Export/import operations run in background with progress indication

## Requirements

- **DataGrip 2024.1** or later (also works with IntelliJ IDEA Ultimate, Rider, or any JetBrains IDE with Database Tools)
- **SqlPackage CLI tool** - Can be installed automatically if you have .NET SDK, or manually from [Microsoft's documentation](https://learn.microsoft.com/en-us/sql/tools/sqlpackage/sqlpackage-download)
- A configured SQL Server or Azure SQL Database connection

## Installation

### From JetBrains Marketplace (Recommended)

1. Open DataGrip
2. Go to **Settings** → **Plugins** → **Marketplace**
3. Search for "SQL Server Bacpac Manager"
4. Click **Install**
5. Restart DataGrip

### Manual Installation

1. Download the latest release `.zip` file from [Releases](https://github.com/carandev/sql-server-bacpac-manager/releases)
2. Open DataGrip
3. Go to **Settings** → **Plugins** → ⚙️ → **Install Plugin from Disk**
4. Select the downloaded `.zip` file
5. Restart DataGrip

## Usage

### Export a Database

1. Connect to your SQL Server in DataGrip
2. In the Database Explorer, **right-click on a database**
3. Select **Bacpac** → **Export to .bacpac**
4. Choose the destination file
5. Enter your password if prompted
6. Wait for the export to complete

![Export Demo](docs/export-demo.gif)

### Import a .bacpac File

1. Connect to your SQL Server in DataGrip
2. In the Database Explorer, **right-click on the server/connection** (not a database)
3. Select **Bacpac** → **Import .bacpac**
4. Select the `.bacpac` file to import
5. Enter the name for the new database
6. Enter your password if prompted
7. Wait for the import to complete

![Import Demo](docs/import-demo.gif)

## SqlPackage Installation

This plugin requires the `SqlPackage` CLI tool from Microsoft. If it's not detected, the plugin will offer to install it automatically.

### Automatic Installation (Requires .NET SDK)

If you have .NET SDK installed, the plugin can install SqlPackage for you:

```bash
dotnet tool install -g microsoft.sqlpackage
```

### macOS Installation & Configuration

1. Install SqlPackage globally via .NET tool:
   ```bash
   dotnet tool install -g microsoft.sqlpackage
   ```

2. Default executable location on macOS:
   ```text
   /Users/[username]/.dotnet/tools/sqlpackage
   ```

3. **DataGrip Path Configuration**: Because macOS GUI applications launched from Finder or Spotlight do not inherit user shell profiles (`.zshrc` / `.bash_profile`), DataGrip may not find `sqlpackage` or `dotnet` in its environment PATH. When prompted by the plugin, choose **"Configurar ruta"** and set the path manually:
   ```text
   /Users/<your-username>/.dotnet/tools/sqlpackage
   ```

4. If installed via Homebrew or custom paths, the plugin also checks `/opt/homebrew/bin/sqlpackage` and `/usr/local/bin/sqlpackage`.

### Manual Installation

Download SqlPackage from Microsoft:
- [Windows](https://aka.ms/sqlpackage-windows)
- [macOS](https://aka.ms/sqlpackage-macos)
- [Linux](https://aka.ms/sqlpackage-linux)

## Supported Authentication Methods

- **SQL Server Authentication** - Username and password
- **Windows Authentication** - Uses your Windows credentials (Windows only)

## Troubleshooting

### "SqlPackage not found"

1. Ensure SqlPackage is installed (see above).
2. On macOS, GUI apps may not inherit terminal `PATH`. Set the manual path in DataGrip to `/Users/<your-username>/.dotnet/tools/sqlpackage`.
3. The plugin searches standard locations (`~/.dotnet/tools`, `/opt/homebrew/bin`, `/usr/local/bin`) automatically.

### "Login failed"

- Verify your username and password
- Ensure the SQL Server allows the authentication method you're using
- Check that your user has the necessary permissions on the database

### Export/Import takes too long

- Large databases may take several minutes to export/import
- The operation runs in the background - you can continue using DataGrip
- Check the progress in the bottom status bar

## Building from Source

```bash
# Clone the repository
git clone https://github.com/carandev/sql-server-bacpac-manager.git
cd sql-server-bacpac-manager

# Build the plugin
./gradlew buildPlugin

# The plugin ZIP will be in build/distributions/
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

**Carlos Gomez** - [carandev.me](https://carandev.me)

---

Made with ❤️ for the DataGrip community
