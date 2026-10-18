@echo off
echo Starting Orbit Demo...
cd /d "%~dp0"
gradlew.bat :lwjgl3:runOrbitDemo
pause
