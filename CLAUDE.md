# SF Golf GPS — guía para Claude

App de golf GPS del Club de Golf San Francisco (Chihuahua). Módulos: `app`
(teléfono Android), `wear` (Galaxy Watch7 / Wear OS), `ios/` (iPhone + Apple
Watch). Package Android: `mx.clubsanfrancisco.golfgps`.

## Cómo compilar (NO hay SDK local en la PC)

Push a la rama → GitHub Actions compila. Corren DOS workflows por push:
- **"Android release (AAB)"** → artifacts `SFGolfGPS-apk` (cel),
  `SFGolfGPS-wear-apk` (reloj) y `SFGolfGPS-aab-google-play` (para publicar).
- **"iOS build"** → IPA sin firmar + build de simulador.

Si el build falla, el CI commitea `build-error.log` al repo — leerlo y corregir.

## Cómo INSTALAR/ACTUALIZAR el cel y el reloj de Andres (desde la PC, por adb Wi-Fi)

`adb` está en `C:\Users\USER\Desktop\Apps\platform-tools-latest-windows\platform-tools\adb.exe`.
Ambos dispositivos ya están **emparejados** con esta PC; normalmente basta conectar.

1. Descargar los DOS artifacts del MISMO run (API de GitHub con el token de
   `git credential fill`): `SFGolfGPS-apk` y `SFGolfGPS-wear-apk`.
2. Descubrir puertos: `adb mdns services` →
   - Reloj = `192.168.0.9:<puerto>` · Cel = `192.168.0.170:<puerto>`
   - Ignorar el puerto fantasma `39541` del reloj si aparece duplicado.
3. `adb connect IP:PUERTO` (sin código si ya empareja). Si rebota, pedir a
   Andres un pairing nuevo: en el dispositivo Wireless debugging → Pair new
   device → `adb pair IP:PUERTO CODIGO` (código como ARGUMENTO, no por prompt).
4. Reloj: ANTES de instalar, `adb -s <reloj> shell svc power stayon true`
   (la subida de ~20 MB se corta si la pantalla se duerme); al final
   `stayon false`. Mejor aún si el reloj está en su cargador.
5. Instalar: `adb -s <dev> install -r <apk>` — entra DIRECTO sin desinstalar
   (firma fija). Lanzar: cel `.../.MainActivity`, reloj `.../.wear.MainActivity`.

**REGLA DE ORO: cel y reloj SIEMPRE del MISMO run.** La Data Layer de Google
solo sincroniza apps con firma idéntica; si difieren, la sincronización muere
en silencio. Diagnóstico: `adb shell dumpsys package mx.clubsanfrancisco.golfgps | grep signatures:`
en ambos — los hashes deben ser iguales.

## Firmas

- `signing/sfgolf-debug.keystore` (pass `android`, alias `sfgolf`) — debug.
- `signing/sfgolf-upload.jks` (pass `sanfrancisco2026`, alias `sfgolf-upload`)
  — llave de SUBIDA para Google Play (release). Los dispositivos de Andres
  corren los RELEASE firmados con esta llave.

## Arquitectura de sincronización cel⇄reloj

Data Layer (`/round/state`), snapshot completo con last-write-wins por
timestamp: nombres, golpes de todos, jugador activo, hoyo, unidades, AUTO,
pin del día (flags), yardas por palo. Separador de listas: U+0001 (`SEP`).
La posición GPS NO se comparte: cada dispositivo mide con su propio GPS
(decisión deliberada). Al tocar el protocolo, cambiar AMBOS lados
(`GolfViewModel.kt` y `wear/MainActivity.kt`) y reinstalar ambos.

## Datos del campo

- `CourseData.kt` (app) y `WearCourse.kt` (wear): 18 hoyos con lat/lng de
  tee/green, `greenDepthM` MEDIDO en satélite (jul 2026) y stroke index de la
  tarjeta oficial. F/B = bordes reales del green; solo el número grande sigue
  al pin.
- `arte-referencia/hoyo_N.jpg`: satélite de cada hoyo ya rotado/encuadrado
  como el arte (tee en (0.5, 0.884), green en (0.5, 0.143), lienzo 1000×890)
  — el arte calcado de ahí hereda los anclajes GPS sin calibración.
