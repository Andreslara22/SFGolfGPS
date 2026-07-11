package mx.clubsanfrancisco.golfgps

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Catálogo de textos de la interfaz en español e inglés. Los términos de golf
 * internacionales (Eagle, Birdie, Par, Bogey, fairway, green, tee, putt, GIR,
 * FIR, Skins, Match Play, Stableford, OUT/IN/TOTAL, nombres de palos) no se
 * traducen. Se elige con Ajustes → Idioma y se provee vía [LocalStrings].
 */
interface AppStrings {
    // Pestañas
    val tabGps: String
    val tabCard: String
    val tabStats: String
    val tabPlayers: String
    val tabSettings: String

    // Pantalla GPS
    val holeWord: String
    val gpsOff: String
    val gpsSearching: String
    val permissionMsg: String
    val enableGps: String
    val playsLike: String
    val suggestedClub: String
    val waitingGps: String
    val measureBtn: String
    val allPlayers: String
    val hidePlayers: String
    val shotInProgress: String
    val fromMark: String
    val walkAndSave: String
    val save: String
    val cancel: String
    val doubleBogey: String

    // Tarjeta
    val cardTitle: String
    val holeCol: String
    val legend: String
    val roundStats: String
    val games: String
    val skinsEmpty: String
    fun carriedSkins(n: Int): String
    fun skinsWinLine(name: String, n: Int): String
    fun skinsTieLine(pot: Int): String
    val skinsHistoryShow: String
    val skinsHistoryHide: String
    val stablefordTitle: String
    val stablefordHint: String
    val finishBtn: String
    val shareBtn: String
    val sharePdfBtn: String
    val shareChooser: String
    val chipGreen: String
    val chipHole: String
    val historyTitle: String
    val historyEmpty: String
    val strokesWord: String
    val holesWord: String
    val delete: String
    val finishDialogTitle: String
    val finishDialogText: String
    val saveReset: String

    // Stats
    val statsSub: String
    val statsEmpty: String
    val tileRounds: String
    val tileAverage: String
    val tileBest: String
    val tilePuttsRound: String
    val fullRoundsNote: String
    fun hcpDetail(rounds: Int): String
    fun hcpNeed(rounds: Int): String
    val trendTitle: String
    fun trendSub(n: Int): String
    val perHoleTitle: String
    val perHoleEmpty: String
    fun worstNote(n: Int): String

    // Jugadores
    val playersTitle: String
    val playersSub: String
    fun playerLabel(n: Int): String
    val remove: String
    fun indexLabel(idx: Double): String
    val addPlayer: String
    val maxPlayers: String

    // Ajustes
    val settingsTitle: String
    val languageTitle: String
    val unitsTitle: String
    val yards: String
    val meters: String
    val themeTitle: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val elevTitle: String
    val elevDesc: String
    fun calibrated(n: Int): String
    val resetSmall: String
    val clubsTitle: String
    val clubsDesc: String
    val resetDefaults: String
    val roundTitle: String
    val clearStrokes: String
    val clearDialogTitle: String
    val clearDialogText: String
    val yesClear: String
    val aboutText: String

    // Imagen compartida
    val summary: String
}

object StringsEs : AppStrings {
    override val tabGps = "GPS"
    override val tabCard = "Tarjeta"
    override val tabStats = "Stats"
    override val tabPlayers = "Jugadores"
    override val tabSettings = "Ajustes"

    override val holeWord = "HOYO"
    override val gpsOff = "GPS desactivado"
    override val gpsSearching = "Buscando señal GPS…"
    override val permissionMsg = "Se necesita permiso de ubicación para medir tu distancia al green."
    override val enableGps = "Activar GPS"
    override val playsLike = "JUEGA COMO"
    override val suggestedClub = "PALO SUGERIDO"
    override val waitingGps = "Esperando GPS"
    override val measureBtn = "📏 Medir"
    override val allPlayers = "Todos los jugadores ▾"
    override val hidePlayers = "Ocultar jugadores ▴"
    override val shotInProgress = "📏 GOLPE EN CURSO"
    override val fromMark = "desde la marca"
    override val walkAndSave = "Camina hasta donde cayó y guarda para afinar el palo."
    override val save = "Guardar"
    override val cancel = "Cancelar"
    override val doubleBogey = "Doble bogey"

    override val cardTitle = "📋 Tarjeta"
    override val holeCol = "Hoyo"
    override val legend = "⭕ bajo par · ⬜ sobre par"
    override val roundStats = "🎯 Stats de la ronda"
    override val games = "🏆 Juegos"
    override val skinsEmpty = "Anota los golpes de todos los jugadores en un hoyo y aquí aparecen los skins. Empates se acarrean."
    override fun carriedSkins(n: Int) =
        "🔥 $n skin${if (n == 1) "" else "s"} acarreado${if (n == 1) "" else "s"} al siguiente hoyo"
    override fun skinsWinLine(name: String, n: Int) =
        "$name gana $n skin${if (n == 1) "" else "s"}"
    override fun skinsTieLine(pot: Int) = "Empate · arrastra $pot"
    override val skinsHistoryShow = "Ver historial por hoyo ▾"
    override val skinsHistoryHide = "Ocultar historial ▴"
    override val stablefordTitle = "STABLEFORD · CON HANDICAP"
    override val stablefordHint = "Par neto = 2 pts, birdie 3, bogey 1. Configura el handicap de cada jugador en Jugadores para que reparta golpes de ventaja."
    override val finishBtn = "🏁 Terminar y guardar ronda"
    override val shareBtn = "📤 Compartir tarjeta como imagen"
    override val sharePdfBtn = "📄 Compartir tarjeta en PDF"
    override val shareChooser = "Compartir tarjeta"
    override val chipGreen = "VER GREEN"
    override val chipHole = "VER HOYO"
    override val historyTitle = "Historial de rondas"
    override val historyEmpty = "Aún no hay rondas guardadas. Termina una ronda para verla aquí. 🌱"
    override val strokesWord = "golpes"
    override val holesWord = "hoyos"
    override val delete = "Borrar"
    override val finishDialogTitle = "¿Terminar esta ronda?"
    override val finishDialogText = "Los golpes se guardarán en tu historial y la tarjeta volverá al hoyo 1."
    override val saveReset = "Guardar y reiniciar"

    override val statsSub = "Tendencias de tus rondas guardadas"
    override val statsEmpty = "Aquí verás promedios, tendencia y tus hoyos más difíciles. Termina y guarda rondas en la Tarjeta para llenarlo. 🌱"
    override val tileRounds = "RONDAS"
    override val tileAverage = "PROMEDIO"
    override val tileBest = "MEJOR"
    override val tilePuttsRound = "PUTTS/RONDA"
    override val fullRoundsNote = "Promedio y mejor score usan solo rondas completas de 18 hoyos."
    override fun hcpDetail(rounds: Int) =
        "Con $rounds ronda${if (rounds == 1) "" else "s"} completa${if (rounds == 1) "" else "s"} · " +
        "mejores diferenciales de las últimas 20 · rating ${CourseData.COURSE_RATING} / slope ${CourseData.SLOPE_RATING}"
    override fun hcpNeed(rounds: Int) =
        "Se calcula con al menos 3 rondas completas de 18 hoyos — llevas $rounds."
    override val trendTitle = "Tendencia"
    override fun trendSub(n: Int) = "Últimas $n rondas de 18 hoyos · barra más baja = mejor score"
    override val perHoleTitle = "Promedio por hoyo"
    override val perHoleEmpty = "Se llena con tus próximas rondas guardadas (las rondas viejas no guardaron el detalle hoyo por hoyo)."
    override fun worstNote(n: Int) = "🔥 = tus 3 hoyos más caros vs par ($n ronda${if (n == 1) "" else "s"})"

    override val playersTitle = "🏌️ Jugadores"
    override val playersSub = "Hasta 5 jugadores por ronda"
    override fun playerLabel(n: Int) = "Jugador $n"
    override val remove = "Quitar"
    override fun indexLabel(idx: Double) = "índice ${"%.1f".format(idx)}"
    override val addPlayer = "＋ Agregar jugador"
    override val maxPlayers = "Máximo de 5 jugadores alcanzado"

    override val settingsTitle = "⚙️ Ajustes"
    override val languageTitle = "Idioma"
    override val unitsTitle = "Unidades"
    override val yards = "Yardas"
    override val meters = "Metros"
    override val themeTitle = "Tema"
    override val themeSystem = "Sistema"
    override val themeLight = "Claro"
    override val themeDark = "Oscuro"
    override val elevTitle = "Elevación (\"juega como\")"
    override val elevDesc = "La app aprende la elevación de cada green cuando lo pisas con el GPS activo. Después de una ronda, las distancias se ajustan solas en tiros cuesta arriba/abajo."
    override fun calibrated(n: Int) = "$n/18 greens calibrados"
    override val resetSmall = "✕ borrar"
    override val clubsTitle = "Distancias de palos (yd)"
    override val clubsDesc = "Se usan para el palo sugerido, por jugador"
    override val resetDefaults = "Restablecer valores"
    override val roundTitle = "Ronda"
    override val clearStrokes = "Borrar golpes (sin guardar)"
    override val clearDialogTitle = "¿Borrar todos los golpes?"
    override val clearDialogText = "Esto borra la tarjeta actual de todos los jugadores sin guardarla en el historial."
    override val yesClear = "Sí, borrar"
    override val aboutText = "${CourseData.CITY} · 18 hoyos · Par ${CourseData.totalPar}\n" +
        "La pantalla se mantiene encendida durante la ronda.\n" +
        "Distancias medidas por GPS (Haversine) al centro del green."

    override val summary = "Resumen"
}

object StringsEn : AppStrings {
    override val tabGps = "GPS"
    override val tabCard = "Scorecard"
    override val tabStats = "Stats"
    override val tabPlayers = "Players"
    override val tabSettings = "Settings"

    override val holeWord = "HOLE"
    override val gpsOff = "GPS off"
    override val gpsSearching = "Searching for GPS…"
    override val permissionMsg = "Location permission is needed to measure your distance to the green."
    override val enableGps = "Enable GPS"
    override val playsLike = "PLAYS LIKE"
    override val suggestedClub = "SUGGESTED CLUB"
    override val waitingGps = "Waiting for GPS"
    override val measureBtn = "📏 Measure"
    override val allPlayers = "All players ▾"
    override val hidePlayers = "Hide players ▴"
    override val shotInProgress = "📏 SHOT IN PROGRESS"
    override val fromMark = "from the mark"
    override val walkAndSave = "Walk to where it landed and save to tune the club."
    override val save = "Save"
    override val cancel = "Cancel"
    override val doubleBogey = "Double bogey"

    override val cardTitle = "📋 Scorecard"
    override val holeCol = "Hole"
    override val legend = "⭕ under par · ⬜ over par"
    override val roundStats = "🎯 Round stats"
    override val games = "🏆 Games"
    override val skinsEmpty = "Enter every player's strokes on a hole and skins show up here. Ties carry over."
    override fun carriedSkins(n: Int) =
        "🔥 $n skin${if (n == 1) "" else "s"} carried to the next hole"
    override fun skinsWinLine(name: String, n: Int) =
        "$name wins $n skin${if (n == 1) "" else "s"}"
    override fun skinsTieLine(pot: Int) = "Tie · carries $pot"
    override val skinsHistoryShow = "Show hole-by-hole history ▾"
    override val skinsHistoryHide = "Hide history ▴"
    override val stablefordTitle = "STABLEFORD · WITH HANDICAP"
    override val stablefordHint = "Net par = 2 pts, birdie 3, bogey 1. Set each player's handicap in Players to hand out advantage strokes."
    override val finishBtn = "🏁 Finish & save round"
    override val shareBtn = "📤 Share scorecard as image"
    override val sharePdfBtn = "📄 Share scorecard as PDF"
    override val shareChooser = "Share scorecard"
    override val chipGreen = "GREEN VIEW"
    override val chipHole = "FULL HOLE"
    override val historyTitle = "Round history"
    override val historyEmpty = "No saved rounds yet. Finish a round to keep it here. 🌱"
    override val strokesWord = "strokes"
    override val holesWord = "holes"
    override val delete = "Delete"
    override val finishDialogTitle = "Finish this round?"
    override val finishDialogText = "Scores will be saved to your history and the scorecard will reset to hole 1."
    override val saveReset = "Save & reset"

    override val statsSub = "Trends from your saved rounds"
    override val statsEmpty = "You'll see averages, trends and your toughest holes here. Finish and save rounds in the Scorecard tab. 🌱"
    override val tileRounds = "ROUNDS"
    override val tileAverage = "AVERAGE"
    override val tileBest = "BEST"
    override val tilePuttsRound = "PUTTS/ROUND"
    override val fullRoundsNote = "Average and best score use only full 18-hole rounds."
    override fun hcpDetail(rounds: Int) =
        "From $rounds full round${if (rounds == 1) "" else "s"} · " +
        "best differentials of the last 20 · rating ${CourseData.COURSE_RATING} / slope ${CourseData.SLOPE_RATING}"
    override fun hcpNeed(rounds: Int) =
        "Needs at least 3 full 18-hole rounds — you have $rounds."
    override val trendTitle = "Trend"
    override fun trendSub(n: Int) = "Last $n 18-hole rounds · lower bar = better score"
    override val perHoleTitle = "Average per hole"
    override val perHoleEmpty = "Fills up with your next saved rounds (older rounds didn't keep the hole-by-hole detail)."
    override fun worstNote(n: Int) = "🔥 = your 3 costliest holes vs par ($n round${if (n == 1) "" else "s"})"

    override val playersTitle = "🏌️ Players"
    override val playersSub = "Up to 5 players per round"
    override fun playerLabel(n: Int) = "Player $n"
    override val remove = "Remove"
    override fun indexLabel(idx: Double) = "index ${"%.1f".format(idx)}"
    override val addPlayer = "＋ Add player"
    override val maxPlayers = "Maximum of 5 players reached"

    override val settingsTitle = "⚙️ Settings"
    override val languageTitle = "Language"
    override val unitsTitle = "Units"
    override val yards = "Yards"
    override val meters = "Meters"
    override val themeTitle = "Theme"
    override val themeSystem = "System"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val elevTitle = "Elevation (\"plays like\")"
    override val elevDesc = "The app learns each green's elevation when you walk onto it with GPS on. After one round, distances adjust automatically for uphill/downhill shots."
    override fun calibrated(n: Int) = "$n/18 greens calibrated"
    override val resetSmall = "✕ reset"
    override val clubsTitle = "Club distances (yd)"
    override val clubsDesc = "Used for the suggested club, per player"
    override val resetDefaults = "Reset to defaults"
    override val roundTitle = "Round"
    override val clearStrokes = "Clear strokes (without saving)"
    override val clearDialogTitle = "Clear all strokes?"
    override val clearDialogText = "This wipes the current scorecard for every player without saving it to history."
    override val yesClear = "Yes, clear"
    override val aboutText = "${CourseData.CITY} · 18 holes · Par ${CourseData.totalPar}\n" +
        "Screen stays awake during your round.\n" +
        "Distances measured by GPS (Haversine) to the center of the green."

    override val summary = "Summary"
}

val LocalStrings = staticCompositionLocalOf<AppStrings> { StringsEs }
