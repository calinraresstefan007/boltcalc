package ro.marius.boltcalc

import android.app.Activity
import android.content.Intent
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

    private val BG = Color.parseColor("#0B100E")
    private val CARD = Color.parseColor("#141C18")
    private val INK = Color.WHITE
    private val MUTED = Color.parseColor("#8FA396")
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
            text = "Bolt Calc"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; setTextColor(INK)
        })
        page.addView(TextView(ctx).apply {
            text = "Verdict pe ofertă, direct peste Bolt Driver"; textSize = 14f; setTextColor(MUTED); setPadding(0, 0, 0, dp(12))
        })

        fun section(t: String) {
            card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(16))
                background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(16).toFloat() }
            }
            page.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            card.addView(TextView(ctx).apply {
                text = t.uppercase(); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ACCENT); letterSpacing = 0.08f
                setPadding(0, 0, 0, dp(6))
            })
        }
        fun label(t: String) = card.addView(TextView(ctx).apply { text = t; textSize = 13f; setTextColor(MUTED); setPadding(0, dp(8), 0, 0) })
        fun styleEdit(e: EditText) {
            e.setTextColor(INK); e.setHintTextColor(Color.parseColor("#55635A")); e.textSize = 18f
            e.setPadding(dp(12), dp(10), dp(12), dp(10))
            e.background = GradientDrawable().apply { setColor(Color.parseColor("#0B100E")); cornerRadius = dp(10).toFloat() }
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
        fun button(t: String, primary: Boolean = false, f: () -> Unit) {
            card.addView(Button(ctx).apply {
                text = t; isAllCaps = false; setTextColor(if (primary) Color.BLACK else INK); textSize = 15f
                background = GradientDrawable().apply {
                    setColor(if (primary) ACCENT else Color.parseColor("#22302A")); cornerRadius = dp(12).toFloat()
                }
                setOnClickListener { f() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(10) })
        }

        section("Pornire")
        button("Activează serviciul în Accesibilitate", true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Istoric oferte") { startActivity(Intent(ctx, HistoryActivity::class.java)) }

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
        button("Salvează", true) { save(); Toast.makeText(ctx, "Salvat", Toast.LENGTH_SHORT).show() }
        button("Testează cu o ofertă de probă") { save(); test() }
        button("Calibrare: ultimul text și click din Bolt") { out.text = "Ultimul click: " + Params.prefs(ctx).getString("click", "(niciunul)") + "\n\n" + Params.prefs(ctx).getString("dump", "(nimic încă)") }
        out = TextView(ctx).apply { textSize = 13f; setTextColor(MUTED); setPadding(0, dp(10), 0, 0) }
        card.addView(out)

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

        setContentView(ScrollView(ctx).apply { setBackgroundColor(BG); addView(page) })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 1001) {
                contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(this).toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Copie salvată", Toast.LENGTH_SHORT).show()
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
