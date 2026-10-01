@echo off
title Finoraax Backend Server (Auto-Restart Keep-Alive)
:start
echo ===================================================
echo   Starting Finoraax Backend Server on Port 5000...
echo ===================================================
python server.py
echo.
echo [WARNING] Server stopped or exited. Restarting in 3 seconds...
timeout /t 3 /nobreak >nul
goto start
