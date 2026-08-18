# Daerah Sini - Offline Indonesia Kecamatan Geofencing & ESP32 Bluetooth Sync

**Daerah Sini** adalah aplikasi Android berbasis Kotlin Jetpack Compose untuk penentuan wilayah Kecamatan, Kota/Kabupaten, dan Provinsi se-Indonesia secara **100% Offline** menggunakan Point-in-Polygon (PIP) ray-casting algorithm dan database SQLite spatial.

---

## 🌟 Fitur Utama

1. **Dashboard Tampilan Informasi Minimalis**:
   - Menampilkan **Kecamatan**, **Kota/Kabupaten**, dan **Provinsi** dengan font Roboto.
   - Pilihan **Tema Warna**: Dark Mode (Background Hitam), Light Mode (Background Putih), atau Sistem Default.
   - Pilihan **Ukuran Teks Dashboard**: Kecil (85%), Normal (100%), Besar (125%), Sangat Besar (150%), Maksimal (175%).
   - **Mode Layar Penuh (Immersive)**: Sembunyikan status bar & bar navigasi sistem.
   - Pilihan **Rotasi Layar**: Otomatis (Sensor), Portrait, Landscape, Reverse Landscape.
2. **Foreground Service & Background Tracking**:
   - Geofence tracking tetap aktif berjalan di background dengan notifikasi persisten.
   - Opsi pengontrolan notifikasi persisten (ongoing) dari menu Pengaturan.
3. **Koneksi ESP32 via Bluetooth (SPP)**:
   - Terhubung secara otomatis ke modul ESP32 Bluetooth Classic (SPP).
   - Mengirim data JSON secara real-time saat terjadi perubahan lokasi.
4. **Tentang Aplikasi**:
   - Informasi aplikasi, versi, dan logo copyright **© MNIROY**.

---

## 🔌 Panduan Konfigurasi ESP32 Bluetooth

### 1. Rangkaian Hardware ESP32
- ESP32 Development Board (ESP32-WROOM-32 / DevKit v1)
- Kabel Micro USB / USB-C

### 2. Kode Program ESP32 (Arduino IDE)

Upload kode C++ berikut ke ESP32 menggunakan **Arduino IDE**:

```cpp
#include "BluetoothSerial.h"
#include <ArduinoJson.h>

BluetoothSerial SerialBT;

void setup() {
  Serial.begin(115200);
  
  // Nama Bluetooth yang akan terdeteksi di HP
  SerialBT.begin("DaerahSini_ESP32"); 
  Serial.println("ESP32 Bluetooth Serial Siap! Pasangkan dengan HP.");
}

void loop() {
  if (SerialBT.available()) {
    String jsonPayload = SerialBT.readStringUntil('\n');
    jsonPayload.trim();
    
    if (jsonPayload.length() > 0) {
      Serial.println("\n[DATA DITERIMA DARI HP]");
      Serial.println(jsonPayload);
      
      StaticJsonDocument<512> doc;
      DeserializationError error = deserializeJson(doc, jsonPayload);
      
      if (!error) {
        const char* kecamatan = doc["kecamatan"];
        const char* kabupaten = doc["kabupaten"];
        const char* provinsi = doc["provinsi"];
        double lat = doc["lat"];
        double lon = doc["lon"];
        
        Serial.print("📍 Kecamatan : "); Serial.println(kecamatan);
        Serial.print("🏙️ Kabupaten : "); Serial.println(kabupaten);
        Serial.print("🗺️ Provinsi  : "); Serial.println(provinsi);
        Serial.print("🌐 Koordinat : "); Serial.print(lat, 6); Serial.print(", "); Serial.println(lon, 6);
      } else {
        Serial.print("JSON Error: ");
        Serial.println(error.c_str());
      }
    }
  }
  delay(20);
}
```

---

## 🛠️ Cara Penggunaan Aplikasi

1. Buka aplikasi **Daerah Sini**.
2. Berikan izin lokasi GPS / A-GPS.
3. Masuk ke **Pengaturan** (tombol gigi di kanan atas):
   - **Koneksi ESP32 (Bluetooth)**: Pasangkan & pilih ESP32 terpasang.
   - **Tampilan & Rotasi Layar**: Atur ukuran teks, mode layar penuh, dan rotasi.
   - **Tema Warna**: Pilih tema gelap / terang.
   - **Notifikasi**: Atur notifikasi persisten background.
   - **Tentang Aplikasi**: Informasi versi & copyright.

---

## 👨‍💻 Publisher

Developed & Published by **MNIROY**  
© 2026 MNIROY. All rights reserved.
