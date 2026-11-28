package ro.marius.boltcalc

import android.content.Context
import java.text.Normalizer

data class Params(
    val consumLa100: Double,
    val pretMotorina: Double,
    val comisionBolt: Double,
    val pretEsteNet: Boolean,
    val maxPreluareKm: Double,
    val minExcelent: Double,
    val minBun: Double,
    val minPret: Double?,
    val maxPret: Double?,
    val minRating: Double?,
    val blocate: List<Blocat>
) {
    companion object {
        private val DEFAULTS = mapOf(
            "consum" to "6.5", "motorina" to "10.90", "comision" to "0.25",
            "maxPre" to "3.5", "minEx" to "2.5", "minBun" to "1.5"
        )

        fun prefs(c: Context) = c.getSharedPreferences("boltcalc2", Context.MODE_PRIVATE)

        fun raw(c: Context, key: String): String = prefs(c).getString(key, null) ?: DEFAULTS[key] ?: ""

        private fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

        fun load(c: Context): Params {
            fun r(k: String) = raw(c, k)
            return Params(
                num(r("consum")) ?: 6.5,
                num(r("motorina")) ?: 10.90,
                num(r("comision")) ?: 0.25,
                prefs(c).getBoolean("net", true),
                num(r("maxPre")) ?: 3.5,
                num(r("minEx")) ?: 2.5,
                num(r("minBun")) ?: 1.5,
                num(r("minPret")),
                num(r("maxPret")),
                num(r("minRating")),
                Blocate.active(c)
            )
        }
    }
}

enum class Verdict { EXCELENT, BUN, REFUZA }

data class Oferta(
    val pret: Double,
    val preluareKm: Double,
    val cursaKm: Double,
    val preluareMin: Int?,
    val cursaMin: Int?,
    val rating: Double?,
    val text: String,
    val adrPreluare: String,
    val adrDestinatie: String,
    val nume: String = "",
    val programata: Boolean = false
)

data class Rezultat(
    val verdict: Verdict,
    val titlu: String,
    val motive: String,
    val ef: Double,
    val profit: Double,
    val cost: Double,
    val ora: Double?,
    val dist: Double,
    val net: Double,
    val minute: Int,
    val gaugeMax: Double,
    val avert: String
)

object Analyzer {

    fun analizeaza(o: Oferta, p: Params): Rezultat {
        val dist = (o.preluareKm + o.cursaKm).coerceAtLeast(0.1)
        val cost = dist * (p.consumLa100 / 100.0) * p.pretMotorina
        val net = if (p.pretEsteNet) o.pret else o.pret * (1.0 - p.comisionBolt)
        val profit = net - cost
        val ef = profit / dist
        val minute = (o.preluareMin ?: 0) + (o.cursaMin ?: 0)

        val why = ArrayList<String>()
        val hits = potriviri(o.text, p.blocate)
        hits.filter { it.refuza }.take(2).forEach { why.add("Evitată: " + it.descriere()) }
        val avert = hits.filter { !it.refuza }.take(2).joinToString("  •  ") { "Atenție: " + it.descriere() }
        val rating = o.rating
        if (p.minRating != null && rating != null && rating < p.minRating) why.add("Rating $rating sub ${p.minRating}")
        if (p.minPret != null && o.pret < p.minPret) why.add("Preț sub ${p.minPret} lei")
        if (p.maxPret != null && o.pret > p.maxPret) why.add("Preț peste ${p.maxPret} lei")
        if (o.preluareKm > p.maxPreluareKm) why.add("Preluare ${o.preluareKm} km")

        val verdict = when {
            why.isNotEmpty() -> Verdict.REFUZA
            ef >= p.minExcelent -> Verdict.EXCELENT
            ef >= p.minBun -> Verdict.BUN
            else -> Verdict.REFUZA
        }
        if (verdict == Verdict.REFUZA && why.isEmpty()) why.add("Pierzi pe motorină")

        val titlu = when (verdict) {
            Verdict.EXCELENT -> "EXCELENTĂ"
            Verdict.BUN -> "BUNĂ"
            Verdict.REFUZA -> "REFUZĂ"
        }
        val ora = if (minute > 0) profit / (minute / 60.0) else null
        return Rezultat(verdict, titlu, why.joinToString("  •  "), ef, profit, cost, ora, dist, net, minute, p.minExcelent * 1.4, avert)
    }

    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    // Potrivire pe cuvinte întregi: "Strada Deleni 7" nu prinde "Strada Deleni 70".
    private fun potriviri(text: String, lista: List<Blocat>): List<Blocat> {
        val hay = " " + Adresa.norm(text) + " "
        return lista.filter { val t = Adresa.norm(it.text); t.length >= 3 && hay.contains(" $t ") }
    }

    // Ecran Bolt (RO): "40,84 lei (NET, taxe incluse)", "+19,64 lei inclus", "5 min • 1.6 km", "Ioana • 5.0 ★"
    // Ecran Bolt (EN): "lei 13.00 (NET, tax included)"
    private val netPrice = Regex("""(\d+(?:[.,]\d{1,2})?)\s*lei\s*\(?\s*NET""", RegexOption.IGNORE_CASE)
    private val priceBefore = Regex("""(?<![+\d])\blei\W{0,3}(\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val priceAfter = Regex("""(?<![+\d.,])(\d+(?:[.,]\d{1,2})?)\s*(?:lei|ron)\b""", RegexOption.IGNORE_CASE)
    private val kmRe = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m)(?![a-zA-Z])""", RegexOption.IGNORE_CASE)
    private val minRe = Regex("""(\d+)\s*min\b""", RegexOption.IGNORE_CASE)
    private val ratingStar = Regex("""(?<![\d.,])([1-5][.,]\d{1,2})\s*★""")
    private val ratingDot = Regex("""[•·]\s*([1-5][.,]\d{1,2})\b(?!\s*(?:km|min))""")

    private fun n(s: String) = s.replace(',', '.').toDouble()

    /**
     * La cursă-în-cursă, cardul ofertei apare peste ecranul cursei în curs. Citim doar textele de la
     * prețul cel mai apropiat de buton ("Acceptă", "Acceptă următoarea cursă", "Acceptare automată") până la buton.
     */
    private fun taieLaOferta(texts: List<String>): List<String> {
        val b = texts.indexOfLast { norm(it).contains("accept") }
        if (b <= 0) return texts
        val p = (b downTo 0).firstOrNull {
            netPrice.containsMatchIn(texts[it]) || priceBefore.containsMatchIn(texts[it]) || priceAfter.containsMatchIn(texts[it])
        } ?: return texts
        return texts.subList(p, b + 1)
    }

    // Oferta există doar când pe ecran e butonul de acceptare. În navigația spre client (preț + distanță de preluare,
    // fără buton) nu afișăm nimic, altfel scorul ar fi fals.
    fun parseaza(texts: List<String>): Oferta? {
        if (texts.none { norm(it).contains("accept") }) return null
        return parseazaBrut(taieLaOferta(texts)) ?: parseazaBrut(texts)
    }

    // "Ioana Lucia • 5.0 ★" -> "IOANA LUCIA"
    private fun numeClient(texts: List<String>): String {
        for (t in texts) {
            if (ratingStar.containsMatchIn(t) || ratingDot.containsMatchIn(t)) {
                val nm = t.substringBefore('•').substringBefore('·').replace("★", "").trim()
                if (nm.any { it.isLetter() } && !distNode.containsMatchIn(nm)) return nm.uppercase(java.util.Locale("ro"))
            }
        }
        return ""
    }

    /** Câte distanțe (km/m) are textul ofertei: peste 2 înseamnă oprire/opriri intermediare (diagnostic). */
    fun nrDistante(text: String): Int = kmRe.findAll(text).count()

    /** Ecran care seamănă a ofertă (preț + două distanțe), chiar dacă butonul încă lipsește. */
    fun pareOferta(texts: List<String>): Boolean = parseazaBrut(texts) != null

    private fun parseazaBrut(texts: List<String>): Oferta? {
        val all = texts.joinToString(" ")
        val priceStr = netPrice.find(all)?.groupValues?.get(1)
            ?: priceBefore.find(all)?.groupValues?.get(1)
            ?: priceAfter.find(all)?.groupValues?.get(1)
            ?: return null
        val dist = kmRe.findAll(all).map {
            val x = n(it.groupValues[1])
            if (it.groupValues[2].lowercase() == "m") x / 1000.0 else x
        }.toList()
        // Cursă programată: scrie "programat" sus, are un singur kilometraj (fără preluare), adrese "în apropiere de ..."
        val na = norm(all)
        val prog = na.contains("programat") || na.contains("scheduled") || na.contains("in apropiere de")
        if (dist.isEmpty() || (dist.size < 2 && !prog)) return null
        val mins = minRe.findAll(all).map { it.groupValues[1].toInt() }.toList()
        val rating = (ratingStar.find(all) ?: ratingDot.find(all))?.groupValues?.get(1)?.let { n(it) }
        val (adrP, adrD) = adrese(texts)
        // Cursă programată: un singur kilometraj (uneori citit de două ori), fără preluare și fără minute.
        if (prog) return Oferta(n(priceStr), 0.0, dist[0], null, null, rating, all, adrP, adrD, numeClient(texts), true)
        return Oferta(n(priceStr), dist[0], dist[1], mins.getOrNull(0), mins.getOrNull(1), rating, all, adrP, adrD, numeClient(texts), prog)
    }

    private val numOnly = Regex("""^\s*(\d+[.,]\d{1,2})\s*$""")

    /** Preț final de pe ecranul "Confirmă tariful": primul preț cu "lei" după titlu (apoi înainte), altfel un număr zecimal singur. */
    fun pretFinal(texts: List<String>): Double? {
        val i = texts.indexOfFirst { norm(it).contains("confirma tariful") }
        if (i < 0) return null
        fun cuLei(t: String): Double? = (netPrice.find(t) ?: priceBefore.find(t) ?: priceAfter.find(t))?.groupValues?.get(1)?.let { n(it) }
        fun simplu(t: String): Double? = numOnly.find(t)?.groupValues?.get(1)?.let { n(it) }
        for (k in i + 1 until texts.size) cuLei(texts[k])?.let { return it }
        for (k in i - 1 downTo 0) cuLei(texts[k])?.let { return it }
        for (k in i + 1 until texts.size) simplu(texts[k])?.let { return it }
        for (k in i - 1 downTo 0) simplu(texts[k])?.let { return it }
        return null
    }

    data class CardProgramat(val pret: Double, val km: Double, val cand: String)

    private val cardPret = Regex("""^\s*(\d+(?:[.,]\d{1,2})?)\s*lei""", RegexOption.IGNORE_CASE)
    private val kmLista = Regex("""(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val oraRe = Regex("""\d{1,2}:\d{2}""")

    /**
     * Ecranul "Curse programate" e o listă de carduri: "14,92 lei • 24 nov., 07:20–07:25", apoi "3.8km", apoi adrese.
     * Fiecare card se calculează separat (un singur kilometraj, fără preluare).
     */
    fun parseazaLista(texts: List<String>): List<CardProgramat> {
        if (texts.none { norm(it).contains("curse programate") }) return emptyList()
        val out = ArrayList<CardProgramat>()
        var i = 0
        while (i < texts.size) {
            val m = cardPret.find(texts[i])
            if (m == null) { i++; continue }
            val pret = n(m.groupValues[1])
            var cand = texts[i].substringAfter('•', "").trim()
            var km: Double? = null
            var j = i + 1
            while (j < texts.size && cardPret.find(texts[j]) == null) {
                if (km == null) kmLista.find(texts[j])?.let { km = n(it.groupValues[1]) }
                if (cand.isEmpty() && oraRe.containsMatchIn(texts[j])) cand = texts[j].trim()
                j++
            }
            val k = km
            if (k != null && k > 0.0) out.add(CardProgramat(pret, k, cand))
            i = j
        }
        return out
    }

    // Adresele sunt textele dintre cele două distanțe (preluare) și după a doua distanță, până la butoane (destinație).
    private val distNode = Regex("""\d+(?:[.,]\d+)?\s*(?:km|m)\b""", RegexOption.IGNORE_CASE)
    private val minOnly = Regex("""^\d+ min$""")
    private val butoane = listOf("accept", "declin", "refuz", "respin")

    private fun adrese(texts: List<String>): Pair<String, String> {
        val idx = texts.indices.filter { distNode.containsMatchIn(texts[it]) }
        if (idx.size < 2) return "" to ""
        fun ok(s: String): Boolean {
            val t = norm(s)
            return t.isNotEmpty() && !minOnly.matches(t) && !distNode.containsMatchIn(s)
        }
        val pre = texts.subList(idx[0] + 1, idx[1]).filter { ok(it) }.take(3)
        val dest = ArrayList<String>()
        for (i in idx[1] + 1 until texts.size) {
            val t = norm(texts[i])
            if (butoane.any { t.contains(it) }) break
            if (ok(texts[i])) dest.add(texts[i])
            if (dest.size >= 3) break
        }
        return pre.joinToString(" ") to dest.joinToString(" ")
    }
}
