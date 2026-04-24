@echo off
setlocal

for %%I in ("%~dp0..\..") do set "REPO_ROOT=%%~fI"
for %%I in ("%REPO_ROOT%\..") do set "WORKSPACE_ROOT=%%~fI"

set "PNPM_HOME=%WORKSPACE_ROOT%\npm-global"
set "PATH=%PNPM_HOME%;%PATH%"
set "npm_config_cache=%WORKSPACE_ROOT%\npm-cache"

if not exist "%PNPM_HOME%\pnpm.cmd" (
  echo [pnpm-local] pnpm.cmd not found at %PNPM_HOME%\pnpm.cmd 1>&2
  exit /b 1
)

call "%PNPM_HOME%\pnpm.cmd" %*
exit /b %ERRORLEVEL%
