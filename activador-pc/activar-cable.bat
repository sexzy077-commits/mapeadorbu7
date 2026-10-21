@echo off
title BUY7 MAPPER - Activador por cable
echo.
echo  BUY7 MAPPER - Activador por cable
echo  ---------------------------------
echo  1. Activa "Depuracion USB" en el telefono y conectalo por cable.
echo  2. Acepta el aviso "Permitir depuracion USB" en el telefono.
echo  3. Este archivo debe estar en la misma carpeta que adb.exe (platform-tools).
echo.
adb devices
echo.
set /p TOKEN=Escribe el codigo que muestra la app: 
adb shell "CLASSPATH=$(pm path com.buy7.mapper | cut -d: -f2) setsid nohup app_process / com.buy7.mapper.server.InputServer 38417 %TOKEN% >/data/local/tmp/buy7.log 2>&1 &"
echo.
echo  Listo. Vuelve a la app y pulsa "2. Activar modo avanzado".
echo  Ya puedes desconectar el cable.
echo.
pause
