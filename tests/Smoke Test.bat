@echo off
echo Starting Smoke Test (boots the game and every demo for 300 frames)...
cd /d "%~dp0.."
call gradlew.bat :lwjgl3:smokeTest --continue
pause
