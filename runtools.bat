@if "%DEBUG%"=="" @echo off
:: Run one of the tools.
:: The first argument must be the name of the tools (e.g. mklanguages).
:: Any remaining arguments are forwarded to the tool's argv.

if "%OS%"=="Windows_NT" setlocal EnableDelayedExpansion

set TASK=%~1

set TOOL=false
if defined TASK if not "!TASK: =!"=="" if exist "tools\%TASK%\*" set TOOL=true

if "%TOOL%"=="false" (
    echo Unknown tool: '%TASK%'
    exit /b 1
)

set ARGS=%*
set ARGS=!ARGS:*%1=!
if "!ARGS:~0,1!"==" " set ARGS=!ARGS:~1!

call gradlew --quiet ":tools:%TASK%:installDist" && call "tools\%TASK%\build\install\%TASK%\bin\%TASK%" %ARGS%

if "%OS%"=="Windows_NT" endlocal
