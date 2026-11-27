package ro.marius.boltcalc

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val fields = LinkedHashMap<String, EditText>()
    private lateinit var net: CheckBox
    private lateinit var out: TextView
    private lateinit var locStatus: TextView
    private lateinit var accStatus: TextView
    private lateinit var locHint: TextView
    private lateinit var saveBtn: Button

    private val BG = Color.parseColor("#0B1020")
    private val CARD = Color.parseColor("#141C18")
    private val INK = Color.WHITE
    private val MUTED = Color.parseColor("#B3C0D9")
    private val ACCENT = Color.parseColor("#2ECC71")

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val p = Params.load(ctx)
        val page = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        var card = page

        page.addView(TextView(ctx).apply {
            val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
            val t = "Bolt Calc  v$ver"
            text = android.text.SpannableString(t).apply {
                val i = t.indexOf("  v")
                setSpan(android.text.style.RelativeSizeSpan(0.5f), i, t.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#8FA0C0")), i, t.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            textSize = 28f; typeface = Typeface.DEFAULT_BOLD; setTextColor(INK)
        })
        page.addView(TextView(ctx).apply {
            text = "Verdict pe ofertă, direct peste Bolt Driver"; textSize = 14f; setTextColor(MUTED); setPadding(0, 0, 0, dp(12))
        })

        val sw = Switch(ctx).apply {
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(14), dp(10), dp(14), dp(10))
            isChecked = Params.prefs(ctx).getBoolean("pornit", true)
            fun upd() { text = if (isChecked) "PORNIT" else "OPRIT"; setTextColor(if (isChecked) ACCENT else Color.parseColor("#FF5252")) }
            upd()
            setOnCheckedChangeListener { _, on ->
                Params.prefs(ctx).edit().putBoolean("pornit", on).apply(); upd()
            }
            background = GradientDrawable().apply { setColor(Color.parseColor("#0E3B34")); cornerRadius = dp(18).toFloat() }
        }
        page.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun paleta(t: String): Pair<Int, Int> = when {
            t.startsWith("Activări") -> "#0F3A4A" to "#5CD6F2"
            t.startsWith("Acces") -> "#0E3B34" to "#4BE3B5"
            t.startsWith("Costuri") -> "#14335F" to "#6CB4FF"
            t.startsWith("Praguri") -> "#33235C" to "#B79BFF"
            t.startsWith("Filtre") -> "#4D2C10" to "#FFB35C"
            t.startsWith("Resetare") -> "#4D1822" to "#FF7A8C"
            else -> "#1E2B38" to "#9FB6CC"
        }.let { Color.parseColor(it.first) to Color.parseColor(it.second) }

        fun section(t: String) {
            val (fundal, accent) = paleta(t)
            card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(16))
                background = GradientDrawable().apply { setColor(fundal); cornerRadius = dp(18).toFloat() }
            }
            page.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            card.addView(TextView(ctx).apply {
                text = t.uppercase(); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(accent); letterSpacing = 0.08f
                setPadding(0, 0, 0, dp(6))
            })
        }
        fun label(t: String) = card.addView(TextView(ctx).apply { text = t; textSize = 13f; setTextColor(MUTED); setPadding(0, dp(8), 0, 0) })
        fun styleEdit(e: EditText) {
            e.setTextColor(INK); e.setHintTextColor(Color.parseColor("#55635A")); e.textSize = 18f
            e.setPadding(dp(12), dp(10), dp(12), dp(10))
            e.background = GradientDrawable().apply { setColor(Color.parseColor("#47000000")); cornerRadius = dp(10).toFloat() }
        }
        fun field(key: String, lbl: String, hint: String = "") {
            label(lbl)
            val e = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(Params.raw(ctx, key))
                this.hint = hint
            }
            styleEdit(e)
            fields[key] = e
            card.addView(e)
        }
        fun button(t: String, primary: Boolean = false, f: () -> Unit): Button {
            val b = Button(ctx).apply {
                text = t; isAllCaps = false; setTextColor(if (primary) Color.BLACK else INK); textSize = 15f
                minHeight = dp(48); setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    setColor(if (primary) ACCENT else Color.parseColor("#24FFFFFF")); cornerRadius = dp(12).toFloat()
                }
                setOnClickListener { f() }
            }
            card.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            return b
        }

        section("Activări")
        fun rand(titlu: String, sus: Int): TextView {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(sus), 0, 0) }
            row.addView(TextView(ctx).apply { text = titlu; textSize = 15f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val pill = TextView(ctx).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(10), dp(5), dp(10), dp(5)) }
            row.addView(pill)
            card.addView(row)
            return pill
        }
        accStatus = rand("Serviciu de accesibilitate", 4)
        button("Activează în Accesibilitate", true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        locStatus = rand("Locație (km reali din GPS)", 16)
        locHint = TextView(ctx).apply { textSize = 12f; setTextColor(MUTED); setPadding(0, dp(6), 0, 0) }
        card.addView(locHint)
        button("Permite locația") { cereLocatia() }

        section("Acces rapid")
        button("Istoric oferte") { startActivity(Intent(ctx, HistoryActivity::class.java)) }
        button("Preferate ♥ (clienți și adrese)") { startActivity(Intent(ctx, FavoriteActivity::class.java)) }
        label("Poziția bannerului pe ecran")
        val spPoz = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, listOf("Sus", "Mijloc", "Puțin sub mijloc"))
            setSelection(Params.prefs(ctx).getInt("poz", 1))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    Params.prefs(ctx).edit().putInt("poz", pos).apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        card.addView(spPoz)

        section("Costuri")
        field("consum", "Consum (l/100 km)")
        field("motorina", "Preț motorină (lei/l)")
        field("comision", "Comision Bolt (0.25 = 25%)")
        net = CheckBox(ctx).apply { text = "Prețul de pe ecran e deja NET"; isChecked = p.pretEsteNet; setTextColor(INK) }
        card.addView(net)

        section("Praguri, profit lei/km")
        field("minEx", "Excelentă de la")
        field("minBun", "Bună de la")
        field("maxPre", "Preluare maximă (km)")

        section("Filtre de refuz, gol = oprit")
        field("minPret", "Preț minim (lei)", "oprit")
        field("maxPret", "Preț maxim (lei), refuză peste", "oprit")
        field("minRating", "Rating minim client", "oprit")
        button("Adrese evitate: motive și perioadă") { startActivity(Intent(ctx, BlocateActivity::class.java)) }

        section("Salvare și test")
        saveBtn = button("Salvează", true) { save(); setSalvat(true); Toast.makeText(ctx, "Salvat", Toast.LENGTH_SHORT).show() }
        // Butonul arată SALVAT până la următoarea modificare.
        setSalvat(true)
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { setSalvat(false) }
        }
        fields.values.forEach { it.addTextChangedListener(watcher) }
        net.setOnCheckedChangeListener { _, _ -> setSalvat(false) }
        button("Testează cu o ofertă de probă") { save(); setSalvat(true); test() }
        button("Diagnostic: ce a citit aplicația din Bolt") {
            val pr = Params.prefs(ctx)
            out.text = "Ultimul eveniment Bolt: " + pr.getString("tEvent", "(niciunul)") +
                "\nUltima ofertă citită: " + pr.getString("tOk", "(niciuna)") +
                "\nUltimul click: " + pr.getString("click", "(niciunul)") +
                "\nUltima notificare/anulare: " + pr.getString("notif", "(niciuna)") +
                "\nSfârșit cursă detectat: " + (if (pr.getString("dumpFinal", null) != null) "da" else "nu încă") +
                "\n\nJurnal scanări (ora, câte ferestre Bolt, ofertă citită?, buton):\n" + pr.getString("jurnal", "(gol)") +
                "\n\nUltima ofertă NECITITĂ:\n" + pr.getString("fail", "(niciuna)") +
                "\n\nUltimul ecran Bolt:\n" + pr.getString("dump", "(nimic încă)") +
                "\n\nEcranul de final de cursă:\n" + pr.getString("dumpFinal", "(nimic încă)")
        }
        button("Salvează diagnostic în fișier (îl trimiți apoi)") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "boltcalc-diagnostic-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".txt")
            }
            startActivityForResult(i, 1003)
        }
        out = TextView(ctx).apply { textSize = 13f; setTextColor(MUTED); setPadding(0, dp(10), 0, 0) }
        card.addView(out)

        section("Resetare")
        button("Resetează date (alegi ce)") { Resetare.dialog(ctx) { recreate() } }

        section("Copie de siguranță")
        button("Salvează copie (Drive sau alt loc)") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "boltcalc-copie-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json")
            }
            startActivityForResult(i, 1001)
        }
        button("Restaurează dintr-o copie") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(i, 1002)
        }

        setContentView(ScrollView(ctx).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#0B1020"), Color.parseColor("#141C34")))
            addView(page)
        })
    }

    override fun onResume() { super.onResume(); actualizeazaLoc() }

    private fun setSalvat(salvat: Boolean) {
        saveBtn.text = if (salvat) "SALVAT ✓" else "Salvează"
        saveBtn.setTextColor(if (salvat) Color.WHITE else Color.BLACK)
        saveBtn.background = GradientDrawable().apply {
            setColor(if (salvat) Color.parseColor("#1B7F4B") else ACCENT); cornerRadius = dp(12).toFloat()
        }
    }

    private fun pastila(tv: TextView, t: String, culoare: Int) {
        tv.text = t
        tv.setTextColor(Color.BLACK)
        tv.background = GradientDrawable().apply { setColor(culoare); cornerRadius = dp(10).toFloat() }
    }

    private fun accesibilitateActiva(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains("$packageName/") && s.contains("BoltReaderService")
    }

    private fun actualizeazaLoc() {
        val verde = Color.parseColor("#2ECC71"); val rosu = Color.parseColor("#FF5252"); val galben = Color.parseColor("#F1C40F")
        if (accesibilitateActiva()) pastila(accStatus, "ACTIV", verde) else pastila(accStatus, "INACTIV", rosu)
        when {
            !Traseu.areLocatie(this) -> {
                pastila(locStatus, "INACTIVĂ", rosu)
                locHint.text = "Apasă „Permite locația” și acordă permisiunea."; locHint.visibility = android.view.View.VISIBLE
            }
            !Traseu.areLocatieMereu(this) -> {
                pastila(locStatus, "PARȚIAL", galben)
                locHint.text = "Alege „Permite tot timpul”, altfel kilometrii nu se măsoară când ești în Waze."; locHint.visibility = android.view.View.VISIBLE
            }
            else -> { pastila(locStatus, "ACTIVĂ", verde); locHint.visibility = android.view.View.GONE }
        }
    }

    private fun cereLocatia() {
        if (!Traseu.areLocatie(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 2001)
        } else if (!Traseu.areLocatieMereu(this)) cereMereu()
        else Toast.makeText(this, "Locația e deja permisă", Toast.LENGTH_SHORT).show()
    }

    private fun cereMereu() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            Toast.makeText(this, "Alege Locație → „Permite tot timpul”", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
        } else if (android.os.Build.VERSION.SDK_INT >= 29) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2002)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) cereMereu()
        actualizeazaLoc()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 1001) {
                contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(this).toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Copie salvată", Toast.LENGTH_SHORT).show()
            } else if (requestCode == 1003) {
                contentResolver.openOutputStream(uri)?.use { it.write(Diagnostic.construieste(this).toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Diagnostic salvat", Toast.LENGTH_SHORT).show()
            } else if (requestCode == 1002) {
                val txt = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                if (Backup.import(this, txt)) {
                    Toast.makeText(this, "Restaurat", Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    Toast.makeText(this, "Fișier invalid", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Eroare: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun save() {
        val e = Params.prefs(this).edit()
        fields.forEach { (k, v) -> e.putString(k, v.text.toString().trim()) }
        e.putBoolean("net", net.isChecked)
        e.apply()
    }

    private fun test() {
        val sample = listOf(
            "Comfort", "+19,64 lei inclus", "40,84 lei (NET, taxe incluse)", "Ioana Lucia • 5.0 ★",
            "5 min • 1.6 km", "Strada Deleni 7, București", "14 min • 3.9 km", "Bulevardul Pache Protopopescu 62, București", "Acceptă"
        )
        val o = Analyzer.parseaza(sample)
        out.text = if (o == null) "Nu am putut citi oferta de probă." else {
            val r = Analyzer.analizeaza(o, Params.load(this))
            "Citit: ${o.pret} lei, preluare ${o.preluareKm} km, cursă ${o.cursaKm} km, rating ${o.rating}\n" +
                "${r.titlu}: %.2f lei/km, profit %.1f lei\n${r.motive}\nDe la: ${o.adrPreluare}\nLa: ${o.adrDestinatie}".format(r.ef, r.profit)
        }
    }
}
