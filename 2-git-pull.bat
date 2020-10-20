call 0-settings.bat

echo off
echo [44;93m
echo        +-------------------------------------------------------------------------+        
echo        !                 Now we are pulling your sources...                      !        
echo        +-------------------------------------------------------------------------+        
echo [0m
echo on
IF "%DEBUG%"=="true" ( 
    pause >nul
)

%GIT% status
%GIT% clean -fx
%GIT% reset --hard
%GIT% fetch --all
%GIT% pull --recurse-submodules origin
%GIT% submodule update --remote --init
%GIT% checkout %REVISION%
%GIT% fetch
%GIT% status

echo Press any key to exit
pause >nul

cd %ROOT%