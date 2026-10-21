@echo off
echo Starting Interior Demo...
cd /d "%~dp0.."
call gradlew.bat :lwjgl3:run
pause
