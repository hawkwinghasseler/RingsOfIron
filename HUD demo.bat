@echo off
echo Starting HUD Demo...
cd /d "%~dp0"
gradlew.bat :lwjgl3:runHudDemo
pause
