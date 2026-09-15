@echo off
setlocal
pushd "%~dp0"
if errorlevel 1 exit /b 1
for /d %%J in ("%~dp0.tools\jdk\*") do if exist "%%~fJ\bin\javac.exe" set "JAVA_HOME=%%~fJ"
set "MAVEN=mvn.cmd"
if exist "%~dp0.tools\apache-maven-3.9.11\bin\mvn.cmd" set "MAVEN=%~dp0.tools\apache-maven-3.9.11\bin\mvn.cmd"
set "APP_ARGS="
:parse
if "%~1"=="" goto run
if /i "%~1"=="-Api" goto api
if /i "%~1"=="--api" goto api
if /i "%~1"=="-Receber" goto receive
if /i "%~1"=="--receber" goto receive
if /i "%~1"=="-Confirmar" goto confirm
if /i "%~1"=="--confirmar" goto confirm
if /i "%~1"=="--help" goto help
echo Argumento invalido. Use -Receber e, opcionalmente, -Confirmar.
popd
exit /b 2
:receive
set "APP_ARGS=%APP_ARGS% --receber"
shift
goto parse
:api
set "APP_ARGS=%APP_ARGS% --api"
shift
goto parse
:confirm
set "APP_ARGS=%APP_ARGS% --confirmar"
shift
goto parse
:help
set "APP_ARGS=--help"
goto run
:run
call "%MAVEN%" -q "-Dmaven.repo.local=%~dp0.tools\m2" compile exec:java "-Dexec.args=%APP_ARGS%"
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%
