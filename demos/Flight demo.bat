@echo off
echo Starting Flight Demo...
cd /d "%~dp0.."
call gradlew.bat :lwjgl3:runFlightDemo
pause
