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
- **Juegos**: **Skins** con acarreo de empates para 2-5 jugadores (corona 👑 al líder y pozo acarreado visible) y **Match Play** clásico para 2 jugadores ("2 UP thru 7", "gana 3&2"). Todo calculado en vivo de los golpes ya anotados, sin captura extra.
- **Tema oscuro verde** (fairway) + tema claro + modo sistema.
- **Yardas / metros** configurables.
- Pantalla siempre encendida durante la ronda.
- Golpes y ajustes se guardan aunque cierres la app (SharedPreferences).

## Cómo compilar
1. Abre la carpeta `SFGolfGPS` en **Android Studio** (Hedgehog o más reciente, JDK 17).
2. Deja que sincronice Gradle (descarga AGP 8.2.2 / Kotlin 1.9.22 automáticamente).
3. Conecta tu teléfono con depuración USB y presiona **Run**, o genera el APK:
   `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
4. Instala el APK. Al abrirla, acepta el permiso de **ubicación precisa**.

## Notas técnicas
- `minSdk 26` (Android 8.0+), `targetSdk 34`.
- GPS: `GPS_PROVIDER`, actualización cada 1 s / 1 m.
- Datos del campo en `CourseData.kt` (18 hoyos, par 72). Si algún día ajustas
  coordenadas de greens/tees, edita solo ese archivo.
- Umbral del palo sugerido en `recommendedClub()` — ajústalo a tus distancias
  personales si quieres.
