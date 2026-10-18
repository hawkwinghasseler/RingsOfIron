@echo off
echo Starting Flight Physics Unit Tests...
cd /d "%~dp0.."
call gradlew.bat :physics-exterior:test
pause
