@echo off
echo Starting Ship Editor...
cd /d "%~dp0.."
gradlew.bat :lwjgl3:runShipEditor
pause
