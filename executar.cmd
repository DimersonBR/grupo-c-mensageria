@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0executar.ps1" %*
exit /b %ERRORLEVEL%
