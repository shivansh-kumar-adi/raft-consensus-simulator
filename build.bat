@echo off
rem Compiles the whole project into .\out  (needs a JDK: javac)
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java test\*.java > sources.txt
javac -d out @sources.txt
del sources.txt
echo Build OK  -^>  .\out
