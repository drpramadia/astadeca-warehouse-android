# ASTADECA Warehouse (Android)

APK warehouse-only. Native Kotlin + Jetpack Compose. Terpisah total dari `erp-astadeca` web
(tidak ada shared code). Data via Supabase REST + RPC, project ref `huaggvlpknwzarpgjkso`.

## Prasyarat
- JDK 17 (`JAVA_HOME` diset)
- Android SDK: `platforms;android-34`, `build-tools;34.0.0`, `platform-tools`
- `local.properties` berisi:
  ```
  sdk.dir=C:\\Users\\<user>\\android-sdk
  supabase.url=https://<ref>.supabase.co
  supabase.anonKey=<anon-key>
  ```

## Build
```
gradlew.bat assembleDebug      # APK debug (Windows)
./gradlew assembleDebug        # APK debug (Linux/macOS/CI)
./gradlew testDebugUnitTest    # unit test
./gradlew assembleRelease      # APK release ter-sign (butuh keystore.properties)
```
Output: `app/build/outputs/apk/{debug,release}/`.

## Signing release
`keystore.properties` (tidak di-commit) di root:
```
storeFile=C:/Users/<user>/.android-keystores/astadeca-warehouse.jks
storePassword=<pw>
keyAlias=astadeca
keyPassword=<pw>
```
Jika file ini tidak ada, `assembleRelease` tetap jalan tapi menghasilkan APK unsigned.
**Jangan tulis file ini dengan BOM UTF-8** — `Properties.load` akan salah baca key pertama.

## Verifikasi integrasi (butuh jaringan)
```
node verify-integration.mjs
```
Cek APK menunjuk project Supabase yang benar + tabel warehouse tersedia.

## Catatan JDK
JDK 17 Microsoft build (`17.0.20.101`) crash di C2 compiler (`EXCEPTION_ACCESS_VIOLATION`).
`gradle.properties` memakai `-XX:TieredStopAtLevel=1` sebagai mitigasi.
Upgrade ke Temurin/Adoptium lalu hapus flag itu.

## Fitur
Login (RPC `login_with_username`), Inventory, Goods Receipt, Goods Issue manual,
Basket, QC, Waste, Surat Jalan (DO), Scan barcode/QR (ML Kit).
