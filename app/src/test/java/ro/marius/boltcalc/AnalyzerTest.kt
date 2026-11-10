package ro.marius.boltcalc

import org.junit.Assert.*
import org.junit.Test

class AnalyzerTest {

    private val defaultParams = Params()

    @Test
    fun `parseaza - standard Romanian format with NET price`() {
        val texts = listOf(
            "lei 25.50 (NET, tax included)",
            "3 min • 2.1 km",
            "9 min • 5.4 km"
        )
        val oferta = Analyzer.parseaza(texts)

        assertNotNull(oferta)
        assertEquals(25.50, oferta!!.pret, 0.001)
        assertEquals(2.1, oferta.preluareKm, 0.001)
        assertEquals(5.4, oferta.cursaKm, 0.001)
    }

    @Test
    fun `parseaza - price after distance`() {
        val texts = listOf(
            "3 min • 2.1 km",
            "9 min • 5.4 km",
            "25.50 lei"
        )
        val oferta = Analyzer.parseaza(texts)

        assertNotNull(oferta)
        assertEquals(25.50, oferta!!.pret, 0.001)
    }

    @Test
    fun `parseaza - comma decimal separator`() {
        val texts = listOf(
            "lei 13,50 (NET, tax included)",
            "3 min • 2,1 km",
            "9 min • 5,4 km"
        )
        val oferta = Analyzer.parseaza(texts)

        assertNotNull(oferta)
        assertEquals(13.50, oferta!!.pret, 0.001)
        assertEquals(2.1, oferta.preluareKm, 0.001)
        assertEquals(5.4, oferta.cursaKm, 0.001)
    }

    @Test
    fun `parseaza - meters converted to km`() {
        val texts = listOf(
            "lei 15.00",
            "300 m",
            "2.5 km"
        )
        val oferta = Analyzer.parseaza(texts)

        assertNotNull(oferta)
        assertEquals(0.3, oferta!!.preluareKm, 0.001)
        assertEquals(2.5, oferta.cursaKm, 0.001)
    }

    @Test
    fun `parseaza - returns null for incomplete data`() {
        val texts = listOf("lei 20.00", "5 km")
        val oferta = Analyzer.parseaza(texts)
        assertNull(oferta)
    }

    @Test
    fun `parseaza - returns null for empty input`() {
        val oferta = Analyzer.parseaza(emptyList())
        assertNull(oferta)
    }

    @Test
    fun `analizeaza - excellent ride with bonus`() {
        val params = defaultParams.copy(
            bonusExtra = 2.0,
            minExcelent = 2.0,
            minBun = 1.0
        )
        val oferta = Oferta(pret = 30.0, preluareKm = 1.0, cursaKm = 8.0)

        val r = Analyzer.analizeaza(oferta, params)

        assertEquals(Verdict.EXCELENT, r.verdict)
        assertTrue(r.profit > 0)
        assertTrue(r.ef >= params.minExcelent)
    }

    @Test
    fun `analizeaza - rejects long pickup`() {
        val params = defaultParams.copy(maxPreluareKm = 2.0)
        val oferta = Oferta(pret = 50.0, preluareKm = 5.0, cursaKm = 10.0)

        val r = Analyzer.analizeaza(oferta, params)

        assertEquals(Verdict.REFUZA, r.verdict)
        assertTrue(r.text.contains("preluare prea lungă"))
    }

    @Test
    fun `analizeaza - rejects unprofitable ride`() {
        val params = defaultParams.copy(
            consumLa100 = 20.0,  // very high consumption
            pretMotorina = 20.0, // very expensive fuel
            minBun = 1.0
        )
        val oferta = Oferta(pret = 10.0, preluareKm = 1.0, cursaKm = 5.0)

        val r = Analyzer.analizeaza(oferta, params)

        assertEquals(Verdict.REFUZA, r.verdict)
        assertTrue(r.text.contains("pierzi pe motorină"))
    }

    @Test
    fun `analizeaza - net price toggle`() {
        val grossPrice = 100.0
        val commission = 0.25

        val netParams = Params(pretEsteNet = true, comisionBolt = commission)
        val grossParams = Params(pretEsteNet = false, comisionBolt = commission)

        val ofertaNet = Oferta(pret = grossPrice, preluareKm = 1.0, cursaKm = 10.0)
        val ofertaGross = Oferta(pret = grossPrice, preluareKm = 1.0, cursaKm = 10.0)

        val rNet = Analyzer.analizeaza(ofertaNet, netParams)
        val rGross = Analyzer.analizeaza(ofertaGross, grossParams)

        // Net price should yield higher profit (no commission deducted)
        assertTrue(rNet.profit > rGross.profit)
        assertEquals(grossPrice * (1 - commission), rGross.profit + rGross.cost - rGross.bonus, 0.01)
    }

    @Test
    fun `analizeaza - bonus per ride calculation`() {
        val bonus = Bonus(amount = 100.0, target = 10, done = 2, endMillis = System.currentTimeMillis() + 86400000, on = true)
        val params = defaultParams.copy(bonuses = listOf(bonus), bonusExtra = 0.0)

        val oferta = Oferta(pret = 50.0, preluareKm = 1.0, cursaKm = 10.0)
        val r = Analyzer.analizeaza(oferta, params)

        // 100 lei / (10 - 2) = 12.5 lei per ride
        assertEquals(12.5, r.bonus, 0.01)
    }

    @Test
    fun `analizeaza - expired bonus not counted`() {
        val bonus = Bonus(amount = 100.0, target = 10, done = 2, endMillis = System.currentTimeMillis() - 86400000, on = true)
        val params = defaultParams.copy(bonuses = listOf(bonus))

        val oferta = Oferta(pret = 50.0, preluareKm = 1.0, cursaKm = 10.0)
        val r = Analyzer.analizeaza(oferta, params)

        assertEquals(0.0, r.bonus, 0.01)
    }

    @Test
    fun `analizeaza - disabled bonus not counted`() {
        val bonus = Bonus(amount = 100.0, target = 10, done = 2, endMillis = System.currentTimeMillis() + 86400000, on = false)
        val params = defaultParams.copy(bonuses = listOf(bonus))

        val oferta = Oferta(pret = 50.0, preluareKm = 1.0, cursaKm = 10.0)
        val r = Analyzer.analizeaza(oferta, params)

        assertEquals(0.0, r.bonus, 0.01)
    }

    @Test
    fun `analizeaza - completed bonus not counted`() {
        val bonus = Bonus(amount = 100.0, target = 10, done = 10, endMillis = System.currentTimeMillis() + 86400000, on = true)
        val params = defaultParams.copy(bonuses = listOf(bonus))

        val oferta = Oferta(pret = 50.0, preluareKm = 1.0, cursaKm = 10.0)
        val r = Analyzer.analizeaza(oferta, params)

        assertEquals(0.0, r.bonus, 0.01)
    }

    @Test
    fun `analizeaza - multiple active bonuses summed`() {
        val bonus1 = Bonus(amount = 100.0, target = 10, done = 2, endMillis = System.currentTimeMillis() + 86400000, on = true)
        val bonus2 = Bonus(amount = 200.0, target = 20, done = 5, endMillis = System.currentTimeMillis() + 86400000, on = true)
        val params = defaultParams.copy(bonuses = listOf(bonus1, bonus2))

        val oferta = Oferta(pret = 50.0, preluareKm = 1.0, cursaKm = 10.0)
        val r = Analyzer.analizeaza(oferta, params)

        // 100/8 + 200/15 = 12.5 + 13.33 = 25.83
        assertEquals(25.83, r.bonus, 0.01)
    }

    @Test
    fun `analizeaza - zero distance handled gracefully`() {
        val params = defaultParams.copy(minBun = -1000.0) // accept any
        val oferta = Oferta(pret = 10.0, preluareKm = 0.0, cursaKm = 0.0)

        val r = Analyzer.analizeaza(oferta, params)

        // Should not crash, distance coerced to 0.1
        assertNotNull(r)
        assertTrue(r.dist >= 0.1)
    }
}