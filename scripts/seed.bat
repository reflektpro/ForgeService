@echo off
echo ========================================
echo   ForgeService - Загрузка тестовых данных
echo ========================================
echo.

cd /d "%~dp0..\\server"

echo Активирую виртуальное окружение...
call venv\Scripts\activate.bat

echo.
echo Устанавливаю PYTHONPATH...
set PYTHONPATH=.

echo.
echo Загружаю реалистичные данные в базу...
python seed.py

echo.
echo Готово! Теперь можно запускать start-server.bat
echo.
pause