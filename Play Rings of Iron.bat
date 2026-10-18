@echo off
echo Starting Rings of Iron...
cd /d "%~dp0"
call gradlew.bat :lwjgl3:runGame
pause
