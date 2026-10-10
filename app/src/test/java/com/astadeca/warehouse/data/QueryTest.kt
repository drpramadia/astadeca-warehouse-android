package com.astadeca.warehouse.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QueryTest {

    @Test fun safeSearch_stripsSyntaxBreakingChars() {
        assertEquals("a bc", Query.safeSearch("a,b(c)"))
    }

    @Test fun safeSearch_trimsWhitespace() {
        assertEquals("batch", Query.safeSearch("  batch  "))
    }

    @Test fun safeSearch_keepsNormalText() {
        assertEquals("SKU-123", Query.safeSearch("SKU-123"))
    }

    @Test fun enc_escapesSpaces() {
        assertFalse(Query.enc("a b").contains(' '))
    }

    @Test fun enc_escapesAmpersand() {
        assertEquals("a%26b", Query.enc("a&b"))
    }
}
