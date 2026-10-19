@echo off
echo Starting the Rings of Iron first play...
cd /d "%~dp0.."
call gradlew.bat :lwjgl3:runFirstPlay
pause
