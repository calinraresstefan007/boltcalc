package ro.marius.boltcalc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Km în gol: distanța parcursă între încheierea unei curse și acceptarea următoarei. Separat de km curse. */
data class SegmentGol(val id: Long, val km: Double)

object Gol {
    private fun prefs(c: Context) = c.getSharedPreferences("gol", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<SegmentGol> {
        val out = ArrayList<SegmentGol>()
        try {
            val a = JSONArray(prefs(c).getString("items", "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); out.add(SegmentGol(o.getLong("id"), o.getDouble("km"))) }
        } catch (e: Exception) { }
        return out
    }

    fun toJson(l: List<SegmentGol>): JSONArray {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("km", it.km)) }; return a
    }

    fun save(c: Context, l: List<SegmentGol>) { prefs(c).edit().putString("items", toJson(l).toString()).apply() }

    fun fromJson(a: JSONArray): List<SegmentGol> = (0 until a.length()).map { val o = a.getJSONObject(it); SegmentGol(o.getLong("id"), o.getDouble("km")) }

    fun adauga(c: Context, km: Double) {
        val l = load(c); l.add(SegmentGol(System.currentTimeMillis(), km))
        while (l.size > 2000) l.removeAt(0)
        save(c, l)
    }

    fun total(c: Context) = load(c).sumOf { it.km }

    /** Șterge segmentele mai noi de [dela]. */
    fun reseteaza(c: Context, dela: Long) { save(c, load(c).filter { it.id < dela }) }
}
