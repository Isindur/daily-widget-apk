@echo off
setlocal

set APP_NAME=Gradle

set DEFAULT_JVM_OPTS=

set CLASSPATH=%~dp0\gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
  echo ERROR: Gradle wrapper JAR not found.
  exit /b 1
)

java %DEFAULT_JVM_OPTS% -jar "%CLASSPATH%" %*