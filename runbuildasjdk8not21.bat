@echo off
setlocal

set "JDK8_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot"
if not exist "%JDK8_HOME%\bin\java.exe" (
  echo [ERROR] JDK 8 not found: "%JDK8_HOME%"
  exit /b 1
)

set "JAVA_HOME=%JDK8_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [INFO] Using JAVA_HOME=%JAVA_HOME%
java -version

if "%~1"=="" (
  call gradlew.bat --no-daemon clean assemble -x test
) else (
  call gradlew.bat %*
)

set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" (
  echo [ERROR] Build failed, deploy watcher not started.
  exit /b %BUILD_EXIT%
)

set "WATCHER_PS=%~dp0tools\deploy-when-mc-closed.ps1"
set "WATCHER_LOG=%~dp0build\deploy-watcher.log"
if not exist "%WATCHER_PS%" (
  echo [WARN] Deploy watcher script not found: "%WATCHER_PS%"
  exit /b 0
)

echo [INFO] Starting deploy watcher (wait MC close -> copy jar)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"\"%WATCHER_PS%\"\" -LogFile \"\"%WATCHER_LOG%\"\"'"
echo [INFO] Deploy watcher started. Log: %WATCHER_LOG%
exit /b 0
