@echo off
setlocal

for %%I in ("%~dp0..\..") do set "REPO_ROOT=%%~fI"
for %%I in ("%REPO_ROOT%\..") do set "WORKSPACE_ROOT=%%~fI"

set "DOCKER_CONFIG=%WORKSPACE_ROOT%\docker-config"
if not exist "%DOCKER_CONFIG%" mkdir "%DOCKER_CONFIG%" >nul 2>nul

set "COMPOSE_PROFILES=--profile nonprod-app --profile nonprod-observe"
docker compose -f "%REPO_ROOT%\docker-compose.dev.yml" %COMPOSE_PROFILES% %*
exit /b %ERRORLEVEL%
