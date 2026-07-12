# Campestre Chihuahua — estado del trabajo

## Objetivo (pedido del usuario)
Replicar lo del Sanfra para Club Campestre de Chihuahua:
1. Mapear 18 hoyos (tee/green GPS + profundidad de green estimada).
2. Generar las 18 ilustraciones (mismo estilo, mismo pipeline svgkit/specs).
3. Selector de club en Ajustes: San Francisco / Campestre (patrón ChoiceButton
   como Unidades/Tema, pref "course", enum en GolfViewModel).
4. Mismo funcionamiento (GPS, distancias, arte en HoleMap con anclajes fijos).

## Descubrimiento
- Tiles Esri World_Imagery via curl --cacert /root/.ccr/ca-bundle.crt
  URL: https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}
  (urllib da 403; curl funciona).
- Campestre ubicado ~28.640, -106.113 (curso arbolado compacto).
- Mosaico z17 (12x12 tiles, 3072x3072) en campestre_z17.jpg.
  Calibración pixel<->latlng en campestre_cal.json (Z=17, x0, y1 del tile NW):
  lat/lng de pixel (px,py): tx = x0 + px/256; ty = y1 + py/256;
  lng = tx/2^17*360-180; lat = atan(sinh(pi*(1-2*ty/2^17)))*180/pi.
- Vista reducida x3 con grid (coords en px del mosaico grande): campestre_small.jpg

## Pipeline (igual que Sanfra)
1. Identificar 18 hoyos en el mosaico: tee y green por hoyo (pixel -> latlng).
   Guardar en campestre_holes.json: [{n, teeLat, teeLng, greenLat, greenLng, par, depthM}]
2. Generar referencias rotadas 1000x890 por hoyo (tee en (500,788), green en (500,128))
   desde el mosaico (rotar+escalar como las refs del Sanfra) -> ref_camp_N.jpg
3. Trazar specs (specs_camp.py, mismo svgkit) -> camp_N.webp 1000x890
4. App:
   - CourseData: agregar campestreHoles + enum de club; GolfViewModel pref "course".
   - HoleMap: holeArt por club (camp_N con anclajes fijos 0.500,0.8854/0.500,0.1438).
   - Ajustes: sección Club (Sanfra/Campestre) + strings es/en.
   - Wear: opcional fase 2 (sync de club + arte campestre en wear).
5. Bump versión, push, CI, avisar.

## Estado
- [x] Mosaico z17 descargado y calibrado.
- [ ] Identificar routing de 18 hoyos (tees/greens) — EN CURSO.
- [ ] campestre_holes.json
- [ ] Referencias por hoyo
- [ ] Specs + ilustraciones
- [ ] Integración app + selector de club
- [ ] Push + CI + aviso

## Notas
- Advertir al usuario: el routing/numeración se infiere del satélite; que lo
  revise (él conoce el campo) y se corrige numeración si hace falta.
- Pars estimados por longitud: <230m par3, 230-430 par4, >430 par5; ajustar a total 72.

## Observaciones del mosaico (coords en pixeles del mosaico 3072x3072)
- Bbox del campo: x 1280-2110, y 1240-2580. Escala z17 ~1.048 m/px.
- Casa club: (1700-1900, 2050-2350) — albercas, canchas de tenis/padel, estacionamiento.
- Lagos principales: (1450-1700, 1930-2100), forma irregular con isla/penínsulas.
- Posible campo de práctica: óvalo uniforme (1440-1610, 2190-2360) al SO de casa club.
- Huerto/vivero (dirt con árboles en filas): (1690-1900, 1250-1350) esquina NE.
- Corredores oeste: dos calles paralelas verticales x 1330-1560, y 1300-1900;
  green brillante en (1385,1405)~r18 y otro ~(1480,1740).
- Corredores este: x 1680-1900, y 1330-1900, con green sinuoso (1770-1840, 1270-1330).
- Sur: corredores (1300-1900, 2300-2560) alrededor del arroyo.
- camp_top.jpg = crop (1250,1230,2160,1900) escala 1.3; camp_bot.jpg = (1250,1880,2160,2580).

## Siguiente paso inmediato
Crops de detalle (400x400 escala 2) para confirmar cada green (círculos claros
con bunkers) y cada tee; ir llenando campestre_holes.json con pixeles
tee/green por hoyo y convertir a lat/lng con campestre_cal.json.

## Detección automática de greens (hecha)
- camp_green_candidates.json: 17 candidatos (coords en px del mosaico 3072).
- Visualización: camp_greens_cand.jpg (crop 1260,1230-2130,2590 a escala 0.85,
  para pasar de display a mosaico: /0.85 y + (1260,1230)).
- Lectura visual: la mayoría se ven como greens reales (círculos brillantes).
  OJO: candidatos junto a la casa club pueden ser prácticas (12, 16 tal vez);
  el 0 está pegado al lago norte (posible green real semi-isla).
  Faltan probablemente 1-3 greens sombreados: revisar NE (cerca del huerto,
  ~(1780,1300) mosaico) y franja este (x 1850-2050, y 1400-1800) y sur.
- SIGUIENTE: crops de detalle para (a) confirmar cada candidato como green,
  (b) encontrar los tees (pads rectangulares) y (c) armar el routing 18 hoyos
  encadenando tee->green (los tees del siguiente hoyo quedan junto al green
  anterior; hoyos 1 y 10 salen cerca de la casa club, 9 y 18 regresan a ella).
- Luego: llenar campestre_holes.json [{n,teePx:[x,y],greenPx:[x,y],par,depthM}],
  convertir px->latlng (mercator con campestre_cal.json), generar refs rotadas
  1000x890 (tee (500,788), green (500,128)) con PIL (rotate+resize del mosaico),
  y de ahí specs_camp.py + camp_N.webp con el MISMO svgkit.

## Crop NE revisado (camp_ne.jpg = crop 1700,1250-2130,1800 escala 1.5)
- Green sinuoso metido en el huerto: complejo ~(1860-1930, 1290-1360) mosaico,
  centro aprox (1885,1325). Fairway llega desde el sur (corredor 1780-1900 x,
  1400-1700 y). Es un green real (candidato faltante del detector o el "0").
- Rectángulo verde en (1960-2020, 1440-1500) = cancha de futbol, NO golf.
- Corredor este continúa en (1900-2000, 1600-1800).
- Pendiente revisar: franja oeste a detalle (tees), zona sur (greens 5,9,10,16),
  y confirmar cuáles candidatos junto a casa club son prácticas.

## Crops oeste y sur revisados
- camp_west.jpg (1280-1650, 1250-1950): el campo empieza en x~1430; al oeste
  puras casas. Green con bunkers ~(1495,1565); posible otro ~(1560,1450).
- camp_south.jpg (1280-1950, 2200-2590): borde sur del campo y~2450.
  - Green chico con bunkers SW ~(1340,2270).
  - CAMPO DE PRÁCTICA confirmado: rectángulo bardeado (1590-1710, 2160-2430)
    — NO es hoyo; el candidato 12 del detector cae ahí, descartarlo.
  - Cluster de bunkers alrededor de green ~(1745,2310).
  - Corredor inferior (1450-1900, 2350-2450) rumbo al este.
- Con esto: greens confirmados a mano hasta ahora ~(1885,1325) NE,
  (1495,1565) W, (1340,2270) SW, (1745,2310) S + los 17 candidatos del json
  (menos el 12=práctica). Siguiente: armar routing encadenando distancias
  tee-green razonables (par3 130-210m, par4 250-420m, par5 430-520m en px:
  1px=1.048m) empezando y terminando en casa club (1700-1900, 2050-2350).

## PAUSADO (por pedido del usuario)
El usuario va a pasar las COORDENADAS de los hoyos él mismo (como hizo con el
Sanfra). NO seguir infiriendo el routing del satélite hasta recibirlas.
Al recibirlas: llenar campestre_holes.json, y seguir el pipeline de arriba
(refs rotadas -> specs -> webp -> selector de club en la app).
Material duradero commiteado en el repo: arte-referencia/campestre/
