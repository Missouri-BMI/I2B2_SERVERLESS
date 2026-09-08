REM 
REM from http://www.microsoft.com/resources/documentation/windows/xp/all/proddocs/en-us/batch.mspx?mfr=true

@echo off

@set CONFIG_CP=.
@set LIB_DIR=%CONFIG_CP%\lib

for /F %%G in ('dir /b %CONFIG_CP%\lib') do (call :append %%G)

@set CONFIG_DIR=conf

call :append %CONFIG_DIR%

REM @echo %CONFIG_CP%

GOTO :eof

@REM appending must be done in procedure call otherwise it won't append.
:append
            @set CONFIG_CP=%CONFIG_CP%;%LIB_DIR%\%1
            GOTO :eof
