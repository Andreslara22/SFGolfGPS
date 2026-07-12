# Yunari Studio — Guía de marca

Marca del estudio que publica las apps. Se muestra como pantalla de inicio
(splash) al abrir cada app y en las fichas de las tiendas.

## Concepto

**Yunari Studio** — diseño y desarrollo de apps. Identidad limpia, oscura y
moderna, con un acento en degradado que transmite tecnología y cuidado.

## Logotipo

Monograma **"Y"** geométrico (dos brazos que bajan a un tronco: crecimiento /
flecha hacia arriba) en degradado violeta → cian, dentro de un badge de
esquinas redondeadas. El logo está dibujado como **vector** en la app
(`YunariSplash.kt` → `YunariLogo`), así que es nítido a cualquier tamaño; y
también hay PNGs listos en `docs/yunari/`.

- `docs/yunari/logo-1024.png` — badge con la Y (marca sola).
- `docs/yunari/app-icon-1024.png` — versión tipo ícono (relleno completo).
- `docs/yunari/lockup-1600x520.png` — logo + "YUNARI STUDIO" horizontal.

## Paleta

| Uso | Color | Hex |
|-----|-------|-----|
| Fondo | casi negro azulado | `#0B0B14` |
| Badge / superficie | gris azulado | `#15162A` |
| Acento 1 (violeta) | violeta | `#8B5CF6` |
| Acento 2 (cian) | cian | `#22D3EE` |
| Texto principal | casi blanco | `#F2F3F7` |
| Texto secundario | gris | `#8A8CA3` |

El acento siempre es el **degradado diagonal `#8B5CF6 → #22D3EE`** (violeta
arriba-izquierda, cian abajo-derecha).

## Tipografía

- **Wordmark "YUNARI":** sans-serif muy bold, mayúsculas, con tracking amplio
  (letter-spacing ~8).
- **"STUDIO":** más chico, mayúsculas, tracking aún mayor, en gris.
- En la app se usa la fuente del sistema en peso Black/SemiBold (sin
  dependencias externas).

## Voz

Corta, clara, en español. Tagline: **"Diseño y desarrollo de apps"**.

## Uso de la pantalla de inicio

- Aparece ~2.2 s al abrir la app y luego cruza (fade) a la app.
- Se puede tocar para saltarla.
- No se repite al girar la pantalla (se recuerda con `rememberSaveable`).
- Fondo siempre oscuro (marca del estudio), independiente del tema de la app.

## Reutilización en futuras apps

Copia `YunariSplash.kt` al nuevo proyecto y envuelve tu pantalla principal con
el `Crossfade` de `MainActivity` (ver este repo). Los colores y el logo viven
todos en ese archivo, así que la marca queda idéntica en cada app del estudio.
