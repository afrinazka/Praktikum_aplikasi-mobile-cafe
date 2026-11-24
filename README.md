# Praktikum_aplikasi-mobile-cafe
# kasihsayang. Cafe: Alur Item, Perhitungan Harga, dan Penanganan Keranjang Kosong

Dokumen ini menjawab tiga pertanyaan:

1. Operasi aritmatika apa saja yang dipakai untuk menghitung jumlah harga?
2. Bagaimana penanganan jika harga/keranjang kosong?
3. Bagaimana alur item dari awal disimpan sampai ditampilkan?

Lokasi kode ditulis sebagai **nama fungsi** (untuk `CafeViewModel.kt`) dan **nomor baris** (untuk `MainActivity.kt`).

---

## 0. Peta singkat: di mana data disimpan

| Data | Disimpan di | Jenis |
|---|---|---|
| Daftar menu + harga | `Catalog.items` (`CafeViewModel.kt`) | Statis, 15 item, tidak berubah |
| Isi keranjang | `cart` di `CafeViewModel` | `Map<Int, Int>`: **id menu → jumlah** |
| Status member | `member` di `CafeViewModel` | `Member?` (`null` = belum member) |

Semua layar (Menu, Pesanan, Membership) memakai **satu `CafeViewModel` yang sama**, dibuat sekali di `MainActivity` (`private val vm: CafeViewModel by viewModels()`, baris 55). Karena itu keranjang tidak hilang saat pindah tab.

> Penting: keranjang **hanya menyimpan id dan jumlah**, bukan nama atau harga. Nama dan harga selalu diambil dari `Catalog.items`. Jadi harga punya satu sumber saja dan tidak mungkin tidak sinkron.

---

## 1. Operasi aritmatika untuk jumlah harga

Semua perhitungan ada di `CafeViewModel.kt`. UI hanya menampilkan hasilnya.

### 1.1 Menambah / mengurangi jumlah item (+1 / −1)

```kotlin
fun addItem(id: Int) {
    val quantity = cart[id] ?: 0
    cart = cart + (id to quantity + 1)      // penjumlahan: jumlah + 1
}

fun removeItem(id: Int) {
    val quantity = cart[id] ?: 0
    cart = if (quantity <= 1) {
        cart - id                           // jumlah habis → item dihapus dari map
    } else {
        cart + (id to quantity - 1)         // pengurangan: jumlah - 1
    }
}
```

- `id to quantity + 1` dibaca `id to (quantity + 1)`, karena `+` lebih dulu daripada `to`.
- `cart + (id to n)` pada `Map` berarti **menimpa** nilai id itu. Map tidak diubah di tempat, tetapi dibuat map baru. Inilah yang membuat Compose mendeteksi perubahan dan menggambar ulang.

### 1.2 Jumlah seluruh item (angka di tab "Pesanan (n)")

```kotlin
val cartCount: Int
    get() = cart.values.sum()               // penjumlahan semua jumlah
```

Dipakai di `MainActivity.kt` baris 119–122:
`"Pesanan (${vm.cartCount})"` jika lebih dari 0.

### 1.3 Total harga sebelum diskon

```kotlin
val total: Int
    get() = Catalog.items.sumOf { item ->
        item.price * (cart[item.id] ?: 0)   // perkalian: harga × jumlah, lalu dijumlahkan
    }
```

Rumus: **total = Σ (harga item × jumlah item)**.

Contoh: Burger ×3 (25.000) + Spaghetti Carbonara ×1 (37.000) = 75.000 + 37.000 = **112.000**.

### 1.4 Diskon, potongan, dan total akhir

```kotlin
fun calculatePrice(total: Int, isMember: Boolean): PriceSummary {
    val percent = when {
        !isMember -> 0
        total > 600000 -> 10
        total > 300000 -> 7
        total > 200000 -> 6
        total > 100000 -> 4
        else -> 0
    }

    val discount = (total * percent / 100.0).roundToInt()   // perkalian, pembagian, pembulatan

    return PriceSummary(
        total = total,
        percent = percent,
        discount = discount,
        finalPrice = total - discount                       // pengurangan
    )
}
```

| Langkah | Operasi | Contoh (total 112.000, member) |
|---|---|---|
| Tentukan persen | perbandingan `>` berurutan, berhenti di yang pertama cocok | 112.000 > 100.000 → **4%** |
| Hitung potongan | `total × persen ÷ 100.0`, lalu `roundToInt()` | 112.000 × 4 ÷ 100.0 = **4.480** |
| Total akhir | `total − potongan` | 112.000 − 4.480 = **107.520** |

Catatan detail:
- Pembagi `100.0` (Double) dipakai agar hasilnya tidak terpotong oleh pembagian bilangan bulat. `roundToInt()` membulatkan ke Rupiah terdekat.
- Syaratnya `>` (di atas), jadi total tepat Rp100.000 **tidak** dapat diskon.
- Karena `when` berhenti di kondisi pertama yang benar dan dimulai dari angka terbesar, hanya **satu diskon tertinggi** yang berlaku.
- `!isMember -> 0` ada paling atas, jadi bukan member selalu 0%.

### 1.5 Ringkasan yang dipakai UI

```kotlin
val priceSummary: PriceSummary
    get() = calculatePrice(total, isMember)
```

Properti ini **dihitung ulang setiap dibaca**. Setiap ada perubahan keranjang atau status member, hasilnya langsung ikut berubah tanpa tombol "hitung".

### 1.6 Format Rupiah (hanya tampilan)

`MainActivity.kt` baris 69–73:

```kotlin
fun rupiah(value: Int): String {
    return "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)
}
```

Angka `107520` ditampilkan menjadi `Rp107.520`. Fungsi ini tidak mengubah nilai, hanya teks.

---

## 2. Penanganan jika harga / keranjang kosong

Penting dibedakan dua hal:

- **Harga item kosong**: tidak mungkin terjadi, karena setiap `MenuItem` di `Catalog` punya `price: Int` bertipe non-null yang diisi tetap di kode.
- **Keranjang kosong**: ini kasus yang sebenarnya ditangani, di tiga lapisan berikut.

### 2.1 Lapisan data (ViewModel): aman, hasilnya 0

```kotlin
val quantity = cart[id] ?: 0                 // id tidak ada di keranjang → dianggap 0, bukan error/null
```

- `total` pada keranjang kosong: `sumOf` menjumlahkan `price × 0` untuk semua item, hasilnya **0**.
- `calculatePrice(0, ...)`: `0 > 100000` salah → persen **0** → potongan **0** → total akhir **0**.
- `removeItem` pada id yang tidak ada: `quantity = 0`, masuk cabang `<= 1` → `cart - id` pada id yang tidak ada tidak melakukan apa pun. Aman, tidak crash.
- `cartItems` memakai `mapNotNull` dan hanya mengambil item dengan `quantity > 0`, jadi pada keranjang kosong daftarnya kosong.

### 2.2 Lapisan tampilan (`OrderScreen`): pesan pengganti

`MainActivity.kt` baris 752–760:

```kotlin
if (vm.cartItems.isEmpty()) {
    Text(
        "Belum ada menu dipilih. Yuk, pilih favoritmu!",
        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
        textAlign = TextAlign.Center,
        color = Muted
    )
} else {
    // tampilkan daftar item
}
```

Ringkasan harga di bawahnya (baris 819–842) tetap tampil dan menunjukkan **Rp0** (total, potongan, dan total akhir). Label tab juga kembali menjadi "Pesanan" tanpa angka, karena `cartCount > 0` salah (baris 119–122).

### 2.3 Lapisan aksi (tombol Kirim): diblokir dengan pesan

`MainActivity.kt` baris 907–924:

```kotlin
message = when {
    vm.cartItems.isEmpty() -> "Pilih setidaknya satu menu."
    customerName.isBlank() -> "Nama pemesan wajib diisi."
    else -> {
        vm.clearCart()
        customerName = ""
        notes = ""
        showProcessedDialog = true
        "Pesanan berhasil dicatat! Terima kasih ♡"
    }
}
```

Urutan pemeriksaan: keranjang kosong dulu, baru nama kosong. Pesanan **tidak diproses** selama salah satunya gagal, dan keranjang tidak dikosongkan.

| Kondisi | Hasil |
|---|---|
| Keranjang kosong | Teks "Belum ada menu dipilih…", total Rp0, kirim ditolak: "Pilih setidaknya satu menu." |
| Keranjang ada, nama kosong | Kirim ditolak: "Nama pemesan wajib diisi." |
| Keduanya terisi | `clearCart()`, form dikosongkan, dialog "Pesanan sudah di proses" tampil |

**Catatan jujur:** tombol "Kirim pesanan" tetap bisa ditekan saat keranjang kosong. Pengamannya berupa pesan, bukan tombol nonaktif. Jika ingin lebih tegas, tambahkan `enabled = vm.cartItems.isNotEmpty()` pada tombol di baris 907.

---

## 3. Review alur item: dari disimpan sampai ditampilkan

### 3.1 Alur ringkas

```
Catalog.items  (sumber tetap: id, nama, harga, emoji)
      │
      ▼
MenuScreen  → tekan "+ Tambah"  → vm.addItem(item.id)
      │                                  │
      │                                  ▼
      │                    cart: Map<id, jumlah>   ← satu-satunya tempat penyimpanan
      │                                  │
      ├──────────────► cartCount ──────► label tab "Pesanan (n)"
      │                                  │
      ▼                                  ▼
OrderScreen ◄──── vm.cartItems  (gabungan Catalog + cart, hanya jumlah > 0)
      │
      ├─ tombol − / +  → vm.removeItem / vm.addItem  (kembali ke cart)
      ├─ vm.priceSummary → total, persen, potongan, total akhir
      └─ Kirim → vm.clearCart() → cart = emptyMap()
```

### 3.2 Langkah demi langkah

**Langkah 1: Sumber daftar menu.** `Catalog.items` berisi 15 `MenuItem(id, name, price, emoji)`. Dibaca oleh `MenuScreen` (baris 334) untuk membuat kartu menu.

**Langkah 2: Item ditambahkan.** Tombol "+ Tambah" di kartu menu (`MainActivity.kt` baris 383–385) memanggil:

```kotlin
Button(onClick = { vm.addItem(item.id) }) { Text("+ Tambah") }
```

Yang dikirim hanya `item.id`.

**Langkah 3: Item disimpan.** `addItem` mengubah `cart`. Contoh:

| Aksi | Isi `cart` |
|---|---|
| (awal) | `{}` |
| Tambah Burger (id 14) | `{14=1}` |
| Tambah Burger lagi | `{14=2}` |
| Tambah Cheesecake (id 12) | `{14=2, 12=1}` |
| Kurangi Burger | `{14=1, 12=1}` |
| Kurangi Burger lagi | `{12=1}` (id 14 dihapus karena jumlah ≤ 1) |
| Kirim pesanan | `{}` |

Karena `cart` adalah `mutableStateOf`, setiap perubahan otomatis memicu Compose menggambar ulang semua bagian yang membacanya.

**Langkah 4: Badge jumlah di tab.** Bottom bar membaca `vm.cartCount` (baris 119–122) dan menampilkan `Pesanan (n)`.

**Langkah 5: Daftar disusun untuk ditampilkan.**

```kotlin
val cartItems: List<Pair<MenuItem, Int>>
    get() = Catalog.items.mapNotNull { item ->
        val quantity = cart[item.id] ?: 0
        if (quantity > 0) item to quantity else null
    }
```

Fungsi ini menggabungkan data dari dua tempat: info item dari `Catalog` dan jumlah dari `cart`. Item yang jumlahnya 0 dibuang.

> Urutan tampil mengikuti **urutan di katalog menu**, bukan urutan klik. Jika Burger (no. 14) diklik lebih dulu dari Americano (no. 1), Americano tetap muncul di atas.

**Langkah 6: Item ditampilkan di OrderScreen.** `MainActivity.kt` baris 763–811, satu baris per item:

| Bagian baris | Kode | Isi |
|---|---|---|
| Nama | `item.name` | Contoh: "Burger" |
| Rincian | `"${rupiah(item.price)} x $quantity"` | Contoh: "Rp25.000 x 2" |
| Tombol − | `vm.removeItem(item.id)` | Mengurangi 1 |
| Jumlah | `"$quantity"` | Contoh: 2 |
| Tombol + | `vm.addItem(item.id)` | Menambah 1 |

Perhatikan bahwa baris hanya menampilkan **harga satuan × jumlah**. Subtotal per baris (25.000 × 2 = 50.000) tidak ditampilkan. Yang tampil hanya total keseluruhan di bagian ringkasan. Ini bisa ditambahkan jika ingin lebih jelas.

**Langkah 7: Ringkasan harga.** `MainActivity.kt` baris 819–842, memakai `PriceRow` (baris 964–988) dan `vm.priceSummary` (diambil di baris 701):

| Baris | Nilai yang ditampilkan |
|---|---|
| Total sebelum diskon | `rupiah(summary.total)` |
| Diskon membership | `"${summary.percent}%"` |
| Nominal potongan | `rupiah(summary.discount)` |
| Total akhir | `rupiah(summary.finalPrice)` (tebal) |

**Langkah 8: Pesanan dikirim.** Setelah validasi lolos (bagian 2.3), `vm.clearCart()` mengosongkan keranjang (`cart = emptyMap()`). Semua tampilan otomatis kembali ke keadaan kosong.

### 3.3 Contoh lengkap dari awal sampai akhir (member)

1. Tekan "+ Tambah" pada Burger 3 kali → `cart = {14=3}`; tab menjadi "Pesanan (3)".
2. Tekan "+ Tambah" pada Spaghetti Carbonara 1 kali → `cart = {14=3, 9=1}`; tab menjadi "Pesanan (4)".
3. Buka tab Pesanan. `cartItems` mengikuti urutan katalog, jadi Spaghetti (no. 9) muncul sebelum Burger (no. 14):
   - Spaghetti Carbonara: `Rp37.000 x 1`
   - Burger: `Rp25.000 x 3`
4. Ringkasan (sudah member): Total sebelum diskon **Rp112.000**, diskon **4%**, potongan **Rp4.480**, total akhir **Rp107.520**.
5. Isi nama pemesan, tekan "Kirim pesanan" → dialog "Pesanan sudah di proses", keranjang kembali kosong dan ringkasan menjadi Rp0.

---

## 4. Daftar singkat lokasi kode

| Topik | File | Lokasi |
|---|---|---|
| Data menu & harga | `CafeViewModel.kt` | `object Catalog` |
| Tambah / kurangi item | `CafeViewModel.kt` | `addItem`, `removeItem` |
| Jumlah item | `CafeViewModel.kt` | `cartCount` |
| Total harga | `CafeViewModel.kt` | `total` |
| Diskon & total akhir | `CafeViewModel.kt` | `calculatePrice`, `priceSummary` |
| Gabungan item + jumlah | `CafeViewModel.kt` | `cartItems` |
| Kosongkan keranjang | `CafeViewModel.kt` | `clearCart` |
| Format Rupiah | `MainActivity.kt` | baris 69–73 |
| Badge jumlah di tab | `MainActivity.kt` | baris 119–122 |
| Tombol "+ Tambah" | `MainActivity.kt` | baris 383–385 |
| Teks keranjang kosong | `MainActivity.kt` | baris 752–760 |
| Daftar item + tombol −/+ | `MainActivity.kt` | baris 763–811 |
| Ringkasan harga | `MainActivity.kt` | baris 819–842, `PriceRow` 964–988 |
| Validasi & kirim pesanan | `MainActivity.kt` | baris 907–924 |

## 5. Hal yang bisa disebut sebagai pengembangan lanjut

- Menonaktifkan tombol "Kirim pesanan" saat keranjang kosong (`enabled = vm.cartItems.isNotEmpty()`).
- Menampilkan subtotal per baris item.
- Menyimpan keranjang secara permanen (DataStore / Room). Saat ini data hilang jika aplikasi ditutup.
- Unit test untuk `calculatePrice()`: fungsinya murni (tanpa UI), jadi mudah diuji pada batas 100.000, 200.000, 300.000, dan 600.000.
