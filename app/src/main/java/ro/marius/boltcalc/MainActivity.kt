package ro.marius.boltcalc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val fields = LinkedHashMap<String, EditText>()
    private lateinit var col: LinearLayout
    private lateinit var bonusBox: LinearLayout
    private lateinit var net: CheckBox
    private lateinit var dumpView: TextView
    private lateinit var bonuses: MutableList<Bonus>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Params.load(this)
        bonuses = p.bonuses.toMutableList()
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }

        col.addView(Button(this).apply {
            text = "1. Activează serviciul (Accesibilitate → Bolt Calc)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        col.addView(Button(this).apply {
            text = "2. Permite afișarea peste alte aplicații (dacă e cerut)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
        })

        col.addView(TextView(this).apply { text = "\nBonusuri Bolt"; textSize = 18f })
        bonusBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(bonusBox)
        col.addView(Button(this).apply {
            text = "✅ Cursă făcută +1 (toate campaniile)"
            setOnClickListener { bonuses.forEach { it.done++ }; Params.saveBonuses(this@MainActivity, bonuses); renderBonuses() }
        })

        col.addView(TextView(this).apply { text = "\nSetări"; textSize = 18f })
        fun field(key: String, label: String, v: Double) {
            col.addView(TextView(this).apply { text = label })
            val e = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(v.toString())
            }
            fields[key] = e; col.addView(e)
        }
        field("consum", "Consum (l/100 km)", p.consumLa100)
        field("motorina", "Preț motorină (lei/l)", p.pretMotorina)
        field("comision", "Comision Bolt (0.25 = 25%)", p.comisionBolt)
        field("bonusExtra", "Bonus extra per cursă (lei)", p.bonusExtra)
        field("maxPreluare", "Preluare maximă (km)", p.maxPreluareKm)
        field("minExcelent", "Prag excelent (lei/km)", p.minExcelent)
        field("minBun", "Prag acceptabil (lei/km)", p.minBun)
        net = CheckBox(this).apply { text = "Prețul de pe ecran e deja NET"; isChecked = p.pretEsteNet }
        col.addView(net)
        col.addView(Button(this).apply {
            text = "Salvează setările"
            setOnClickListener {
                val e = Params.prefs(this@MainActivity).edit()
                fields.forEach { (k, v) -> v.text.toString().replace(',', '.').toFloatOrNull()?.let { f -> e.putFloat(k, f) } }
                e.putBoolean("net", net.isChecked).apply()
                Toast.makeText(this@MainActivity, "Salvat", Toast.LENGTH_SHORT).show()
            }
        })
        col.addView(Button(this).apply {
            text = "Calibrare: ultimul text citit din Bolt"
            setOnClickListener { dumpView.text = Params.prefs(this@MainActivity).getString("dump", "(nimic încă)") }
        })
        dumpView = TextView(this).apply { textSize = 12f }
        col.addView(dumpView)

        renderBonuses()
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun renderBonuses() {
        bonusBox.removeAllViews()
        val fmt = SimpleDateFormat("d MMM HH:mm", Locale("ro"))
        bonuses.forEach { b ->
            val cb = CheckBox(this).apply {
                text = "${b.amount.toInt()} lei la ${b.target} curse, până ${fmt.format(Date(b.endMillis))}"
                isChecked = b.on
                setOnCheckedChangeListener { _, v -> b.on = v; Params.saveBonuses(this@MainActivity, bonuses); renderBonuses() }
            }
            val left = (b.target - b.done).coerceAtLeast(0)
            val info = TextView(this).apply {
                text = if (left == 0) "atins ✔" else "rămase $left → %.2f lei/cursă".format(b.amount / left)
            }
            val done = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER; setText(b.done.toString())
                setOnFocusChangeListener { _, f -> if (!f) text.toString().toIntOrNull()?.let { b.done = it; Params.saveBonuses(this@MainActivity, bonuses) } }
            }
            bonusBox.addView(cb); bonusBox.addView(TextView(this).apply { text = "Făcute:" })
            bonusBox.addView(done); bonusBox.addView(info)
        }
    }
}
