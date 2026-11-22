package ro.marius.boltcalc

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Text complet de diagnostic, salvat ca fișier și trimis de utilizator. */
object Diagnostic {
    fun construieste(c: Context): String {
        val p = Params.prefs(c)
        val sb = StringBuilder()
        val f = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ro"))
        sb.append("=== BOLT CALC DIAGNOSTIC ===\n")
        sb.append("Generat: ").append(f.format(Date())).append("\n")
        sb.append("Telefon: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Comutator PORNIT: ").append(p.getBoolean("pornit", true)).append("\n")
        sb.append("Locație: ").append(if (Traseu.areLocatie(c)) "da" else "NU").append(", tot timpul: ").append(if (Traseu.areLocatieMereu(c)) "da" else "NU").append("\n")
        sb.append("Cursă măsurată acum: ").append(Traseu.idActiv() != null).append("\n\n")
        sb.append("Ultimul eveniment Bolt: ").append(p.getString("tEvent", "(niciunul)")).append("\n")
        sb.append("Ultima ofertă citită: ").append(p.getString("tOk", "(niciuna)")).append("\n")
        sb.append("Ultimul click: ").append(p.getString("click", "(niciunul)")).append("\n")
        sb.append("Ultima notificare/anulare: ").append(p.getString("notif", "(niciuna)")).append("\n\n")
        sb.append("--- JURNAL SCANĂRI (ora, ferestre Bolt, texte, ofertă citită?, buton?) ---\n")
        sb.append(p.getString("jurnal", "(gol)")).append("\n\n")
        sb.append("--- ULTIMA OFERTĂ NECITITĂ ---\n").append(p.getString("fail", "(niciuna)")).append("\n\n")
        sb.append("--- ULTIMUL ECRAN BOLT ---\n").append(p.getString("dump", "(nimic)")).append("\n\n")
        sb.append("--- ECRANUL DE FINAL DE CURSĂ ---\n").append(p.getString("dumpFinal", "(nimic)")).append("\n\n")
        sb.append("--- ULTIMELE 20 OFERTE DIN ISTORIC (fără nume și adrese) ---\n")
        val hm = SimpleDateFormat("dd.MM HH:mm:ss", Locale("ro"))
        Istoric.load(c).take(20).forEach {
            sb.append(hm.format(Date(it.id))).append("  ").append(it.status).append("  ").append("%.2f lei".format(it.pret))
                .append("  prel %.1f + cursă %.1f km".format(it.preluareKm, it.cursaKm))
            if (it.kmReal != null) sb.append("  km reali %.1f".format(it.kmReal))
            sb.append("\n")
        }
        sb.append("\nKm în gol (total): %.1f\n".format(Gol.total(c)))
        return sb.toString()
    }
}
