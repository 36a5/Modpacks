@echo off
rem al Shabab pack installer/updater for TLauncher players.
rem Run this any time to install the pack or pull the latest update.
rem Requirements: Java 17+ installed, Forge 1.20.1 profile created in TLauncher once.

setlocal
set "PACK_URL=https://REPLACE-ME.github.io/al-shabab/pack.toml"
set "GAME_DIR=%APPDATA%\.minecraft"

if not "%~1"=="" set "GAME_DIR=%~1"

echo [al-shabab] Game directory: %GAME_DIR%
if not exist "%GAME_DIR%" (
    echo ERROR: %GAME_DIR% not found. Launch TLauncher once first, or pass your game folder:
    echo        update.bat "D:\path\to\.minecraft"
    exit /b 1
)

cd /d "%GAME_DIR%"

if not exist packwiz-installer-bootstrap.jar (
    echo [al-shabab] Downloading updater...
    curl -fsSL -o packwiz-installer-bootstrap.jar https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar
    if errorlevel 1 (
        echo ERROR: download failed. Check your internet connection.
        exit /b 1
    )
)

echo [al-shabab] Installing / updating the pack...
java -jar packwiz-installer-bootstrap.jar -g -s client "%PACK_URL%"
if errorlevel 1 (
    echo ERROR: update failed. Send a screenshot of this window to the server admin.
    exit /b 1
)

echo.
echo [al-shabab] Done! Open TLauncher, pick the Forge 1.20.1 profile, and play.
pause
