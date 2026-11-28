package ro.marius.boltcalc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/**
 * GPS în două moduri:
 *  - CURSĂ: de la acceptarea ofertei până la ecranul de final (km reali);
 *  - GOL: de la încheierea unei curse până la acceptarea următoarei (km în gol, contorizați separat de km curse).
 * La cursă-în-cursă, următoarea ofertă acceptată intră la coadă și pornește când se termină cea curentă.
 */
object Traseu {
    private var activ: Long? = null
    private val coada = ArrayList<Long>()
    private var golActiv = false
    private var metri = 0.0
    private var ultima: Location? = null
    private var startMs = 0L
    private var lm: LocationManager? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            if (l.hasAccuracy() && l.accuracy > 40f) return
            val u = ultima
            if (u == null) { ultima = l; return }
            val d = u.distanceTo(l).toDouble()
            val dt = ((l.time - u.time) / 1000.0).coerceAtLeast(1.0)
            if (d < 8.0) return              // zgomot GPS când stai pe loc
            if (d / dt > 60.0) { ultima = l; return }   // salt imposibil (>216 km/h)
            metri += d
            ultima = l
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun areLocatie(c: Context) =
        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun areLocatieMereu(c: Context) =
        android.os.Build.VERSION.SDK_INT < 29 ||
            c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun opresteGps() {
        try { lm?.removeUpdates(listener) } catch (e: Exception) { }
        lm = null
    }

    private fun pornesteGps(c: Context) {
        metri = 0.0; ultima = null; startMs = System.currentTimeMillis()
        if (!areLocatie(c)) return
        try {
            val m = c.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm = m
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 0f, listener, Looper.getMainLooper())
        } catch (e: Exception) { }
    }

    fun start(c: Context, id: Long) {
        if (id == activ || coada.contains(id)) return
        if (activ != null) { coada.add(id); return }
        porneste(c, id)
    }

    private fun porneste(c: Context, id: Long) {
        // Dacă măsuram kilometri în gol, îi salvăm separat (nu intră în calculul cursei).
        if (golActiv) {
            val km = metri / 1000.0
            val avut = ultima != null
            opresteGps(); golActiv = false
            if (avut && km > 0.05) Gol.adauga(c, km)
        }
        activ = id
        pornesteGps(c)
    }

    fun idActiv() = activ

    /** Aplicația a fost oprită din comutator: oprim GPS-ul și uităm măsurătorile în curs. */
    fun opreste() {
        opresteGps(); activ = null; coada.clear(); golActiv = false; ultima = null
    }

    /** Ignorăm ecranul de final al cursei precedente cât timp tocmai a pornit următoarea (primele 20 s). */
    fun poateTermina() = activ != null && System.currentTimeMillis() - startMs > 20_000L

    /** Oprește măsurătoarea curentă. Rezultatul se scrie în istoric. */
    fun termina(c: Context, platita: Boolean = true): Long? {
        val id = activ ?: return null
        val km = metri / 1000.0
        val avut = areLocatie(c) && ultima != null
        opresteGps()
        activ = null; ultima = null
        if (avut && km > 0.05) {
            val p = Params.load(c)
            val e = Istoric.load(c).firstOrNull { it.id == id }
            if (e != null) {
                val pretEf = e.pretFinal ?: e.pret   // prețul final de pe "Confirmă tariful", dacă a apărut deja
                val net = if (p.pretEsteNet) pretEf else pretEf * (1.0 - p.comisionBolt)
                val cost = km * (p.consumLa100 / 100.0) * p.pretMotorina
                val profit = if (platita) net - cost else -cost   // cursă anulată: nu încasezi nimic
                Istoric.seteazaKmReal(c, id, km, profit / km, profit)
            }
        }
        if (coada.isNotEmpty()) porneste(c, coada.removeAt(0))
        else { golActiv = true; pornesteGps(c) }   // de aici până la următoarea acceptare = km în gol
        return id
    }

    /** Siguranță: o cursă nu durează peste 3 ore, iar kilometrii în gol se opresc după 2 ore fără ofertă acceptată. */
    fun verificaExpirat(c: Context) {
        val acum = System.currentTimeMillis()
        if (activ != null && acum - startMs > 3 * 3600_000L) {
            opresteGps(); activ = null; ultima = null
            if (coada.isNotEmpty()) porneste(c, coada.removeAt(0))
        } else if (golActiv && acum - startMs > 2 * 3600_000L) {
            opresteGps(); golActiv = false; ultima = null
        }
    }
}
