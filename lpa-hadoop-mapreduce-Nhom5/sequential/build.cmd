@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME (
  echo JAVA_HOME is missing.
  exit /b 1
)
if not exist build\classes mkdir build\classes
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -d build\classes src\lpa\sequential\SequentialLPA.java
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\jar.exe" cfe LPA_Sequential.jar lpa.sequential.SequentialLPA -C build\classes .
if errorlevel 1 exit /b 1
echo Built LPA_Sequential.jar
