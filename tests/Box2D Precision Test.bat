@echo off
echo Starting Box2D Precision Test...
cd /d "%~dp0.."
call gradlew.bat :lwjgl3:runPhysicsTest
pause
