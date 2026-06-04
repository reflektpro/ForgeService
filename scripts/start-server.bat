@echo off
echo ========================================
echo   ForgeService - Запуск сервера
echo ========================================
echo.

cd /d "%~dp0..\\server"

echo Активирую виртуальное окружение...
call venv\Scripts\activate.bat

echo.
echo Устанавливаю PYTHONPATH для правильных импортов...
set PYTHONPATH=.

echo.
echo Запускаю сервер...
echo Сервер будет доступен по адресу: http://localhost:8000
echo Swagger (документация API): http://localhost:8000/docs
echo.
echo Чтобы остановить сервер - закрой это окно или нажми Ctrl+C
echo.

uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

pause