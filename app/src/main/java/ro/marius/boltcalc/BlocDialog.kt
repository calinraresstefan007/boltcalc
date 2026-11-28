package ro.marius.boltcalc

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

object BlocDialog {

    fun arata(
        a: Activity,
        init: Blocat?,
        textInitial: String,
        onSave: (Blocat) -> Unit,
        onDelete: (() -> Unit)?
    ) {
        val d = a.resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        fun label(t: String) = col.addView(TextView(a).apply { text = t; textSize = 13f; setPadding(0, dp(12), 0, dp(2)) })

        val (tip0, nume0, nr0) = Adresa.imparte(init?.text ?: textInitial)
        label("Tipul străzii")
        val spTip = Spinner(a)
        spTip.adapter = ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, Adresa.TIPURI.map { it.second })
        spTip.setSelection(Adresa.TIPURI.indexOfFirst { it.first == tip0 }.coerceAtLeast(0))
        col.addView(spTip)
        label("Numele străzii")
        val nume = EditText(a).apply { setText(nume0); hint = "ex: Deleni" }
        col.addView(nume)
        label("Număr (opțional, gol = toată strada)")
        val nr = EditText(a).apply { setText(nr0); hint = "ex: 7" }
        col.addView(nr)

        label("Motive (poți bifa mai multe)")
        val checks = LinkedHashMap<String, CheckBox>()
        val nota = EditText(a).apply { hint = "Notă"; setText(init?.nota ?: ""); visibility = View.GONE }
        val spin = Spinner(a)
        var princ = init?.principal ?: ""
        var curKeys: List<String> = emptyList()

        fun refreshSpin() {
            curKeys = checks.filter { it.value.isChecked }.keys.toList()
            if (princ !in curKeys) princ = curKeys.firstOrNull() ?: ""
            spin.adapter = ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, curKeys.map { Motive.eticheta(it) })
            val idx = curKeys.indexOf(princ)
            if (idx >= 0) spin.setSelection(idx)
            nota.visibility = if (checks["altele"]?.isChecked == true) View.VISIBLE else View.GONE
        }

        for ((k, lbl) in Motive.TOATE) {
            val cb = CheckBox(a).apply {
                text = lbl
                isChecked = init?.motive?.contains(k) == true
                setOnCheckedChangeListener { _, _ -> refreshSpin() }
            }
            checks[k] = cb
            col.addView(cb)
        }
        col.addView(nota)

        label("Motivul principal")
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos in curKeys.indices) princ = curKeys[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        col.addView(spin)
        refreshSpin()

        label("Perioada de blocare")
        val rgPer = RadioGroup(a)
        val per = listOf(30 to "30 de zile", 60 to "60 de zile", 0 to "Nelimitat")
        val perIds = HashMap<Int, Int>()
        for ((z, t) in per) {
            val rb = RadioButton(a).apply { text = t; id = View.generateViewId() }
            perIds[rb.id] = z
            rgPer.addView(rb)
            if ((init?.zile ?: 30) == z) rgPer.check(rb.id)
        }
        col.addView(rgPer)

        label("Ce face aplicația")
        val rgAct = RadioGroup(a)
        val rbRef = RadioButton(a).apply { text = "Refuză oferta"; id = View.generateViewId() }
        val rbAv = RadioButton(a).apply { text = "Doar avertizează"; id = View.generateViewId() }
        rgAct.addView(rbRef); rgAct.addView(rbAv)
        rgAct.check(if (init?.refuza == false) rbAv.id else rbRef.id)
        col.addView(rgAct)

        val b = AlertDialog.Builder(a)
            .setTitle(if (init == null) "Adresă evitată" else "Editează adresa")
            .setView(ScrollView(a).apply { addView(col) })
            .setPositiveButton("Salvează", null)
            .setNegativeButton("Anulează", null)
        if (onDelete != null) b.setNeutralButton("Șterge", null)
        val dlg = b.create()
        dlg.show()

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val t = Adresa.compune(Adresa.TIPURI[spTip.selectedItemPosition].first, nume.text.toString(), nr.text.toString())
            val motive = checks.filter { it.value.isChecked }.keys.toList()
            when {
                nume.text.toString().trim().length < 2 -> Toast.makeText(a, "Scrie numele străzii", Toast.LENGTH_SHORT).show()
                motive.isEmpty() -> Toast.makeText(a, "Bifează cel puțin un motiv", Toast.LENGTH_SHORT).show()
                else -> {
                    val zile = perIds[rgPer.checkedRadioButtonId] ?: 30
                    val now = System.currentTimeMillis()
                    val start = if (init == null || init.zile != zile) now else init.start
                    onSave(
                        Blocat(
                            init?.id ?: now, start, t, motive, if (princ in motive) princ else motive.first(),
                            nota.text.toString().trim(), zile, rgAct.checkedRadioButtonId != rbAv.id
                        )
                    )
                    dlg.dismiss()
                }
            }
        }
        if (onDelete != null) {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { onDelete(); dlg.dismiss() }
        }
    }
}
