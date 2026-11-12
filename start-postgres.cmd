@echo off
setlocal
cd /d "%~dp0"

if "%DB_PASSWORD%"=="" set /p DB_PASSWORD=请输入 PostgreSQL 用户 webporduce 的密码: 
if "%DB_PASSWORD%"=="" (
  echo 密码不能为空。
  exit /b 1
)

set SPRING_PROFILES_ACTIVE=local-postgres
set DB_URL=jdbc:postgresql://localhost:5432/webporduce
set DB_USERNAME=webporduce
set APP_STORAGE_ROOT=%CD%\data\uploads

echo 正在使用 local-postgres profile 启动后端...
call mvnw.cmd -B -ntp "-Dskip.frontend=true" spring-boot:run

endlocal
