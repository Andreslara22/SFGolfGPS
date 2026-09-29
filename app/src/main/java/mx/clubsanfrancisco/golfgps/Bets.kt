package mx.clubsanfrancisco.golfgps

/**
 * Apuesta por puntos Stableford (con handicap), dividida en tres rondas:
 * Front 9, Back 9 y General (18). Cada jugador pone [GolfViewModel.betAmount]
 * por ronda (ej. 100 por ronda = 300 en total) y la bolsa de cada ronda se
 * reparte:
 *  - Automático: por lugar según los puntos, con los % de 1º/2º/3º. Los
 *    empates juntan los % de los lugares que ocupan y los parten en partes
 *    iguales. Si hay menos jugadores que lugares con %, los % se reescalan
 *    sobre los lugares que sí existen (la bolsa siempre se reparte completa).
 *  - Manual: el usuario elige quién se lleva la bolsa completa de esa ronda.
 */
object Bets {

    /** Rango de hoyos de cada ronda: 0 front · 1 back · 2 general. */
    val segments: List<IntRange> = listOf(0 until 9, 9 until 18, 0 until 18)

    class SegmentResult(
        /** Puntos de cada jugador en la ronda. */
        val points: List<Int>,
        /** Lugar de cada jugador (1 = primero; empatados comparten lugar). */
        val places: List<Int>,
        /** Premio que cobra cada jugador de la bolsa de esta ronda. */
        val prizes: List<Double>,
        val pot: Int,
        /** true si el ganador lo eligió el usuario. */
        val manual: Boolean
    )

    fun segment(
        players: List<Player>, segment: Int, amount: Int,
        payoutPct: List<Int>, manualWinner: Int
    ): SegmentResult {
        val n = players.size
        val pts = players.map { it.stablefordPoints(segments[segment]) }
        val places = pts.map { p -> 1 + pts.count { it > p } }
        val pot = amount * n

        if (manualWinner in 0 until n) {
            return SegmentResult(pts, places,
                List(n) { if (it == manualWinner) pot.toDouble() else 0.0 }, pot, true)
        }

        // % por posición 0..n-1 (reescalado si hay menos jugadores que lugares).
        val raw = List(n) { payoutPct.getOrElse(it) { 0 }.toDouble() }
        val sum = raw.sum()
        val share = if (sum > 0) raw.map { it / sum } else List(n) { if (it == 0) 1.0 else 0.0 }

        // Nadie ha anotado en esta ronda: todavía no hay reparto.
        if (players.none { p -> segments[segment].any { p.strokes[it] > 0 } }) {
            return SegmentResult(pts, places, List(n) { 0.0 }, pot, false)
        }

        val prizes = MutableList(n) { 0.0 }
        pts.distinct().forEach { value ->
            val tied = pts.indices.filter { pts[it] == value }
            val first = places[tied.first()] - 1
            val groupShare = (first until first + tied.size).sumOf { share.getOrElse(it) { 0.0 } }
            tied.forEach { prizes[it] = pot * groupShare / tied.size }
        }
        return SegmentResult(pts, places, prizes, pot, false)
    }

    /** Resultado de las tres rondas. */
    fun all(vm: GolfViewModel): List<SegmentResult> =
        (0..2).map { segment(vm.players, it, vm.betAmount, vm.betPayout, vm.betWinners[it]) }

    /** Neto de cada jugador: lo que cobra en las tres rondas menos lo que apostó. */
    fun net(vm: GolfViewModel, results: List<SegmentResult> = all(vm)): List<Double> =
        vm.players.indices.map { i -> results.sumOf { it.prizes[i] } - 3.0 * vm.betAmount }
}
