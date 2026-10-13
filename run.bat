@echo off
rem Usage: run.bat [options]     e.g.  run.bat   |  run.bat --cli  |  run.bat --demo
cd /d "%~dp0"
if not exist out call build.bat
java -cp "out;lib\*" raft.Main %*
