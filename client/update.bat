@echo off
rem ============================================================
rem  al Shabab - modpack installer / updater  (Windows)
rem
rem  Works with EVERY launcher: TLauncher, the official Minecraft
rem  Launcher, Prism, CurseForge App, Modrinth App.
rem
rem  Run it any time. It installs the pack the first time and
rem  updates you to the server's exact version every time after.
rem
rem  Usage:
rem    update.bat                      (uses %APPDATA%\.minecraft)
rem    update.bat "D:\path\to\folder"  (any other game folder)
rem
rem  Requirements: Java 17+, and the Forge 1.20.1 profile/instance
rem  already created once in your launcher.
rem ============================================================

setlocal
set "PACK_URL=https://36a5.github.io/Modpacks/pack.toml"
set "GAME_DIR=%APPDATA%\.minecraft"

if not "%~1"=="" set "GAME_DIR=%~1"

echo.
echo [al-shabab] Game directory: %GAME_DIR%

where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo ERROR: Java is not installed, or not on your PATH.
    echo        Install Java 17 from https://adoptium.net and run this again.
    pause
    exit /b 1
)

if not exist "%GAME_DIR%" (
    echo.
    echo ERROR: "%GAME_DIR%" does not exist.
    echo        Launch your launcher once with Forge 1.20.1 so it creates the folder,
    echo        or point this script at the right folder:
    echo            update.bat "D:\path\to\your\.minecraft"
    pause
    exit /b 1
)

cd /d "%GAME_DIR%"

if not exist packwiz-installer-bootstrap.jar (
    echo [al-shabab] Downloading the updater...
    curl -fsSL -o packwiz-installer-bootstrap.jar https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar
    if errorlevel 1 (
        echo.
        echo ERROR: could not download the updater. Check your internet connection.
        pause
        exit /b 1
    )
)

echo [al-shabab] Installing / updating the pack. This takes a few minutes the first time...
java -jar packwiz-installer-bootstrap.jar -g -s client "%PACK_URL%"
if errorlevel 1 (
    echo.
    echo ERROR: the update failed. Screenshot this window and send it to the server admin.
    pause
    exit /b 1
)

echo.
echo [al-shabab] Done. Open your launcher, pick the Forge 1.20.1 profile, and play.
echo.
pause
