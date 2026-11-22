package ro.marius.boltcalc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Backup {
    private val KEYS = listOf("consum", "motorina", "comision", "maxPre", "minEx", "minBun", "minPret", "maxPret", "minRating")

    fun export(c: Context): String {
        val p = Params.prefs(c)
        val setari = JSONObject()
        for (k in KEYS) p.getString(k, null)?.let { setari.put(k, it) }
        return JSONObject()
            .put("v", 2)
            .put("exportat", System.currentTimeMillis())
            .put("setari", setari)
            .put("net", p.getBoolean("net", true))
            .put("blocate", Blocate.toJson(Blocate.load(c)))
            .put("favorite", Favorite.toJson(Favorite.load(c)))
            .put("gol", Gol.toJson(Gol.load(c)))
            .put("istoric", JSONArray(Istoric.rawJson(c)))
            .toString(2)
    }

    fun import(c: Context, text: String): Boolean {
        return try {
            val o = JSONObject(text)
            val setari = o.getJSONObject("setari")
            val blocate = Blocate.fromJson(o.getJSONArray("blocate"))
            val istoric = (o.optJSONArray("istoric") ?: JSONArray()).toString()
            val ed = Params.prefs(c).edit()
            for (k in KEYS) { if (setari.has(k)) ed.putString(k, setari.getString(k)) else ed.remove(k) }
            ed.putBoolean("net", o.optBoolean("net", true))
            ed.apply()
            Blocate.save(c, blocate)
            Gol.save(c, o.optJSONArray("gol")?.let { Gol.fromJson(it) } ?: emptyList())
            Favorite.save(c, o.optJSONArray("favorite")?.let { Favorite.fromJson(it) } ?: emptyList())
            Istoric.inlocuieste(c, istoric)
            true
        } catch (e: Exception) {
            false
        }
    }
}
