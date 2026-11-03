package ro.marius.boltcalc

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class BoltReaderService : AccessibilityService() {

    private val boltPackage = "ee.mtakso.driver"
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private var lastRun = 0L
    private val hideRunnable = Runnable { hideOverlay() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != boltPackage) return
        val now = System.currentTimeMillis()
        if (now - lastRun < 150) return
        lastRun = now

        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>()
        collect(root, texts)

        // pentru calibrare: ultimul text citit apare în aplicație
        getSharedPreferences("bolt", MODE_PRIVATE).edit()
            .putString("dump", texts.joinToString("\n")).apply()

        val oferta = Analyzer.parseaza(texts) ?: return
        val r = Analyzer.analizeaza(oferta, Params.load(this))
        show(r, oferta)
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (n == null) return
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    private fun show(r: Rezultat, o: Oferta) {
        val color = when (r.verdict) {
            Verdict.EXCELENT -> Color.parseColor("#1B8F3A")
            Verdict.BUN -> Color.parseColor("#B8860B")
            Verdict.REFUZA -> Color.parseColor("#C62828")
        }
        val txt = "${r.text}\n" +
            "%.2f lei/km  •  profit %.1f lei\n".format(r.ef, r.profit) +
            "bonus %.1f/cursă  •  fără bonus %.2f lei/km".format(r.bonus, r.efFaraBonus)

        if (overlay == null) {
            val tv = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(32, 20, 32, 20)
                gravity = Gravity.CENTER
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP; y = 120 }
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(tv, lp)
            overlay = tv
        }
        overlay?.apply {
            background = GradientDrawable().apply {
                setColor(color); cornerRadius = 24f; alpha = 235
            }
            text = txt
        }
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 2500)
    }

    private fun hideOverlay() {
        overlay?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    override fun onInterrupt() { hideOverlay() }
    override fun onDestroy() { hideOverlay(); super.onDestroy() }
}
