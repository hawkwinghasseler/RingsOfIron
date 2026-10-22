@echo off
echo Starting the Orbit scenario...
cd /d "%~dp0.."
gradlew.bat :lwjgl3:runGame -Pscenario=orbit
pause
