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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var autoKey: String? = null
    private val hideRunnable = Runnable { hideOverlay() }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun keyOf(o: Oferta) = "${o.pret}|${o.preluareKm}|${o.cursaKm}|${o.preluareMin}|${o.cursaMin}"

    // Acceptare automată activă? Se deduce din ce vedem: eticheta "Acceptare automată" o pornește, un buton "Acceptă" manual o oprește.
    private var modAuto = false
    private val inchidere = Runnable { endOffer(); lockKey = null }

    /** Cardul dispare doar după o pauză de 2,5 s fără ofertă citită (o citire ratată nu mai închide oferta). */
    private fun programeazaInchidere() {
        handler.removeCallbacks(inchidere)
        if (curId != null) handler.postDelayed(inchidere, 2500) else lockKey = null
    }

    private fun endOffer() {
        val id = curId
        if (id != null) {
            // Oferta a dispărut fără click pe Refuză: dacă acceptarea automată e activă, cursa a fost acceptată.
            if (modAuto) { if (Istoric.marcheazaAuto(this, id)) Traseu.start(this, id) }
            else Istoric.inchide(this, id)
        }
        curId = null
        curKey = null
        autoKey = null
    }

    /** Clientul (sau Bolt) a anulat cursa: oprim GPS-ul și marcăm oferta ca anulată. */
    private fun anuleaza() {
        val id = Traseu.idActiv() ?: return
        Traseu.termina(this, false)
        Istoric.seteazaStatus(this, id, Istoric.ANULAT)
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
        if (status == Istoric.ACCEPTAT) Traseu.start(this, id)
        lockKey = curKey
        curId = null
        curKey = null
        hideOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != boltPackage) return
        if (!pornit()) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) { handleClick(event); return }
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val nt = event.text?.joinToString(" ") ?: ""
            if (nt.isNotBlank()) {
                val hmN = SimpleDateFormat("HH:mm:ss", Locale("ro")).format(Date())
                Params.prefs(this).edit().putString("notif", hmN + " " + nt).apply()
                if (Analyzer.norm(nt).let { it.contains("anulat") || it.contains("cancelled") || it.contains("canceled") }) anuleaza()
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastRun < 250) {
            // Nu pierdem evenimentul: recitim puțin mai târziu (cardul ofertei se poate completa după primul eveniment).
            if (!trailPending) { trailPending = true; handler.postDelayed(trail, 300) }
            return
        }
        scan(event)
    }

    private var trailPending = false
    private var rescanCount = 0
    private val trail = Runnable { trailPending = false; scan(null) }
    private val jurnal = ArrayDeque<String>()

    private fun rootOf(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var r = n
        var i = 0
        while (r?.parent != null && i < 40) { r = r.parent; i++ }
        return r
    }

    private var eraPornit = true
    private var ultimaCursa: Long? = null
    private var pretFinalVazut: Double? = null

    /** Comutatorul PORNIT/OPRIT din aplicație. Când e oprit: fără banner, fără istoric, fără GPS. */
    private fun pornit(): Boolean {
        val on = Params.prefs(this).getBoolean("pornit", true)
        if (!on && eraPornit) { handler.removeCallbacks(inchidere); hideOverlay(); Traseu.opreste(); curId = null; curKey = null; autoKey = null }
        eraPornit = on
        return on
    }

    private fun scan(event: AccessibilityEvent?) {
        if (!pornit()) return
        val now = System.currentTimeMillis()
        lastRun = now

        // Citim DOAR ferestre Bolt (niciodată aplicația noastră sau Waze): ferestrele din listă + rădăcina ferestrei care a generat evenimentul.
        val groups = ArrayList<List<String>>()
        fun adaug(r: AccessibilityNodeInfo?) {
            if (r == null || r.packageName?.toString() != boltPackage) return
            val g = ArrayList<String>()
            collect(r, g)
            if (g.isNotEmpty() && groups.none { it == g }) groups.add(g)
        }
        try { for (w in windows) adaug(w.root) } catch (e: Exception) { }
        try { adaug(rootOf(event?.source)) } catch (e: Exception) { }
        try { adaug(rootInActiveWindow) } catch (e: Exception) { }   // adăugat doar dacă e Bolt (verificat în adaug)
        val hm = SimpleDateFormat("HH:mm:ss", Locale("ro")).format(Date(now))
        val dump = groups.joinToString("\n---- fereastră ----\n") { it.joinToString("\n") }
        val ed = Params.prefs(this).edit().putString("dump", dump).putString("tEvent", hm)

        Traseu.verificaExpirat(this)
        if (Traseu.idActiv() != null && Analyzer.norm(dump).let { it.contains("anulat") || it.contains("cancelled") || it.contains("canceled") }) {
            ed.putString("notif", hm + " (ecran) " + dump.lines().firstOrNull { Analyzer.norm(it).let { x -> x.contains("anulat") || x.contains("cancel") } })
            anuleaza()
        }
        if (Traseu.poateTermina()) {
            val nf = Analyzer.norm(dump)
            if (nf.contains("confirma tariful") || nf.contains("evalueaza pasagerul") || nf.contains("evalueaza clientul")) {
                ed.putString("dumpFinal", hm + "\n" + dump)
                ultimaCursa = Traseu.termina(this); pretFinalVazut = null
            }
        }
        val idCursa = ultimaCursa
        if (idCursa != null && Analyzer.norm(dump).contains("confirma tariful")) {
            val pf = Analyzer.pretFinal(groups.flatten())
            if (pf != null && pf != pretFinalVazut) {
                pretFinalVazut = pf
                Istoric.seteazaPretFinal(this, idCursa, pf)
                ed.putString("dumpFinal", hm + "\n" + dump)
            }
        }
        val idCursa = ultimaCursa
        if (idCursa != null && Analyzer.norm(dump).contains("confirma tariful")) {
            val pf = Analyzer.pretFinal(groups.flatten())
            if (pf != null && pf != pretFinalVazut) {
                pretFinalVazut = pf
                Istoric.seteazaPretFinal(this, idCursa, pf)
                ed.putString("dumpFinal", hm + "\n" + dump)
            }
        }

        // Ecranul "Curse programate" (listă): un rând cu calcul pentru fiecare card vizibil, fără istoric.
        val carduri = Analyzer.parseazaLista(groups.flatten())
        if (carduri.isNotEmpty()) { ed.apply(); arataLista(carduri); return }

        var oferta: Oferta? = null
        for (g in groups) { oferta = Analyzer.parseaza(g); if (oferta != null) break }
        if (oferta == null && groups.size > 1) oferta = Analyzer.parseaza(groups.flatten())
        val partial = groups.any { Analyzer.pareOferta(it) }
        val ndj = Analyzer.norm(dump)
        val etAuto = ndj.contains("acceptare automata") || ndj.contains("auto accept")
        if (etAuto) modAuto = true else if (ndj.contains("accept") && oferta != null) modAuto = false
        if (oferta != null || partial || ndj.contains("accept")) {
            jurnal.addLast(hm + "  ferestre=" + groups.size + " texte=" + groups.sumOf { it.size } + "  ofertă=" + (if (oferta != null) "DA" else "NU") +
                "  buton=" + (if (etAuto) "auto" else if (ndj.contains("accept")) "manual" else "nu") + "  modAuto=" + modAuto)
            while (jurnal.size > 40) jurnal.removeFirst()
            ed.putString("jurnal", jurnal.joinToString("\n"))
        }
        if (oferta == null && partial && rescanCount < 8) {
            // Pare o ofertă incompletă (fără buton încă): mai citim de câteva ori, la 250 ms.
            rescanCount++
            if (!trailPending) { trailPending = true; handler.postDelayed(trail, 250) }
        } else if (oferta != null || !partial) rescanCount = 0
        if (oferta == null) {
            val n = Analyzer.norm(dump)
            val seamanaOferta = n.contains("lei") && (n.contains("accept") || n.contains("declin") || n.contains("refuz"))
            if (seamanaOferta) ed.putString("fail", hm + "\n" + dump)
            ed.apply()
            programeazaInchidere(); hideOverlay(); return
        }
        ed.putString("tOk", hm).apply()
        handler.removeCallbacks(inchidere)
        val key = keyOf(oferta)
        if (key == lockKey) { hideOverlay(); return }
        val r = Analyzer.analizeaza(oferta, Params.load(this))
        // Cursele programate: doar banner cu calcul, fără istoric și fără măsurare GPS (poți răsfoi zeci de carduri).
        if (oferta.programata) { show(oferta, r); return }
        // Cu acceptarea automată, în locul butonului "Acceptă" scrie "Acceptare automată" circa 2 secunde.
        val nd = Analyzer.norm(dump)
        val auto = nd.contains("acceptare automata") || nd.contains("auto accept")
        if (key == curKey && auto && autoKey != key) {
            curId?.let { Istoric.seteazaStatus(this, it, Istoric.AUTO); Traseu.start(this, it) }
            autoKey = key
        }
        if (key != curKey) {
            endOffer()
            curKey = key
            if (auto) autoKey = key
            val id = System.currentTimeMillis()
            curId = id
            Istoric.adauga(
                this,
                Intrare(
                    id, oferta.pret, oferta.preluareKm, oferta.cursaKm, r.minute, oferta.rating,
                    r.ef, r.profit, r.verdict.name, r.motive, oferta.adrPreluare, oferta.adrDestinatie,
                    if (auto) Istoric.AUTO else Istoric.VAZUT, oferta.nume
                )
            )
            if (auto) Traseu.start(this, id)
        }
        // Cursele acceptate automat se înregistrează, dar nu afișăm banner.
        if (auto || autoKey == key) hideOverlay() else show(oferta, r)
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (n == null) return
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        // Descrierea se adaugă doar dacă diferă de text, altfel aceeași valoare (ex: "3.8km") ar fi numărată de două ori.
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() && it != n.text?.toString() }?.let { out.add(it) }
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
            ).apply {
                // Banner mutat din partea de sus ca să nu acopere butonul "Refuză". Poziția se alege din aplicație.
                when (Params.prefs(this@BoltReaderService).getInt("poz", 1)) {
                    0 -> { gravity = Gravity.TOP; y = dp(36) }
                    2 -> { gravity = Gravity.CENTER; y = dp(110) }
                    else -> { gravity = Gravity.CENTER; y = 0 }
                }
                horizontalMargin = 0.03f
            }
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
        val pf = Favorite.potrivire(this, o.nume, o.text)
        val titluTxt = if (pf == Favorite.NIMIC) r.titlu else r.titlu + "  ♥"
        tvTitle?.text = android.text.SpannableString(titluTxt).apply {
            if (pf != Favorite.NIMIC) setSpan(
                android.text.style.ForegroundColorSpan(Color.parseColor(if (pf == Favorite.SIGUR) "#FF3B3B" else "#F1C40F")),
                r.titlu.length, titluTxt.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvTitle?.setTextColor(color)
        tvPrice?.text = "%.2f lei".format(o.pret)
        val minTxt = if (r.minute > 0) "  •  ${r.minute} min" else ""
        val cl = if (o.nume.isNotEmpty() || o.rating != null)
            "\n" + o.nume + (if (o.rating != null) (if (o.nume.isNotEmpty()) "  " else "") + "★ ${o.rating}" else "") else ""
        tvSub?.text = "%.1f km total$minTxt".format(r.dist) + (if (o.programata) "  •  fără preluare" else "") + cl
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

    private var boxLista: LinearLayout? = null

    private fun arataLista(carduri: List<Analyzer.CardProgramat>) {
        if (box != null) hideOverlay()
        val p = Params.load(this)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (boxLista == null) {
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply { setColor(Color.parseColor("#F20E1512")); setStroke(dp(2), Color.parseColor("#6CB4FF")); cornerRadius = dp(22).toFloat() }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                when (Params.prefs(this@BoltReaderService).getInt("poz", 1)) {
                    0 -> { gravity = Gravity.TOP; y = dp(36) }
                    2 -> { gravity = Gravity.CENTER; y = dp(110) }
                    else -> { gravity = Gravity.CENTER; y = 0 }
                }
                horizontalMargin = 0.03f
            }
            wm.addView(b, lp)
            boxLista = b
        }
        val b = boxLista ?: return
        b.removeAllViews()
        b.addView(txt(12f, true, Color.parseColor("#6CB4FF")).apply { text = "CURSE PROGRAMATE  •  lei/km, fără preluare" })
        for (c in carduri.take(6)) {
            val r = Analyzer.analizeaza(Oferta(c.pret, 0.0, c.km, null, null, null, "", "", ""), p)
            val col = when (r.verdict) {
                Verdict.EXCELENT -> Color.parseColor("#2ECC71")
                Verdict.BUN -> Color.parseColor("#F1C40F")
                Verdict.REFUZA -> Color.parseColor("#FF5252")
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
            row.addView(txt(12f, false, Color.parseColor("#B3C0D9")).apply { text = c.cand }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(txt(14f, true).apply { text = "%.2f lei • %.1f km".format(c.pret, c.km); setPadding(dp(8), 0, dp(10), 0) })
            row.addView(txt(18f, true, col).apply { text = "%.2f".format(r.ef) })
            b.addView(row)
        }
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 8000)
    }

    private fun hideOverlay() {
        boxLista?.let { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        boxLista = null
        box?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        box = null; gauge = null; tvTitle = null; tvPrice = null; tvSub = null
        tvWhy = null; tvWarn = null; tvOra = null; tvCost = null; tvProfit = null
    }

    override fun onInterrupt() { hideOverlay() }
    override fun onDestroy() { hideOverlay(); super.onDestroy() }
}
