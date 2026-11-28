package ro.marius.boltcalc

import org.junit.Assert.*
import org.junit.Test

class AnalyzerTest {

    private val defaultParams = Params(
        consumLa100 = 6.5,
        pretMotorina = 10.90,
        comisionBolt = 0.25,
        pretEsteNet = true,
        maxPreluareKm = 3.5,
        minExcelent = 2.5,
        minBun = 1.5,
        minPret = null,
        maxPret = null,
        minRating = null,
        blocate = emptyList()
    )

    @Test
    fun `parseaza - standard Romanian format with NET price`() {
        val texts = listOf(
            "lei 25.50 (NET, tax included)",
            "3 min • 2.1 km",
            "9 min • 5.4 km",
            "Acceptă"
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
            "25.50 lei",
            "Acceptă"
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
            "9 min • 5,4 km",
            "Acceptă"
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
            "2.5 km",
            "Acceptă"
        )
        val oferta = Analyzer.parseaza(texts)

        assertNotNull(oferta)
        assertEquals(0.3, oferta!!.preluareKm, 0.001)
        assertEquals(2.5, oferta.cursaKm, 0.001)
    }

    @Test
    fun `parseaza - returns null for incomplete data`() {
        val texts = listOf("lei 20.00", "5 km", "Acceptă")
        val oferta = Analyzer.parseaza(texts)
        assertNull(oferta)
    }

    @Test
    fun `parseaza - returns null for empty input`() {
        val oferta = Analyzer.parseaza(emptyList())
        assertNull(oferta)
    }

    private fun makeOferta(pret: Double, preluareKm: Double, cursaKm: Double): Oferta =
        Oferta(pret, preluareKm, cursaKm, null, null, null, "", "", "")

    @Test
    fun `analizeaza - excellent ride`() {
        val params = defaultParams.copy(minExcelent = 2.0, minBun = 1.0)
        val oferta = makeOferta(30.0, 1.0, 8.0)

        val r = Analyzer.analizeaza(oferta, params)

        assertEquals(Verdict.EXCELENT, r.verdict)
        assertTrue(r.profit > 0)
        assertTrue(r.ef >= params.minExcelent)
    }

    @Test
    fun `analizeaza - rejects long pickup`() {
        val params = defaultParams.copy(maxPreluareKm = 2.0)
        val oferta = makeOferta(50.0, 5.0, 10.0)

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
        val oferta = makeOferta(10.0, 1.0, 5.0)

        val r = Analyzer.analizeaza(oferta, params)

        assertEquals(Verdict.REFUZA, r.verdict)
        assertTrue(r.text.contains("pierzi pe motorină"))
    }

    @Test
    fun `analizeaza - net price toggle`() {
        val grossPrice = 100.0
        val commission = 0.25

        val netParams = defaultParams.copy(pretEsteNet = true, comisionBolt = commission)
        val grossParams = defaultParams.copy(pretEsteNet = false, comisionBolt = commission)

        val ofertaNet = makeOferta(grossPrice, 1.0, 10.0)
        val ofertaGross = makeOferta(grossPrice, 1.0, 10.0)

        val rNet = Analyzer.analizeaza(ofertaNet, netParams)
        val rGross = Analyzer.analizeaza(ofertaGross, grossParams)

        // Net price should yield higher profit (no commission deducted)
        assertTrue(rNet.profit > rGross.profit)
        assertEquals(grossPrice * (1 - commission), rGross.profit + rGross.cost, 0.01)
    }

    @Test
    fun `analizeaza - zero distance handled gracefully`() {
        val params = defaultParams.copy(minBun = -1000.0) // accept any
        val oferta = makeOferta(10.0, 0.0, 0.0)

        val r = Analyzer.analizeaza(oferta, params)

        // Should not crash, distance coerced to 0.1 internally
        assertNotNull(r)
        assertTrue(r.ef.isFinite())
    }
}