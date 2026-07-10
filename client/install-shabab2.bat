@echo off
rem ============================================================
rem  Shabab 2 - ONE-CLICK INSTALLER
rem
rem  Download this file, double-click it, answer one question
rem  (which launcher you use). It installs the "Shabab 2" pack:
rem  Java (if needed), Forge, all mods, configs and shaderpacks
rem  into the right place for your launcher - no pack menu, it
rem  always installs Shabab 2.
rem
rem  Run it again any time to update to the server's version.
rem ============================================================
title Shabab 2 - Installer

set "SCRIPT_URL=https://raw.githubusercontent.com/36a5/Modpacks/master/client/install.ps1"
set "LOCAL_PS=%TEMP%\shabab2-install.ps1"

echo.
echo   Downloading the installer...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; try { Invoke-WebRequest '%SCRIPT_URL%' -OutFile '%LOCAL_PS%' } catch { Write-Host 'Could not reach GitHub. Check your internet connection.' -ForegroundColor Red; exit 1 }"
if errorlevel 1 goto :failed

rem Always install the Shabab 2 pack (-Pack al-shabab-2), skipping the pack menu.
rem A game folder dragged onto this file is passed through as -GameDir.
if "%~1"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%LOCAL_PS%" -Pack al-shabab-2
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%LOCAL_PS%" -Pack al-shabab-2 -GameDir "%~1"
)
if errorlevel 1 goto :failed

del "%LOCAL_PS%" >nul 2>nul
echo.
pause
exit /b 0

:failed
echo.
echo   Something went wrong. Screenshot this whole window and send it to the admin.
echo.
pause
exit /b 1
