package ro.marius.boltcalc

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView

class BoltReaderService : AccessibilityService() {

    private val boltPackage = "ee.mtakso.driver"
    private val handler = Handler(Looper.getMainLooper())
    private var box: LinearLayout? = null
    private var gauge: GaugeView? = null
    private var tvTitle: TextView? = null
    private var tvPrice: TextView? = null
    private var tvSub: TextView? = null
    private var tvWhy: TextView? = null
    private var tvWarn: TextView? = null
    private var tvOra: TextView? = null
    private var tvCost: TextView? = null
    private var tvProfit: TextView? = null
    private var lastRun = 0L
    private var curId: Long? = null
    private var curKey: String? = null
    private var lockKey: String? = null
    private val hideRunnable = Runnable { hideOverlay() }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun keyOf(o: Oferta) = "${o.pret}|${o.preluareKm}|${o.cursaKm}|${o.preluareMin}|${o.cursaMin}"

    private fun endOffer() {
        curId?.let { Istoric.inchide(this, it) }
        curId = null
        curKey = null
    }

    private fun handleClick(event: AccessibilityEvent) {
        val parts = ArrayList<String>()
        event.text?.forEach { parts.add(it.toString()) }
        event.contentDescription?.let { parts.add(it.toString()) }
        event.source?.let { collect(it, parts) }
        val t = Analyzer.norm(parts.joinToString(" "))
        Params.prefs(this).edit().putString("click", t).apply()
        val id = curId ?: return
        val status = when {
            t.contains("accept") -> Istoric.ACCEPTAT
            t.contains("declin") || t.contains("refuz") || t.contains("respin") -> Istoric.REFUZAT
            else -> return
        }
        Istoric.seteazaStatus(this, id, status)
        lockKey = curKey
        curId = null
        curKey = null
        hideOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != boltPackage) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) { handleClick(event); return }
        val now = System.currentTimeMillis()
        if (now - lastRun < 150) return
        lastRun = now

        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>()
        collect(root, texts)
        Params.prefs(this).edit().putString("dump", texts.joinToString("\n")).apply()

        val oferta = Analyzer.parseaza(texts)
        if (oferta == null) { endOffer(); lockKey = null; hideOverlay(); return }
        val key = keyOf(oferta)
        if (key == lockKey) { hideOverlay(); return }
        val r = Analyzer.analizeaza(oferta, Params.load(this))
        if (key != curKey) {
            endOffer()
            curKey = key
            val id = System.currentTimeMillis()
            curId = id
            Istoric.adauga(
                this,
                Intrare(
                    id, oferta.pret, oferta.preluareKm, oferta.cursaKm, r.minute, oferta.rating,
                    r.ef, r.profit, r.verdict.name, r.motive, oferta.adrPreluare, oferta.adrDestinatie, Istoric.VAZUT
                )
            )
        }
        show(oferta, r)
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (n == null) return
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    private fun txt(size: Float, bold: Boolean, color: Int = Color.WHITE): TextView = TextView(this).apply {
        setTextColor(color)
        textSize = size
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun tile(label: String, valueView: TextView): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(8), dp(4), dp(8))
        background = GradientDrawable().apply { setColor(Color.parseColor("#1A2620")); cornerRadius = dp(12).toFloat() }
        addView(valueView)
        addView(txt(10f, false, Color.parseColor("#8FA396")).apply { text = label; gravity = Gravity.CENTER })
    }

    private fun build(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val g = GaugeView(this)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(g, LinearLayout.LayoutParams(dp(104), dp(104)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        val title = txt(14f, true)
        val price = txt(32f, true)
        val sub = txt(13f, false, Color.parseColor("#8FA396"))
        col.addView(title); col.addView(price); col.addView(sub)
        top.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        val why = txt(13f, true).apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply { setColor(Color.parseColor("#C62828")); cornerRadius = dp(10).toFloat() }
        }
        root.addView(why, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        val warn = txt(13f, true, Color.BLACK).apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply { setColor(Color.parseColor("#F1C40F")); cornerRadius = dp(10).toFloat() }
        }
        root.addView(warn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        val ora = txt(17f, true); val cost = txt(17f, true); val profit = txt(17f, true)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val lp = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) } }
        row.addView(tile("LEI/ORĂ", ora), lp())
        row.addView(tile("COST", cost), lp())
        row.addView(tile("PROFIT", profit), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        gauge = g; tvTitle = title; tvPrice = price; tvSub = sub; tvWhy = why; tvWarn = warn
        tvOra = ora; tvCost = cost; tvProfit = profit
        return root
    }

    private fun show(o: Oferta, r: Rezultat) {
        if (box == null) {
            val b = build()
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP; y = dp(36); horizontalMargin = 0.03f }
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(b, lp)
            box = b
        }
        val color = when (r.verdict) {
            Verdict.EXCELENT -> Color.parseColor("#2ECC71")
            Verdict.BUN -> Color.parseColor("#F1C40F")
            Verdict.REFUZA -> Color.parseColor("#FF5252")
        }
        box?.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F20E1512")); setStroke(dp(2), color); cornerRadius = dp(22).toFloat()
        }
        gauge?.apply {
            value = r.ef.toFloat(); maxValue = r.gaugeMax.toFloat(); gaugeColor = color
            centerText = "%.2f".format(r.ef); subText = "lei/km"; invalidate()
        }
        tvTitle?.text = r.titlu
        tvTitle?.setTextColor(color)
        tvPrice?.text = "%.2f lei".format(o.pret)
        val minTxt = if (r.minute > 0) "  •  ${r.minute} min" else ""
        tvSub?.text = "%.1f km total$minTxt".format(r.dist)
        tvWhy?.text = r.motive
        tvWhy?.visibility = if (r.motive.isEmpty()) View.GONE else View.VISIBLE
        tvWarn?.text = r.avert
        tvWarn?.visibility = if (r.avert.isEmpty()) View.GONE else View.VISIBLE
        tvOra?.text = r.ora?.let { "%.0f".format(it) } ?: "–"
        tvCost?.text = "%.1f".format(r.cost)
        tvProfit?.text = "%.1f".format(r.profit)
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 4000)
    }

    private fun hideOverlay() {
        box?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        box = null; gauge = null; tvTitle = null; tvPrice = null; tvSub = null
        tvWhy = null; tvWarn = null; tvOra = null; tvCost = null; tvProfit = null
    }

    override fun onInterrupt() { hideOverlay() }
    override fun onDestroy() { hideOverlay(); super.onDestroy() }
}
