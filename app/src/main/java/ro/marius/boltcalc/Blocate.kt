package ro.marius.boltcalc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Motive {
    val TOATE = listOf(
        "lucru" to "Drum în lucru",
        "pietruit" to "Drum pietruit",
        "pamant" to "Drum cu pământ",
        "periculos" to "Zonă periculoasă",
        "gropi" to "Drum cu gropi",
        "ingust" to "Drum îngust",
        "altele" to "Altele"
    )

    fun eticheta(k: String): String = TOATE.firstOrNull { it.first == k }?.second ?: k
}

data class Blocat(
    val id: Long,
    var start: Long,
    var text: String,
    var motive: List<String>,
    var principal: String,
    var nota: String,
    var zile: Int,          // 30, 60 sau 0 = nelimitat
    var refuza: Boolean     // true = refuză, false = doar avertizează
) {
    fun expiraLa(): Long? = if (zile <= 0) null else start + zile * 86_400_000L

    fun activ(now: Long = System.currentTimeMillis()): Boolean {
        val e = expiraLa()
        return e == null || now < e
    }

    fun etichetaPrincipala(): String =
        if (principal == "altele" && nota.isNotBlank()) nota.trim() else Motive.eticheta(principal)

    fun descriere(): String {
        val alte = if (motive.size > 1) " +${motive.size - 1}" else ""
        val exp = expiraLa()?.let { ", până ${dataScurta(it)}" } ?: ""
        return "$text (${etichetaPrincipala()}$alte$exp)"
    }

    companion object {
        fun dataScurta(t: Long): String = SimpleDateFormat("d MMM", Locale("ro")).format(Date(t))
    }
}

object Blocate {
    private const val KEY = "blocate"

    fun toJson(list: List<Blocat>): JSONArray {
        val a = JSONArray()
        for (b in list) {
            a.put(
                JSONObject().put("id", b.id).put("start", b.start).put("text", b.text)
                    .put("motive", JSONArray(b.motive)).put("principal", b.principal)
                    .put("nota", b.nota).put("zile", b.zile).put("refuza", b.refuza)
            )
        }
        return a
    }

    fun fromJson(a: JSONArray): MutableList<Blocat> {
        val out = ArrayList<Blocat>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val m = o.getJSONArray("motive")
            val motive = (0 until m.length()).map { m.getString(it) }
            out.add(
                Blocat(
                    o.getLong("id"), o.getLong("start"), o.getString("text"), motive,
                    o.optString("principal", motive.firstOrNull() ?: "altele"), o.optString("nota", ""),
                    o.optInt("zile", 0), o.optBoolean("refuza", true)
                )
            )
        }
        return out
    }

    fun load(c: Context): MutableList<Blocat> {
        val p = Params.prefs(c)
        val s = p.getString(KEY, null)
        if (s == null) {
            // Migrare din vechea listă text (câte o adresă pe rând)
            val now = System.currentTimeMillis()
            val vechi = (p.getString("avoid", "") ?: "").lines().map { it.trim() }.filter { it.length >= 3 }
            val list = vechi.mapIndexed { i, t -> Blocat(now + i, now, t, listOf("altele"), "altele", "", 0, true) }.toMutableList()
            save(c, list)
            return list
        }
        return try { fromJson(JSONArray(s)) } catch (e: Exception) { ArrayList() }
    }

    fun save(c: Context, list: List<Blocat>) {
        Params.prefs(c).edit().putString(KEY, toJson(list).toString()).apply()
    }

    fun salveaza(c: Context, b: Blocat) {
        val l = load(c)
        val i = l.indexOfFirst { it.id == b.id }
        if (i >= 0) l[i] = b else l.add(0, b)
        save(c, l)
    }

    fun sterge(c: Context, id: Long) {
        save(c, load(c).filter { it.id != id })
    }

    fun active(c: Context): List<Blocat> = load(c).filter { it.activ() }
}
