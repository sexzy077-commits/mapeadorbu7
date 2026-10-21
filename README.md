# BUY7 MAPPER (v0.3)

Cursor propio flotante + touchpad, mira, joystick W A S D, teclas que mantienen toques
y modo avanzado por depuración inalámbrica (como GG Mouse 3).

## Cambiar tu diseño
- Flecha: `app/src/main/res/drawable/cursor.png`
- Logo: `app/src/main/res/drawable/ic_logo_fg.xml`

## Compilar desde el teléfono
1. Sube esta carpeta a un repositorio de GitHub (rama `main`).
2. Pestaña **Actions** > "Build APK" > cuando termine, descarga `BUY7-MAPPER-apk`.

## Usarlo
1. Abre la app y da los permisos (sobre otras apps + accesibilidad).
2. Modo avanzado (una sola vez): conéctate a Wi‑Fi, pulsa «1. Emparejar», entra a
   Opciones de desarrollador > Depuración inalámbrica > Emparejar con código y escribe el
   código en la notificación de BUY7. Luego pulsa «2. Activar modo avanzado».
3. Elige tu juego y pulsa «ACTIVAR Y JUGAR».

## Activar por cable (PC)
Carpeta `activador-pc`: pon `activar-cable.bat` junto a `adb.exe` (platform-tools de Google),
conecta el teléfono con Depuración USB y escribe el código que muestra la app.
