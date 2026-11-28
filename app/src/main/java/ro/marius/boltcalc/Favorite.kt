package ro.marius.boltcalc

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

/** Client + adresă marcate cu inimioară (de ex. lasă bacșiș). Potrivire sigură = nume + adresă. */
data class Favorit(val id: Long, val nume: String, val adresa: String, val tip: String)

object Favorite {
    const val NIMIC = 0
    const val ADRESA = 1      // doar adresa se potrivește
    const val SIGUR = 2       // nume + adresă
    val TIPURI = listOf("Acasă", "Serviciu", "Altceva")

    private fun prefs(c: Context) = c.getSharedPreferences("boltcalc2", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Favorit> {
        val out = ArrayList<Favorit>()
        try {
            val a = JSONArray(prefs(c).getString("favorite", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Favorit(o.getLong("id"), o.optString("nume", ""), o.getString("adresa"), o.optString("tip", "")))
            }
        } catch (e: Exception) { }
        return out
    }

    fun toJson(l: List<Favorit>): JSONArray {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("nume", it.nume).put("adresa", it.adresa).put("tip", it.tip)) }
        return a
    }

    fun save(c: Context, l: List<Favorit>) { prefs(c).edit().putString("favorite", toJson(l).toString()).apply() }

    fun fromJson(a: JSONArray): List<Favorit> =
        (0 until a.length()).map { val o = a.getJSONObject(it); Favorit(o.getLong("id"), o.optString("nume", ""), o.getString("adresa"), o.optString("tip", "")) }

    fun adauga(c: Context, f: Favorit) { val l = load(c); l.add(f); save(c, l) }
    fun sterge(c: Context, id: Long) { save(c, load(c).filter { it.id != id }) }

    private fun areAdresa(text: String, adresa: String): Boolean {
        val t = Adresa.norm(adresa)
        return t.length >= 3 && (" " + Adresa.norm(text) + " ").contains(" $t ")
    }

    /** Potrivire pentru o ofertă: textul ei conține adresa; numele clientului trebuie să fie același pentru potrivire sigură. */
    fun potrivire(c: Context, nume: String, text: String): Int {
        var best = NIMIC
        for (f in load(c)) {
            if (!areAdresa(text, f.adresa)) continue
            val n = f.nume.isNotEmpty() && Analyzer.norm(f.nume) == Analyzer.norm(nume)
            if (n) return SIGUR
            best = ADRESA
        }
        return best
    }

    fun potrivireIntrare(c: Context, e: Intrare) = potrivire(c, e.nume, e.adrP + " " + e.adrD)

    /** Dialog: alegi adresa (preluare sau destinație), tipul și salvezi. */
    fun dialog(ctx: Context, e: Intrare, dupa: () -> Unit) {
        val dp = ctx.resources.displayMetrics.density
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), 0) }
        val nume = EditText(ctx).apply { hint = "Nume client"; setText(e.nume); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS }
        col.addView(nume)
        val adr = EditText(ctx).apply { hint = "Adresa (stradă + număr)"; inputType = InputType.TYPE_CLASS_TEXT }
        val rg = RadioGroup(ctx)
        val opt = listOf("De la" to e.adrP, "La" to e.adrD).filter { it.second.isNotEmpty() }
        opt.forEachIndexed { i, (lbl, a) ->
            rg.addView(RadioButton(ctx).apply { id = 1000 + i; text = "$lbl: $a" })
        }
        rg.setOnCheckedChangeListener { _, id -> adr.setText(opt[id - 1000].second.substringBefore(',').trim()) }
        col.addView(rg); col.addView(adr)
        val tip = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, TIPURI) }
        col.addView(tip)
        if (opt.isNotEmpty()) rg.check(1000)
        AlertDialog.Builder(ctx).setTitle("♥ Client / adresă preferată").setView(col)
            .setPositiveButton("Salvează") { _, _ ->
                val a = adr.text.toString().trim()
                if (a.length >= 3) {
                    adauga(ctx, Favorit(System.currentTimeMillis(), nume.text.toString().trim().uppercase(java.util.Locale("ro")), a, TIPURI[tip.selectedItemPosition]))
                    Toast.makeText(ctx, "Salvat ♥", Toast.LENGTH_SHORT).show()
                    dupa()
                } else Toast.makeText(ctx, "Adresa e prea scurtă", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Anulează", null).show()
    }

    /** Adăugare / editare manuală: nume (opțional), adresă, tip. Fără nume = potrivire doar pe adresă. */
    fun manual(ctx: Context, f: Favorit?, dupa: () -> Unit) {
        val dp = ctx.resources.displayMetrics.density
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), 0) }
        val nume = EditText(ctx).apply { hint = "Nume client (opțional)"; setText(f?.nume ?: ""); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS }
        val adr = EditText(ctx).apply { hint = "Adresa (ex: Strada Deleni 7)"; setText(f?.adresa ?: ""); inputType = InputType.TYPE_CLASS_TEXT }
        val tip = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, TIPURI)
            setSelection(TIPURI.indexOf(f?.tip ?: "").coerceAtLeast(0))
        }
        col.addView(nume); col.addView(adr); col.addView(tip)
        val b = AlertDialog.Builder(ctx).setTitle(if (f == null) "♥ Adaugă preferat" else "♥ Editează preferat").setView(col)
            .setPositiveButton("Salvează") { _, _ ->
                val a = adr.text.toString().trim()
                if (a.length < 3) { Toast.makeText(ctx, "Adresa e prea scurtă", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val nou = Favorit(f?.id ?: System.currentTimeMillis(), nume.text.toString().trim().uppercase(java.util.Locale("ro")), a, TIPURI[tip.selectedItemPosition])
                save(ctx, load(ctx).filter { it.id != nou.id } + nou)
                dupa()
            }
            .setNegativeButton("Anulează", null)
        if (f != null) b.setNeutralButton("Șterge") { _, _ -> sterge(ctx, f.id); dupa() }
        b.show()
    }

    /** Dacă oferta e deja potrivită: oferă ștergerea favoritelor care se potrivesc. */
    fun dialogSterge(ctx: Context, e: Intrare, dupa: () -> Unit) {
        val text = e.adrP + " " + e.adrD
        val l = load(ctx).filter { areAdresa(text, it.adresa) }
        if (l.isEmpty()) return
        AlertDialog.Builder(ctx).setTitle("♥ Șterge din preferate?")
            .setMessage(l.joinToString("\n") { (if (it.nume.isNotEmpty()) it.nume + " – " else "") + it.adresa + " (" + it.tip + ")" })
            .setPositiveButton("Șterge") { _, _ -> l.forEach { sterge(ctx, it.id) }; dupa() }
            .setNeutralButton("Adaugă altă") { _, _ -> dialog(ctx, e, dupa) }
            .setNegativeButton("Anulează", null).show()
    }
}
