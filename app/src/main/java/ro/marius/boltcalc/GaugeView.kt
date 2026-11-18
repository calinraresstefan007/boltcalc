package ro.marius.boltcalc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

class GaugeView(ctx: Context) : View(ctx) {
    var value = 0f
    var maxValue = 1f
    var gaugeColor = Color.GREEN
    var centerText = ""
    var subText = ""

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#26332D")
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }
    private val tSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#8FA396")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val sw = 10f * d
        track.strokeWidth = sw
        arc.strokeWidth = sw
        arc.color = gaugeColor
        val pad = sw / 2f + 2f * d
        val rect = RectF(pad, pad, width - pad, height - pad)
        canvas.drawArc(rect, 135f, 270f, false, track)
        val frac = (value / maxValue).coerceIn(0f, 1f)
        if (frac > 0f) canvas.drawArc(rect, 135f, 270f * frac, false, arc)
        tCenter.textSize = 26f * d
        tSub.textSize = 12f * d
        canvas.drawText(centerText, width / 2f, height / 2f + 6f * d, tCenter)
        canvas.drawText(subText, width / 2f, height / 2f + 26f * d, tSub)
    }
}
