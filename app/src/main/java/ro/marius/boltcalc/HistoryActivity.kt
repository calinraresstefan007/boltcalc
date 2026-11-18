package ro.marius.boltcalc

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : Activity() {

    private val BG = Color.parseColor("#0B100E")
    private val CARD = Color.parseColor("#141C18")
    private val MUTED = Color.parseColor("#8FA396")
    private val GREEN = Color.parseColor("#2ECC71")
    private val AMBER = Color.parseColor("#F1C40F")
    private val RED = Color.parseColor("#FF5252")

    private lateinit var summary: TextView
    private lateinit var list: LinearLayout
    private lateinit var chips: LinearLayout
    private var filtru: String? = null

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        page.addView(TextView(this).apply {
            text = "Istoric oferte"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        summary = TextView(this).apply {
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(16), dp(14), dp(16), dp(14)); background = round(CARD, 16)
        }
        page.addView(summary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        page.addView(chips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        page.addView(TextView(this).apply {
            text = "Atinge o adresă din listă ca s-o adaugi la adresele evitate."
            textSize = 12f; setTextColor(MUTED); setPadding(0, dp(10), 0, 0)
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        page.addView(Button(this).apply {
            text = "Șterge istoricul"; isAllCaps = false; setTextColor(Color.WHITE); background = round(Color.parseColor("#22302A"), 12)
            setOnClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setMessage("Ștergi tot istoricul de oferte?")
                    .setPositiveButton("Șterge") { _, _ -> Istoric.sterge(this@HistoryActivity); render() }
                    .setNegativeButton("Anulează", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) })
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(page) })
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun blocheaza(adr: String) {
        val propus = adr.substringBefore(',').trim()
        BlocDialog.arata(this, null, propus, {
            Blocate.salveaza(this, it)
            Toast.makeText(this, "Adăugat la adrese evitate", Toast.LENGTH_SHORT).show()
        }, null)
    }

    private fun verdictColor(v: String) = when (v) {
        "EXCELENT" -> GREEN
        "BUN" -> AMBER
        else -> RED
    }

    private fun render() {
        val all = Istoric.load(this)
        val acc = all.filter { it.status == Istoric.ACCEPTAT }
        val ref = all.count { it.status == Istoric.REFUZAT }
        val exp = all.count { it.status == Istoric.EXPIRAT }
        val km = acc.sumOf { it.preluareKm + it.cursaKm }
        val profit = acc.sumOf { it.profit }
        val ratate = all.count { (it.verdict == "BUN" || it.verdict == "EXCELENT") && (it.status == Istoric.REFUZAT || it.status == Istoric.EXPIRAT) }
        summary.text = "${all.size} oferte văzute  •  ${acc.size} acceptate  •  $ref refuzate  •  $exp expirate\n" +
            "Profit din acceptate: %.0f lei".format(profit) +
            (if (km > 0) "  •  medie %.2f lei/km".format(profit / km) else "") + "\n" +
            "Oferte bune pe care nu le-ai luat: $ratate"

        chips.removeAllViews()
        listOf("Toate" to null, "Acceptate" to Istoric.ACCEPTAT, "Refuzate" to Istoric.REFUZAT, "Expirate" to Istoric.EXPIRAT).forEach { (lbl, st) ->
            val on = filtru == st
            chips.addView(TextView(this).apply {
                text = lbl; textSize = 13f; gravity = android.view.Gravity.CENTER
                setTextColor(if (on) Color.BLACK else Color.WHITE)
                background = round(if (on) GREEN else Color.parseColor("#22302A"), 20)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setOnClickListener { filtru = st; render() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        }

        list.removeAllViews()
        val rows = all.filter { filtru == null || it.status == filtru }
        if (rows.isEmpty()) {
            list.addView(TextView(this).apply { text = "Nicio ofertă aici încă."; setTextColor(MUTED); setPadding(0, dp(20), 0, 0) })
            return
        }
        val hm = SimpleDateFormat("HH:mm", Locale("ro"))
        val dm = SimpleDateFormat("dd.MM HH:mm", Locale("ro"))
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        rows.forEach { e ->
            val d = Date(e.id)
            val whenTxt = if (dayFmt.format(d) == today) hm.format(d) else dm.format(d)
            val c = verdictColor(e.verdict)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = round(CARD, 14)
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            top.addView(TextView(this).apply { text = whenTxt; setTextColor(MUTED); textSize = 13f }, LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT))
            top.addView(TextView(this).apply {
                text = "%.2f lei".format(e.pret); setTextColor(Color.WHITE); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(TextView(this).apply {
                text = "%.2f lei/km".format(e.ef); setTextColor(c); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, dp(10), 0)
            })
            val (pillTxt, pillCol) = when (e.status) {
                Istoric.ACCEPTAT -> "ACCEPTATĂ" to GREEN
                Istoric.REFUZAT -> "REFUZATĂ" to RED
                Istoric.EXPIRAT -> "EXPIRATĂ" to MUTED
                else -> "ÎN CURS" to AMBER
            }
            top.addView(TextView(this).apply {
                text = pillTxt; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.BLACK)
                setPadding(dp(8), dp(4), dp(8), dp(4)); background = round(pillCol, 8)
            })
            row.addView(top)
            val min = if (e.minute > 0) "  •  ${e.minute} min" else ""
            val rat = if (e.rating != null) "  •  ★ ${e.rating}" else ""
            row.addView(TextView(this).apply {
                text = "preluare %.1f km + cursă %.1f km$min  •  profit %.1f lei$rat".format(e.preluareKm, e.cursaKm, e.profit)
                setTextColor(MUTED); textSize = 12f; setPadding(0, dp(6), 0, 0)
            })
            fun adr(label: String, a: String) {
                if (a.isEmpty()) return
                row.addView(TextView(this).apply {
                    text = "$label  $a"; setTextColor(Color.WHITE); textSize = 13f
                    setPadding(dp(10), dp(8), dp(10), dp(8)); background = round(Color.parseColor("#1B2822"), 8)
                    setOnClickListener { blocheaza(a) }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            }
            adr("DE LA", e.adrP)
            adr("LA", e.adrD)
            if (e.motive.isNotEmpty()) row.addView(TextView(this).apply {
                text = e.motive; setTextColor(RED); textSize = 12f; setPadding(0, dp(2), 0, 0)
            })
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
    }
}
