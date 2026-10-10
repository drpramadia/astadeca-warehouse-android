package com.astadeca.warehouse.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test fun fmtNum_usesIndonesianSeparators() {
        assertEquals("1.234,56", fmtNum(1234.56))
    }

    @Test fun fmtNum_trimsTrailingZeros() {
        assertEquals("1.000", fmtNum(1000.0))
    }

    @Test fun fmtNum_nullStringIsZero() {
        assertEquals("0", fmtNum(null as String?))
    }

    @Test fun fmtKg_wholeNumberNoDecimals() {
        assertEquals("25", fmtKg(25.0))
    }

    @Test fun fmtKg_fractionKeepsTwoDecimals() {
        assertEquals("25.50", fmtKg(25.5))
    }

    @Test fun fmtDate_parsesIsoUtc() {
        assertEquals("10 Okt 2026", fmtDate("2026-10-10T04:42:13Z"))
    }

    @Test fun fmtDate_blankReturnsDash() {
        assertEquals("-", fmtDate(null))
        assertEquals("-", fmtDate(""))
    }

    @Test fun fmtDate_invalidFallsBackToFirstTenChars() {
        assertEquals("bukan-tang", fmtDate("bukan-tanggal"))
    }

    @Test fun fmtDateTime_convertsUtcToLocalZone() {
        // 04:42 UTC -> waktu lokal mesin. Format: "dd MMM yyyy HH:mm".
        val out = fmtDateTime("2026-10-10T04:42:13Z")
        assertTrue(out.matches(Regex("\\d{2} \\w+ \\d{4} \\d{2}:\\d{2}")))
        // Zona mesin bukan UTC -> jam lokal beda dari 04:42.
        if (java.util.TimeZone.getDefault().rawOffset != 0) assertFalse(out.endsWith(" 04:42"))
    }

    @Test fun fmtDateTime_blankReturnsDash() {
        assertEquals("-", fmtDateTime(null))
    }
}
