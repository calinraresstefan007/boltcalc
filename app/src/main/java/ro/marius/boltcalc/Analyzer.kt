package ro.marius.boltcalc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Bonus(var amount: Double, var target: Int, var done: Int, val endMillis: Long, var on: Boolean)

data class Params(
    val consumLa100: Double = 6.5,
    val pretMotorina: Double = 10.90,
    val comisionBolt: Double = 0.25,
    val pretEsteNet: Boolean = true,      // Bolt arată "NET, tax included"
    val bonusExtra: Double = 0.0,
    val maxPreluareKm: Double = 3.5,
    val minExcelent: Double = 2.5,
    val minBun: Double = 1.5,
    val bonuses: List<Bonus> = emptyList()
) {
    fun bonusPerCursa(): Double {
        val now = System.currentTimeMillis()
        var t = bonusExtra
        for (b in bonuses) if (b.on && b.done < b.target && now < b.endMillis) t += b.amount / (b.target - b.done)
        return t
    }

    companion object {
        private fun defaultBonuses() = listOf(
            Bonus(185.0, 90, 64, 1793680440000L, true),   // 3 nov 2026 06:34 (EET)
            Bonus(580.0, 230, 64, 1794890040000L, true)
        )
        fun prefs(c: Context) = c.getSharedPreferences("bolt", Context.MODE_PRIVATE)

        fun load(c: Context): Params {
            val p = prefs(c); val d = Params()
            val bons = try {
                val a = JSONArray(p.getString("bonuses", null) ?: return d.copy(bonuses = defaultBonuses()))
                (0 until a.length()).map { val o = a.getJSONObject(it)
                    Bonus(o.getDouble("amount"), o.getInt("target"), o.getInt("done"), o.getLong("end"), o.getBoolean("on")) }
            } catch (e: Exception) { defaultBonuses() }
            return Params(
                p.getFloat("consum", d.consumLa100.toFloat()).toDouble(),
                p.getFloat("motorina", d.pretMotorina.toFloat()).toDouble(),
                p.getFloat("comision", d.comisionBolt.toFloat()).toDouble(),
                p.getBoolean("net", d.pretEsteNet),
                p.getFloat("bonusExtra", 0f).toDouble(),
                p.getFloat("maxPreluare", d.maxPreluareKm.toFloat()).toDouble(),
                p.getFloat("minExcelent", d.minExcelent.toFloat()).toDouble(),
                p.getFloat("minBun", d.minBun.toFloat()).toDouble(),
                bons
            )
        }

        fun saveBonuses(c: Context, list: List<Bonus>) {
            val a = JSONArray()
            list.forEach { a.put(JSONObject().put("amount", it.amount).put("target", it.target)
                .put("done", it.done).put("end", it.endMillis).put("on", it.on)) }
            prefs(c).edit().putString("bonuses", a.toString()).apply()
        }
    }
}

enum class Verdict { EXCELENT, BUN, REFUZA }

data class Rezultat(
    val verdict: Verdict, val text: String, val profit: Double, val ef: Double,
    val bonus: Double, val profitFaraBonus: Double, val efFaraBonus: Double, val cost: Double
)

data class Oferta(val pret: Double, val preluareKm: Double, val cursaKm: Double)

object Analyzer {
    fun analizeaza(o: Oferta, p: Params): Rezultat {
        val dist = (o.preluareKm + o.cursaKm).coerceAtLeast(0.1)
        val cost = dist * (p.consumLa100 / 100) * p.pretMotorina
        val net = if (p.pretEsteNet) o.pret else o.pret * (1 - p.comisionBolt)
        val bonus = p.bonusPerCursa()
        val profit = net + bonus - cost
        val ef = profit / dist
        val (v, t) = when {
            o.preluareKm > p.maxPreluareKm -> Verdict.REFUZA to "❌ REFUZĂ (preluare prea lungă)"
            ef >= p.minExcelent -> Verdict.EXCELENT to "⭐ EXCELENTĂ! IA-O!"
            ef >= p.minBun -> Verdict.BUN to "✔️ IA-O (bun pt bonus)"
            else -> Verdict.REFUZA to "❌ REFUZĂ (pierzi pe motorină)"
        }
        return Rezultat(v, t, profit, ef, bonus, net - cost, (net - cost) / dist, cost)
    }

    // Bolt: "lei 13.00 (NET, tax included)", "3 min • 2.1 km", "9 min • 5.4 km"
    private val kmRe = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m)(?![a-zA-Z])""", RegexOption.IGNORE_CASE)
    private val priceBefore = Regex("""\blei\W{0,3}(\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val priceAfter = Regex("""(\d+(?:[.,]\d{1,2})?)\s*(?:lei|ron)\b""", RegexOption.IGNORE_CASE)
    private fun num(s: String) = s.replace(',', '.').toDouble()

    fun parseaza(texts: List<String>): Oferta? {
        val all = texts.joinToString(" ")
        val m = priceBefore.find(all) ?: priceAfter.find(all) ?: return null
        val dist = kmRe.findAll(all).map {
            val x = num(it.groupValues[1]); if (it.groupValues[2].lowercase() == "m") x / 1000.0 else x
        }.toList()
        if (dist.size < 2) return null
        return Oferta(num(m.groupValues[1]), dist[0], dist[1])
    }
}
