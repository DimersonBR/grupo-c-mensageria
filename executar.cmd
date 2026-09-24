@echo off
setlocal
rem Encaminha os argumentos para o script principal sem depender da politica global do PowerShell.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0executar.ps1" %*
exit /b %ERRORLEVEL%
