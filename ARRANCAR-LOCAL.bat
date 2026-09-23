@echo off
title PLAIN local (Postgres + backend)
echo ============================================
echo   PLAIN - entorno local
echo ============================================
echo.

echo [1/2] Arrancando PostgreSQL local...
"C:\Users\carlo\pgsql\pgsql\bin\pg_ctl.exe" -D "C:\Users\carlo\pgsql\data" -l "C:\Users\carlo\pgsql\postgres.log" -w -t 60 start
if errorlevel 1 (
  echo     (si ya estaba arrancado, este mensaje es normal)
)

echo.
echo [2/2] Arrancando el backend en http://127.0.0.1:8000 ...
cd /d C:\Users\carlo\plain\backend
set DATABASE_URL=postgresql://carlo@127.0.0.1:5432/plain?sslmode=disable
set PGCLIENTENCODING=UTF8
start "" http://127.0.0.1:8000/docs
.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000

echo.
echo El backend se ha detenido. Pulsa una tecla para cerrar.
pause
