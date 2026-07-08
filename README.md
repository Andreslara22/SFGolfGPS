# SF Golf GPS — Club de Golf San Francisco (Chihuahua)

App Android **100% nativa** (Kotlin + Jetpack Compose). Sin servicios de terceros:
el GPS usa `LocationManager` del sistema Android y los datos del campo están
embebidos localmente. Funciona sin internet.

## Funciones
- **Distancia en tiempo real** de tu posición al centro del green (fórmula de Haversine), en número gigante legible a pleno sol.
- **Cursor de medición en el mapa**: toca cualquier punto del hoyo y verás la distancia de tu posición (o del tee) a ese punto **y** de ese punto al green — ideal para planear layups. Toca el marcador para quitarlo.
- **Front / Center / Back reales**: cada green tiene su profundidad (`greenDepthM` en `CourseData.kt`, afinable por hoyo) y las posiciones de bandera roja/blanca/azul se calculan proporcionalmente a ella.
- **"Plays like" por elevación**: la app aprende la elevación de cada green automáticamente cuando lo pisas con el GPS activo (autocalibración con media móvil, persiste entre rondas). Después de una ronda, los tiros cuesta arriba/abajo muestran la distancia efectiva y el palo sugerido la usa. Estado y reset en Settings.
- **Detección automática de hoyo** por el tee más cercano, con botones ◀ / ▶ para cambio manual y botón AUTO para volver.
- **Palo sugerido** según la distancia al green.
- **Contador de golpes hasta 5 jugadores**, con tarjeta completa: OUT / IN / TOTAL y diferencial vs par (birdies en verde, bogeys en rojo).
- **Stats de ronda**: putts por hoyo y fairway ✓/✗ (par 4/5) directo en la tarjeta de golpes; GIR se deriva automáticamente (golpes − putts ≤ par − 2). Resumen FIR / GIR / putts por jugador en el Scorecard y en el historial de rondas guardadas.
- **Juegos**: **Skins** con acarreo de empates para 2-5 jugadores (corona 👑 al líder y pozo acarreado visible), **Match Play** clásico para 2 jugadores ("2 UP thru 7", "gana 3&2") y **Stableford con handicap**: cada jugador configura su handicap (0-40) en Players, el stroke index de cada hoyo reparte los golpes de ventaja y los puntos son los tradicionales (par neto 2 · birdie 3 · bogey 1 · doble bogey neto o peor 0). Todo calculado en vivo de los golpes ya anotados, sin captura extra.
- **Medir golpe** 📏: marca la bola antes de pegar, camina a donde cayó y guarda — la app aprende tus distancias reales por palo (media móvil 70/30) y el palo sugerido usa TUS números.
- **Pestaña Stats** 📊: promedio y mejor score, putts por ronda, %GIR, %FIR, tendencia de las últimas 10 rondas y promedio por hoyo con tus 3 hoyos más caros vs par (🔥), por jugador.
- **Handicap index (WHS)**: diferenciales con rating/slope del campo (`COURSE_RATING`/`SLOPE_RATING` en `CourseData.kt`, afinables) y mejores 8 de las últimas 20 rondas completas (tabla oficial para menos rondas, mínimo 3). Visible en Stats y junto a cada jugador.
- **Tema oscuro verde** (fairway) + tema claro + modo sistema.
- **Yardas / metros** configurables.
- **Wear OS mejorado**: F/C/B alrededor de la distancia grande, contador de putts y resumen de ronda ("TOTAL 42 · +6 · thru 9") directo en el reloj, con scroll para pantallas chicas. **Vibra** cuando el GPS detecta que cambiaste de hoyo (AUTO) y trae **complicación de carátula** (hoyo y golpes, un toque abre la app) además del Tile.
- Pantalla siempre encendida durante la ronda.
- Golpes y ajustes se guardan aunque cierres la app (SharedPreferences).

## Cómo compilar
1. Abre la carpeta `SFGolfGPS` en **Android Studio** (Hedgehog o más reciente, JDK 17).
2. Deja que sincronice Gradle (descarga AGP 8.2.2 / Kotlin 1.9.22 automáticamente).
3. Conecta tu teléfono con depuración USB y presiona **Run**, o genera el APK:
   `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
4. Instala el APK. Al abrirla, acepta el permiso de **ubicación precisa**.

## Cuenta y respaldo en la nube (opcional)

El código de login (crear cuenta / iniciar sesión / olvidé mi contraseña) y el
respaldo del historial ya están integrados con **Firebase**, pero la sección de
"Cuenta" solo aparece si el proyecto tiene la configuración. Para activarla:

1. Entra a https://console.firebase.google.com → **Crear proyecto** (`SF Golf`,
   Analytics desactivado).
2. **Agregar app → Android** con package `mx.clubsanfrancisco.golfgps` y
   descarga el `google-services.json`.
3. Pon el archivo en **`app/google-services.json`** de este repo y haz push
   (el build lo detecta y activa Firebase solo).
4. En la consola: **Authentication → Sign-in method → Email/Password: habilitar**.
5. **Firestore Database → Crear base de datos** y en *Rules* pega:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{userId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```

Sin el json, la app compila y funciona 100% local (sin sección de cuenta).
El respaldo (jugadores, palos, historial, elevaciones) se sube al cerrar cada
ronda y con el botón "Respaldar ahora"; "Restaurar" lo baja en otro teléfono.

## Notas técnicas
- `minSdk 26` (Android 8.0+), `targetSdk 34`.
- GPS: `GPS_PROVIDER`, actualización cada 1 s / 1 m.
- Datos del campo en `CourseData.kt` (18 hoyos, par 72). Si algún día ajustas
  coordenadas de greens/tees, edita solo ese archivo.
- El **stroke index** de cada hoyo (para Stableford) está estimado por longitud
  relativa al par; cámbialo en `CourseData.kt` por el índice oficial de la
  tarjeta del club cuando lo tengas.
- Umbral del palo sugerido en `recommendedClub()` — ajústalo a tus distancias
  personales si quieres.
