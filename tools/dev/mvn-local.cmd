@echo off
setlocal

for %%I in ("%~dp0..\..") do set "REPO_ROOT=%%~fI"
for %%I in ("%REPO_ROOT%\..") do set "WORKSPACE_ROOT=%%~fI"

set "JAVA_HOME=%USERPROFILE%\.jdks\ms-17.0.18"
if not exist "%JAVA_HOME%\bin\java.exe" (
  for /d %%D in ("%USERPROFILE%\.jdks\*17*") do (
    if not defined JAVA_HOME_FALLBACK if exist "%%~fD\bin\java.exe" set "JAVA_HOME_FALLBACK=%%~fD"
  )
  if defined JAVA_HOME_FALLBACK set "JAVA_HOME=%JAVA_HOME_FALLBACK%"
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [mvn-local] Java 17 not found under %USERPROFILE%\.jdks 1>&2
  exit /b 1
)

set "MAVEN_HOME="
for /d %%D in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.12\*") do (
  if exist "%%~fD\bin\mvn.cmd" set "MAVEN_HOME=%%~fD"
  if exist "%%~fD\apache-maven-3.9.12\bin\mvn.cmd" set "MAVEN_HOME=%%~fD\apache-maven-3.9.12"
)

if not defined MAVEN_HOME (
  echo [mvn-local] Maven 3.9.12 wrapper distribution not found under %USERPROFILE%\.m2\wrapper\dists 1>&2
  exit /b 1
)

set "HOME=%WORKSPACE_ROOT%\maven-home"
set "USERPROFILE=%WORKSPACE_ROOT%\maven-home"
if not exist "%HOME%\.m2\repository" mkdir "%HOME%\.m2\repository" >nul 2>nul

set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

call "%MAVEN_HOME%\bin\mvn.cmd" -s "%REPO_ROOT%\.mvn-local-settings.xml" %*
exit /b %ERRORLEVEL%
