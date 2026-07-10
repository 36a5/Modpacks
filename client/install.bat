@echo off
rem ============================================================
rem  al Shabab - ONE-CLICK INSTALLER
rem
rem  Download this file, double-click it, answer one question.
rem
rem  It asks which launcher you use - CurseForge, Modrinth,
rem  TLauncher, or the official Minecraft Launcher - then
rem  installs Java (if you need it), Forge, all 207 mods, the
rem  configs and the shaderpacks into the right place for it.
rem
rem  Run it again any time to update to the server's version.
rem ============================================================
title al Shabab - Installer

set "SCRIPT_URL=https://raw.githubusercontent.com/36a5/Modpacks/master/client/install.ps1"
set "LOCAL_PS=%TEMP%\al-shabab-install.ps1"

echo.
echo   Downloading the installer...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; try { Invoke-WebRequest '%SCRIPT_URL%' -OutFile '%LOCAL_PS%' } catch { Write-Host 'Could not reach GitHub. Check your internet connection.' -ForegroundColor Red; exit 1 }"
if errorlevel 1 goto :failed

rem Pass through a game folder if one was dragged onto this file.
rem With no argument the script asks which launcher you use.
if "%~1"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%LOCAL_PS%"
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%LOCAL_PS%" -GameDir "%~1"
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
