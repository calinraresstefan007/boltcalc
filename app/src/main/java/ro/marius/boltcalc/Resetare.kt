package ro.marius.boltcalc

import android.app.AlertDialog
import android.content.Context
import android.widget.*

/** Resetare selectivă: bifezi tipurile de curse și măsurătorile de șters, apoi perioada. */
object Resetare {
    private val CHEI_SETARI = listOf("consum", "motorina", "comision", "maxPre", "minEx", "minBun", "minPret", "maxPret", "minRating", "net")

    fun dialog(ctx: Context, dupa: () -> Unit) {
        val dp = ctx.resources.displayMetrics.density
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0) }
        fun titlu(t: String) = col.addView(TextView(ctx).apply { text = t; textSize = 14f; setPadding(0, (10 * dp).toInt(), 0, (2 * dp).toInt()) })
        fun cb(t: String) = CheckBox(ctx).apply { text = t }.also { col.addView(it) }

        titlu("Curse din istoric (șterge complet tipurile bifate):")
        val cAcc = cb("Acceptate (inclusiv automate)")
        val cRef = cb("Refuzate")
        val cExp = cb("Expirate / în curs")
        val cAnul = cb("Anulate")
        titlu("Măsurători GPS:")
        val cKm = cb("Km curse (km reali + profit real, din cardurile cursei)")
        val cProfit = cb("Doar profit real și lei/km real (km curse rămân)")
        val cGol = cb("Km în gol")
        titlu("Perioada (pentru tot ce e mai sus):")
        val rg = RadioGroup(ctx)
        val per = listOf("Tot", "Doar azi", "Ultimele 7 zile")
        per.forEachIndexed { i, t -> rg.addView(RadioButton(ctx).apply { id = 2000 + i; text = t }) }
        rg.check(2000)
        col.addView(rg)
        titlu("Alte date (se șterg complet):")
        val cFav = cb("Inimioare (clienți și adrese preferate)")
        val cBloc = cb("Adrese evitate")
        val cSet = cb("Setări: costuri, praguri, filtre (revin la valorile standard)")

        AlertDialog.Builder(ctx).setTitle("Resetare").setView(ScrollView(ctx).apply { addView(col) })
            .setPositiveButton("Continuă") { _, _ ->
                val alese = ArrayList<String>()
                if (cAcc.isChecked) alese.add("curse acceptate")
                if (cRef.isChecked) alese.add("curse refuzate")
                if (cExp.isChecked) alese.add("curse expirate/în curs")
                if (cAnul.isChecked) alese.add("curse anulate")
                if (cKm.isChecked) alese.add("km curse")
                if (cProfit.isChecked) alese.add("profit real")
                if (cGol.isChecked) alese.add("km în gol")
                if (cFav.isChecked) alese.add("inimioare")
                if (cBloc.isChecked) alese.add("adrese evitate")
                if (cSet.isChecked) alese.add("setări")
                if (alese.isEmpty()) { Toast.makeText(ctx, "Nu ai bifat nimic", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val p = per[rg.checkedRadioButtonId - 2000]
                val dela = when (p) {
                    "Doar azi" -> java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    "Ultimele 7 zile" -> System.currentTimeMillis() - 7 * 86_400_000L
                    else -> 0L
                }
                AlertDialog.Builder(ctx).setTitle("Sigur?")
                    .setMessage("Se vor șterge: " + alese.joinToString(", ") + ".\nPerioadă (curse și măsurători): $p.\nAcțiunea nu se poate anula. Fă mai întâi o copie de siguranță dacă nu ești sigur.")
                    .setPositiveButton("Resetează") { _, _ ->
                        val st = HashSet<String>()
                        if (cAcc.isChecked) { st.add(Istoric.ACCEPTAT); st.add(Istoric.AUTO) }
                        if (cRef.isChecked) st.add(Istoric.REFUZAT)
                        if (cExp.isChecked) { st.add(Istoric.EXPIRAT); st.add(Istoric.VAZUT) }
                        if (cAnul.isChecked) st.add(Istoric.ANULAT)
                        if (st.isNotEmpty() || cKm.isChecked || cProfit.isChecked)
                            Istoric.reseteaza(ctx, dela, st, cKm.isChecked, cProfit.isChecked)
                        if (cGol.isChecked) Gol.reseteaza(ctx, dela)
                        if (cFav.isChecked) Favorite.save(ctx, emptyList())
                        if (cBloc.isChecked) Blocate.save(ctx, emptyList())
                        if (cSet.isChecked) {
                            val e = Params.prefs(ctx).edit()
                            CHEI_SETARI.forEach { e.remove(it) }
                            e.apply()
                        }
                        Toast.makeText(ctx, "Resetat", Toast.LENGTH_SHORT).show()
                        dupa()
                    }
                    .setNegativeButton("Anulează", null).show()
            }
            .setNegativeButton("Anulează", null).show()
    }
}
