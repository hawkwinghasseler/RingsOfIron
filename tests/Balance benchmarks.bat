@echo off
echo Starting the balance benchmarks (the careers take up to an hour)...
echo The tables go to core\build\playtest; the checklists are in documentation\playtest.
cd /d "%~dp0.."
call gradlew.bat :core:playtestBenchmark
pause
