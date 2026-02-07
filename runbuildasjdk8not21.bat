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

exit /b %ERRORLEVEL%
