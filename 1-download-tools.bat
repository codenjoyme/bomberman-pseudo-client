call 0-settings.bat

echo off
echo [44;93m
echo        +-------------------------------------+        
echo        !           Installing Git            !        
echo        +-------------------------------------+        
echo [0m
echo on

rd /S /Q %GIT_HOME%
mkdir %GIT_HOME%
powershell -command "& { set-executionpolicy remotesigned -s currentuser; [System.Net.ServicePointManager]::SecurityProtocol = 3072 -bor 768 -bor 192 -bor 48; (new-object System.Net.WebClient).DownloadFile('https://github.com/git-for-windows/git/releases/download/v2.18.0.windows.1/MinGit-2.18.0-64-bit.zip','%ROOT%\tools\git.zip') }"
%ROOT%\tools\7z x -y -o%GIT_HOME% %ROOT%\tools\git.zip

%GIT% status

echo Press any key to exit
pause >nul

cd %ROOT%