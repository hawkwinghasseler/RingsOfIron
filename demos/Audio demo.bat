@echo off
echo Starting Spatial Audio Demo...
cd /d "%~dp0.."
gradlew.bat :lwjgl3:runAudioDemo
pause
