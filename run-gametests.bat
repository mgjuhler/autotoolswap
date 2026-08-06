@echo off
rem Runs the automated in-game test suite (Fabric client gametests)
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
call gradlew.bat runClientGameTest
pause
