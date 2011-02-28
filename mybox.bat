
@echo off

REM - This script is for running mybox in Windows

set CWD=%CD%
set JAVA="C:\Program Files\Java\jre6\bin\java"
set BASECOMMAND=%JAVA% -cp dist/mybox.jar net.mybox.mybox

if "%1"=="setup" %BASECOMMAND%.ClientSetup & goto :EOF
if "%1"=="client"  %BASECOMMAND%.Client & goto :EOF
if "%1"=="awt"  %BASECOMMAND%.ClientGUI & goto :EOF

%BASECOMMAND%.ClientGUI

