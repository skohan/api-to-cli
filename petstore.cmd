@echo off
rem Launcher for the generated CLI. Builds the shaded jar on first use.
rem Usage: petstore <command> [options]   e.g.  petstore login -u -p
setlocal
set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%petstore-cli\target\petstore-cli-1.0.0-SNAPSHOT.jar"

if not exist "%JAR%" (
  echo CLI jar not found - building ^(first run only^)... 1>&2
  call mvn -q -f "%SCRIPT_DIR%pom.xml" clean install -DskipTests
  if errorlevel 1 exit /b 1
)

java -jar "%JAR%" %*
exit /b %errorlevel%
