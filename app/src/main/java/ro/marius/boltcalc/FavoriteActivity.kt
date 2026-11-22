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

/** Lista de clienți și adrese preferate (♥), cu adăugare manuală. */
class FavoriteActivity : Activity() {

    private val MUTED = Color.parseColor("#E0B3C8")
    private val PINK = Color.parseColor("#FF5C9A")
    private lateinit var list: LinearLayout

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(32)) }
        page.addView(TextView(this).apply { text = "♥ Preferate"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        page.addView(TextView(this).apply {
            text = "Roșu pe ofertă = nume și adresă se potrivesc. Galben = se potrivește doar adresa (sau ai lăsat numele gol). Poți adăuga și manual, din detaliile unei curse finalizate din Bolt."
            textSize = 12f; setTextColor(MUTED); setPadding(0, dp(4), 0, dp(10))
        })
        page.addView(Button(this).apply {
            text = "+ Adaugă manual"; isAllCaps = false; setTextColor(Color.WHITE); background = round(Color.parseColor("#C2185B"), 12)
            setOnClickListener { Favorite.manual(this@FavoriteActivity, null) { render() } }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        setContentView(ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#1A0B18"), Color.parseColor("#2B1428")))
            addView(page)
        })
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        list.removeAllViews()
        val all = Favorite.load(this).sortedByDescending { it.id }
        if (all.isEmpty()) {
            list.addView(TextView(this).apply { text = "Niciun preferat încă."; setTextColor(MUTED); setPadding(0, dp(20), 0, 0) })
            return
        }
        for (f in all) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = round(Color.parseColor("#4A1A33"), 16)
                setOnClickListener { Favorite.manual(this@FavoriteActivity, f) { render() } }
            }
            row.addView(TextView(this).apply {
                text = "♥  " + (if (f.nume.isNotEmpty()) f.nume else "(fără nume)")
                setTextColor(PINK); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            })
            row.addView(TextView(this).apply {
                text = f.adresa + (if (f.tip.isNotEmpty()) "  •  " + f.tip else ""); setTextColor(Color.WHITE); textSize = 14f; setPadding(0, dp(4), 0, 0)
            })
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
    }
}
