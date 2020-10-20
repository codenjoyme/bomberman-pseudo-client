call 0-settings.bat

%GIT% status

echo off
echo [44;93m
echo        +-------------------------------------------------------------------------+        
echo        !                 Now we are building your sources...                     !        
echo        +-------------------------------------------------------------------------+        
echo [0m
echo on
IF "%DEBUG%"=="true" ( 
    pause >nul
)

%GIT% clean -fx
%GIT% reset --hard
%GIT% fetch --all
%GIT% pull --recurse-submodules origin
%GIT% submodule update --remote --init
%GIT% checkout ${REVISION}
%GIT% fetch
%GIT% status

cd %GAMES_HOME%
call %MVNW% clean install -N -DskipTests=%SKIP_TESTS%

cd %ENGINE_HOME%
call %MVNW% clean install -DskipTests=%SKIP_TESTS%

cd %GAMES_HOME%\%GAME_TO_RUN%
call %MVNW% clean install -DskipTests=%SKIP_TESTS%

echo Press any key to exit
pause >nul

cd %ROOT%