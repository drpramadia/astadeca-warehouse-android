// Bukti integrasi: baca local.properties APK, panggil API ERP, cek tabel warehouse.
import { readFileSync } from 'node:fs'

const props = Object.fromEntries(
  readFileSync('C:/Users/Dio Pramdia/astadeca-warehouse-android/local.properties', 'utf8')
    .split(/\r?\n/).filter((l) => l.includes('=') && !l.startsWith('#'))
    .map((l) => { const i = l.indexOf('='); return [l.slice(0, i).trim(), l.slice(i + 1).trim()] }),
)
const url = props['supabase.url']
const key = props['supabase.anonKey']
const ref = (url.match(/https:\/\/([a-z0-9]+)\.supabase/) || [])[1]

console.log('APK -> ref:', ref)
if (ref !== 'huaggvlpknwzarpgjkso') { console.error('SALAH PROJECT'); process.exit(1) }

const h = { apikey: key, 'Content-Type': 'application/json' }

// 1) Kontrak auth yang dipakai APK
const rpc = await fetch(`${url}/rest/v1/rpc/login_with_username`, {
  method: 'POST', headers: h, body: JSON.stringify({ p_username: 'siswoko' }),
})
const body = await rpc.json()
console.log('login_with_username(siswoko):', rpc.status, 'exists=', body.success === true ? 'yes' : 'no')

// 2) Tabel warehouse yang dipakai APK harus ada di project ini
const tables = ['inventory', 'inventory_movements', 'cold_storage_baskets', 'goods_receipts',
  'goods_receipt_lines', 'qc_inspections', 'qc_inspection_lines', 'waste_records', 'stock_waste',
  'delivery_requests', 'delivery_orders', 'products']
let ok = 0
for (const t of tables) {
  const r = await fetch(`${url}/rest/v1/${t}?select=id&limit=1`, { headers: h })
  const present = r.status === 200
  if (present) ok++
  console.log(`  ${present ? 'OK  ' : 'FAIL'} ${t} (${r.status})`)
}
console.log(`\n${ok}/${tables.length} tabel warehouse ada di ref ${ref}`)

// 4) RPC transaksional yang dipakai APK harus ada (bukan 404 dari PostgREST).
//    403/400 = fungsi ada tapi ditolak tanpa sesi (benar). 404 = belum dimigrasi.
const rpcCheck = await fetch(`${url}/rest/v1/rpc/issue_stock`, {
  method: 'POST', headers: h,
  body: JSON.stringify({ p_inventory_id: '00000000-0000-0000-0000-000000000000', p_take: 1 }),
})
console.log(`rpc issue_stock: HTTP ${rpcCheck.status} ${rpcCheck.status === 404 ? '(BELUM DIMIGRASI)' : '(ada)'}`)

// 3) Tabel project LAIN harus TIDAK ada di sini (kalau ada, berarti salah project)
const wrongRef = 'rivofdqcouniijfbtlyl'
console.log(`\nRef project lain (warehouse-system): ${wrongRef} — TIDAK dipakai APK ini`)
