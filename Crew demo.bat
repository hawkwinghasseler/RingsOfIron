@echo off
echo Starting Crew Demo...
cd /d "%~dp0"
gradlew.bat :lwjgl3:runCrewDemo
pause
