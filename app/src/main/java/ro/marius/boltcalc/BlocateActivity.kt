package ro.marius.boltcalc

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class BlocateActivity : Activity() {

    private val BG = Color.parseColor("#0B100E")
    private val CARD = Color.parseColor("#141C18")
    private val MUTED = Color.parseColor("#E8C3C9")
    private val GREEN = Color.parseColor("#2ECC71")
    private val AMBER = Color.parseColor("#F1C40F")
    private val RED = Color.parseColor("#FF5252")
    private lateinit var list: LinearLayout

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        page.addView(TextView(this).apply {
            text = "Adrese evitate"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        page.addView(TextView(this).apply {
            text = "Fără număr blochezi toată strada, cu număr doar acel număr. Tipul contează: Strada Deleni și Aleea Deleni sunt străzi diferite. Alege „Oricare tip” ca să le blochezi pe amândouă. Poți evita și un client: scrie numele lui în câmpul „Nume” (tip „Oricare”)."
            textSize = 12f; setTextColor(MUTED); setPadding(0, dp(4), 0, dp(10))
        })
        page.addView(Button(this).apply {
            text = "+ Adaugă adresă sau nume"; isAllCaps = false; setTextColor(Color.WHITE); background = round(Color.parseColor("#C62828"), 12)
            setOnClickListener { BlocDialog.arata(this@BlocateActivity, null, "", { Blocate.salveaza(this@BlocateActivity, it); render() }, null) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        setContentView(ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#1F0D12"), Color.parseColor("#2B1219")))
            addView(page)
        })
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        list.removeAllViews()
        val all = Blocate.load(this).sortedWith(compareByDescending<Blocat> { it.activ() }.thenByDescending { it.start })
        if (all.isEmpty()) {
            list.addView(TextView(this).apply { text = "Nicio adresă evitată încă."; setTextColor(MUTED); setPadding(0, dp(20), 0, 0) })
            return
        }
        for (b in all) {
            val activ = b.activ()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener {
                    BlocDialog.arata(this@BlocateActivity, b, "",
                        { Blocate.salveaza(this@BlocateActivity, it); render() },
                        { Blocate.sterge(this@BlocateActivity, b.id); render() })
                }
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(TextView(this).apply {
                text = b.text; setTextColor(Color.WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val (pt, pc) = when {
                !activ -> "EXPIRATĂ" to MUTED
                b.refuza -> "REFUZĂ" to RED
                else -> "AVERTIZEAZĂ" to AMBER
            }
            top.addView(TextView(this).apply {
                text = pt; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.BLACK)
                setPadding(dp(8), dp(4), dp(8), dp(4)); background = round(pc, 8)
            })
            row.addView(top)
            val alte = b.motive.filter { it != b.principal }.joinToString(", ") { Motive.eticheta(it) }
            row.addView(TextView(this).apply {
                text = b.etichetaPrincipala() + if (alte.isNotEmpty()) "  •  $alte" else ""
                setTextColor(Color.WHITE); textSize = 13f; setPadding(0, dp(6), 0, 0)
            })
            val exp = b.expiraLa()
            row.addView(TextView(this).apply {
                text = when {
                    exp == null -> "Blocată nelimitat"
                    activ -> "Blocată până pe ${Blocat.dataScurta(exp)}"
                    else -> "Expirată pe ${Blocat.dataScurta(exp)}"
                }
                setTextColor(MUTED); textSize = 12f; setPadding(0, dp(2), 0, 0)
            })
            if (!activ) {
                row.addView(TextView(this).apply {
                    text = "Reactivează"; setTextColor(GREEN); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(8), 0, 0)
                    setOnClickListener { b.start = System.currentTimeMillis(); Blocate.salveaza(this@BlocateActivity, b); render() }
                })
            }
            // Culoare după stare: roșu = refuză, galben = avertizează, gri = expirată.
            val (tint, band) = when {
                !activ -> "#1E2B38" to MUTED
                b.refuza -> "#431B22" to RED
                else -> "#40350F" to AMBER
            }
            val r16 = dp(16).toFloat()
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = round(Color.parseColor(tint), 16)
                alpha = if (activ) 1f else 0.7f
            }
            wrap.addView(android.view.View(this).apply {
                background = GradientDrawable().apply { setColor(band); cornerRadii = floatArrayOf(r16, r16, 0f, 0f, 0f, 0f, r16, r16) }
            }, LinearLayout.LayoutParams(dp(8), ViewGroup.LayoutParams.MATCH_PARENT))
            wrap.addView(row, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            list.addView(wrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
    }
}
