# Ficha de Google Play — SF Golf GPS

Todo lo que hay que pegar/elegir en Play Console para publicar la app.
Copia y pega los textos tal cual; ajusta lo que quieras.

---

## Datos básicos

- **Nombre de la app (30 caracteres máx):** `SF Golf GPS`
- **Package / ID de aplicación:** `mx.clubsanfrancisco.golfgps` (ya fijo, no cambiar)
- **Categoría:** Deportes
- **Etiquetas:** golf, GPS, deporte
- **Correo de contacto:** andres.guru@gmail.com
- **Sitio web (opcional):** —
- **Política de privacidad (URL):** se activa con GitHub Pages (ver abajo).
  Quedará en: `https://andreslara22.github.io/SFGolfGPS/privacy-policy.html`

---

## Descripción breve (80 caracteres máx)

```
GPS de golf para el Club San Francisco: distancias, tarjeta y stats, sin internet.
```

*(son 81 con acentos contados como 1; si Play se queja, usa esta de 78:)*

```
GPS de golf del Club San Francisco: distancias, tarjeta y stats sin internet.
```

---

## Descripción completa (4000 caracteres máx)

```
SF Golf GPS es la app oficial de distancias y tarjeta de anotación del Club de
Golf San Francisco (Chihuahua). Hecha para jugarse a pleno sol y sin depender de
internet: los datos del campo están dentro de la app y el GPS trabaja en tu
propio teléfono.

DISTANCIAS EN TIEMPO REAL
• Número gigante con la distancia al centro del green, legible bajo el sol.
• Frente / Centro / Fondo del green calculados por hoyo.
• Posición del pin del día (rojo/blanco/azul) con la rotación del club.
• "Plays like": la app aprende la elevación de cada green y ajusta la distancia
  efectiva en tiros cuesta arriba o cuesta abajo.
• Palo sugerido según la distancia, usando TUS distancias por palo.

MAPA DE CADA HOYO
• Ilustración del hoyo con tu posición GPS y la línea al green.
• Toca cualquier punto para medir un layup: distancia de ti a ese punto y de ese
  punto al green.

TARJETA Y ESTADÍSTICAS
• Anota golpes de hasta 5 jugadores, con putts y fairways.
• OUT / IN / TOTAL y diferencial contra par.
• GIR y FIR automáticos, promedio de putts y tendencia de tus rondas.
• Handicap index estilo WHS con el rating y slope oficiales del campo.
• Comparte la tarjeta como imagen por WhatsApp, correo, etc.

JUEGOS
• Skins con acarreo de empates.
• Match Play para 2 jugadores.
• Stableford con handicap por hoyo.

MEDIR TUS GOLPES
• Marca la bola, camina a donde cayó y guarda: la app aprende cuánto pegas con
  cada palo y afina el palo sugerido.

RELOJ
• Compatible con reloj vinculado: distancias, contador de golpes y resumen de la
  ronda en la muñeca.

PRIVACIDAD
• Funciona 100% en tu dispositivo. Tu ubicación se usa solo para medir
  distancias mientras juegas y nunca sale de tu teléfono. Sin anuncios.

Club de Golf San Francisco · Chihuahua, México · 18 hoyos · Par 72.
```

---

## Formulario "Seguridad de los datos" (Data safety)

Respuestas para el cuestionario de Play (coinciden con la política de privacidad):

- **¿Tu app recopila o comparte datos del usuario?**
  → *Ubicación aproximada/precisa:* se **usa** pero **NO se recopila** (no sale
    del dispositivo, se procesa en tiempo real). En el formulario marca que la
    ubicación **no se recopila ni se comparte** porque no se envía a servidores.
  → Si el club activa la cuenta opcional con Firebase, entonces sí marca:
    *Correo electrónico* e *historial de la app* → recopilados, para
    "funcionalidad de la app", cifrado en tránsito, el usuario puede pedir que se
    borren. (Mientras no actives Firebase, no marques nada de esto.)
- **¿Se cifran los datos en tránsito?** Sí (si aplica Firebase).
- **¿El usuario puede pedir que se borren sus datos?** Sí.

## Clasificación de contenido (Content rating)

- Cuestionario IARC: la app **no** tiene violencia, sexo, apuestas con dinero
  real, ni contenido para adultos. Los "juegos" (Skins/Match Play) son formatos
  de puntuación de golf, **no** apuestas dentro de la app.
- Resultado esperado: **Para todos / PEGI 3**.

## Acceso a la app (App access)

- La app funciona completa sin cuenta ni contraseña → marca
  "Toda la funcionalidad está disponible sin credenciales especiales".

## Anuncios

- **La app no contiene anuncios.**

## País/precio

- **Gratis**, disponible en México (y donde quieras).

---

## Gráficos que pide Play (los preparo aparte)

- **Ícono:** 512 × 512 px, PNG (ya existe el ícono de la app; hago la versión 512).
- **Gráfico destacado (feature graphic):** 1024 × 500 px.
- **Capturas de teléfono:** mínimo 2 (recomendado 4-8), entre 320 y 3840 px de lado.
  Se pueden tomar del emulador o de un teléfono con la app.

---

## Publicar la política de privacidad (gratis, con GitHub Pages)

1. En GitHub: repo **SFGolfGPS** → **Settings** → **Pages**.
2. En "Build and deployment": Source = **Deploy from a branch**.
3. Branch = **main**, carpeta = **/docs** → Save.
4. En 1-2 minutos la política queda en:
   `https://andreslara22.github.io/SFGolfGPS/privacy-policy.html`
5. Esa URL se pega en Play Console → Contenido de la app → Política de privacidad.
