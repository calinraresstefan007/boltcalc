package ro.marius.boltcalc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Intrare(
    val id: Long,
    val pret: Double,
    val preluareKm: Double,
    val cursaKm: Double,
    val minute: Int,
    val rating: Double?,
    val ef: Double,
    val profit: Double,
    val verdict: String,
    val motive: String,
    val adrP: String,
    val adrD: String,
    var status: String
)

object Istoric {
    const val VAZUT = "vazut"
    const val ACCEPTAT = "acceptat"
    const val REFUZAT = "refuzat"
    const val EXPIRAT = "expirat"
    private const val MAX = 500

    private fun prefs(c: Context) = c.getSharedPreferences("istoric", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Intrare> {
        val out = ArrayList<Intrare>()
        try {
            val a = JSONArray(prefs(c).getString("items", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val r = o.optDouble("rating", Double.NaN)
                out.add(
                    Intrare(
                        o.getLong("id"), o.getDouble("pret"), o.getDouble("pre"), o.getDouble("cur"),
                        o.getInt("min"), if (r.isNaN()) null else r, o.getDouble("ef"), o.getDouble("profit"),
                        o.getString("verdict"), o.optString("motive", ""), o.optString("adrP", ""), o.optString("adrD", ""), o.getString("status")
                    )
                )
            }
        } catch (e: Exception) { }
        return out
    }

    private fun save(c: Context, list: List<Intrare>) {
        val a = JSONArray()
        for (e in list) {
            val o = JSONObject()
                .put("id", e.id).put("pret", e.pret).put("pre", e.preluareKm).put("cur", e.cursaKm)
                .put("min", e.minute).put("ef", e.ef).put("profit", e.profit)
                .put("verdict", e.verdict).put("motive", e.motive).put("adrP", e.adrP).put("adrD", e.adrD).put("status", e.status)
            if (e.rating != null) o.put("rating", e.rating)
            a.put(o)
        }
        prefs(c).edit().putString("items", a.toString()).apply()
    }

    fun adauga(c: Context, e: Intrare) {
        val l = load(c)
        l.add(0, e)
        while (l.size > MAX) l.removeAt(l.size - 1)
        save(c, l)
    }

    fun seteazaStatus(c: Context, id: Long, status: String) {
        val l = load(c)
        l.firstOrNull { it.id == id }?.status = status
        save(c, l)
    }

    /** Oferta a dispărut fără click detectat: o marcăm expirată. */
    fun inchide(c: Context, id: Long) {
        val l = load(c)
        val e = l.firstOrNull { it.id == id } ?: return
        if (e.status == VAZUT) { e.status = EXPIRAT; save(c, l) }
    }

    fun rawJson(c: Context): String = prefs(c).getString("items", "[]") ?: "[]"

    fun inlocuieste(c: Context, json: String) { prefs(c).edit().putString("items", json).apply() }

    fun sterge(c: Context) { prefs(c).edit().remove("items").apply() }
}
