@echo off
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
javac --release 21 -encoding UTF-8 -d build\classes src\LocalInitializr.java
if errorlevel 1 exit /b 1
jar --create --file technotes-initializr-v4.jar --main-class LocalInitializr -C build\classes . -C resources .
if errorlevel 1 exit /b 1
echo Built technotes-initializr-v4.jar
