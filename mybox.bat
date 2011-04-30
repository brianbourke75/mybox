
@echo off

REM - This script is for running mybox in Windows

set APPHOME=%~dp0
set JAVA="C:\Program Files\Java\jre6\bin\java"
set BASECOMMAND=%JAVA% -cp "%APPHOME%/dist/mybox.jar" net.mybox.mybox

%BASECOMMAND%.%*
