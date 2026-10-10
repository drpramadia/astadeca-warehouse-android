package com.astadeca.warehouse.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Format angka & tanggal. Murni (tanpa Compose/Android) supaya bisa diuji
 * di JVM lewat unit test.
 */

/** Angka dengan pemisah ribuan gaya Indonesia (titik), desimal koma. */
fun fmtNum(v: Double): String =
    String.format(Locale("id", "ID"), "%,.2f", v).trimEnd('0').trimEnd(',')

fun fmtNum(s: String?): String = fmtNum(s?.toDoubleOrNull() ?: 0.0)

/** Angka bulat tanpa desimal bila memang bulat (untuk kg). */
fun fmtKg(v: Double): String =
    if (v == Math.floor(v) && v.isFinite()) "${v.toLong()}" else String.format(Locale.US, "%.2f", v)

fun fmtKg(s: String?): String = fmtKg(s?.toDoubleOrNull() ?: 0.0)

private fun parseIso(iso: String): Date? {
    val cut = if (iso.length >= 19) iso.substring(0, 19) else iso
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    parser.timeZone = TimeZone.getTimeZone("UTC")
    return parser.parse(cut)
}

/** Format tanggal ringkas: 10 Okt 2026. */
fun fmtDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    return runCatching {
        val d = parseIso(iso) ?: return iso.take(10)
        SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(d)
    }.getOrDefault(iso.take(10))
}

/** Format tanggal + jam: 10 Okt 2026 14:30. */
fun fmtDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    return runCatching {
        val d = parseIso(iso) ?: return iso
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(d)
    }.getOrDefault(iso)
}
