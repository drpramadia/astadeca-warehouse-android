@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
setlocal

set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set GRADLE_USER_HOME=%USERPROFILE%\.gradle

java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found. Please install JDK 17+.
    exit /b 1
)

"%JAVA_HOME%\bin\java.exe" -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: JAVA_HOME not set or invalid. Set JAVA_HOME to JDK 17+.
    exit /b 1
)

"%JAVA_HOME%\bin\java.exe" ^
  -Dorg.gradle.appname=%APP_BASE_NAME% ^
  -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" ^
  org.gradle.wrapper.GradleWrapperMain %*

if errorlevel 1 exit /b 1
