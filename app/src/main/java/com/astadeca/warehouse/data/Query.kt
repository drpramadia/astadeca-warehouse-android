package com.astadeca.warehouse.data

/**
 * Helper query PostgREST. Murni (tanpa Android) supaya bisa diuji di JVM.
 */
object Query {

    /**
     * Buang karakter yang bisa merusak sintaks `or=(...)` PostgREST.
     * Nilai tetap dibiarkan sebagai teks biasa; `ilike` wildcard `*` dikelola pemanggil.
     */
    fun safeSearch(raw: String): String =
        raw.replace(",", " ").replace("(", "").replace(")", "").trim()

    /** Encode komponen URL untuk nilai filter PostgREST. */
    fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
