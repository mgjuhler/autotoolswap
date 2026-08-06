@echo off
rem Starts Minecraft 26.2 with the mod loaded (test world in run\)
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
call gradlew.bat runClient
pause
