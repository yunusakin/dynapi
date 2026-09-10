@echo off
set SCRIPT_DIR=%~dp0
if exist "%SCRIPT_DIR%..\cli\bin\spectra.js" node "%SCRIPT_DIR%..\cli\bin\spectra.js" %*
