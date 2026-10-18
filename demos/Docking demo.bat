@echo off
echo Starting Docking Demo...
cd /d "%~dp0.."
gradlew.bat :lwjgl3:runDockingDemo
pause
