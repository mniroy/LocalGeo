# 🛰️ Offline Indonesia Kecamatan Geofencing & ESP32 Integration

A 100% offline real-time sub-district (Kecamatan) geofencing Android application built with Kotlin, Jetpack Compose, SQLite spatial queries, and Ray-Casting Point-in-Polygon (PIP) engine. Includes live Bluetooth integration to stream real-time location and sub-district data directly to an **ESP32 microcontroller** for dashboard displays.

---

## 📱 Features
- **100% Offline PIP Engine**: Evaluates >7,000 Indonesia sub-districts (Kecamatan) in ~0.5ms directly on device using optimized SQLite spatial bounding boxes and binary polygon blobs.
- **GPS & A-GPS Integration**: Fallback support for hardware GPS sat, Wi-Fi triangulation, and Cell tower location fixes.
- **Minimalist Info Dashboard**: High-contrast, clean UI showing strictly Kecamatan, Kota/Kabupaten, and Provinsi using Roboto font.
- **Dynamic Rotation & Theme Controls**: Easily switch themes (System, Dark, Light) and force screen orientation (Portrait, Landscape, Reverse Landscape) via the settings menu.
- **Live ESP32 Bluetooth Streaming**: Streams JSON payloads over Bluetooth SPP whenever location or sub-district changes.

---

## ⚡ ESP32 Setup Guide & Documentation

### 🔌 1. Data Communication Protocol
The Android application streams data over **Bluetooth Classic (SPP - Serial Port Profile)**. Whenever a location fix or sub-district update occurs, the app sends a JSON string terminated by a newline character (`\n`):

```json
{
  "kecamatan": "Medan Barat",
  "kabupaten": "Kota Medan",
  "provinsi": "Sumatera Utara",
  "latitude": 3.59220,
  "longitude": 98.67850,
  "timestamp": 1786978754000
}
```

---

### 🧰 2. Hardware & Software Requirements

#### Hardware:
- Any standard **ESP32 Development Board** (ESP32 WROOM-32, NodeMCU-32S, ESP32-S3 with BT Classic support, etc.)
- Micro-USB or USB-C cable for flashing and serial monitoring.
- (Optional) I2C OLED Display (SSD1306 128x64) or LCD 1602 for physical dashboard output.

#### Software:
- **Arduino IDE** (v2.0 or newer)
- **ESP32 Board Package** (`esp32` by Espressif Systems) installed via Board Manager.
- **ArduinoJson Library** (v6.x or v7.x) installed via Library Manager.

---

### 💻 3. Complete ESP32 Arduino Code

Copy and upload the following code to your ESP32 board using Arduino IDE:

```cpp
#include "BluetoothSerial.h"
#include <ArduinoJson.h>

// Check if Bluetooth Classic is enabled
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUETOOTH_CB_HM_INCLUDED)
#error Bluetooth is not enabled! Please run `make menuconfig` to enable it
#endif

BluetoothSerial SerialBT;

// Built-in LED pin (usually GPIO 2 on standard ESP32 boards)
const int LED_PIN = 2;

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  // Initialize Bluetooth Serial with device name
  SerialBT.begin("ESP32_Geofence_Display");
  Serial.println("==================================================");
  Serial.println("ESP32 Bluetooth Geofence Receiver Started!");
  Serial.println("Pair your phone with: ESP32_Geofence_Display");
  Serial.println("==================================================");
}

void loop() {
  // Check if data is available from Bluetooth
  if (SerialBT.available()) {
    // Read JSON string payload until newline
    String jsonPayload = SerialBT.readStringUntil('\n');
    jsonPayload.trim();

    if (jsonPayload.length() > 0) {
      Serial.println("\n[BT RECEIVED] Raw Payload:");
      Serial.println(jsonPayload);

      // Allocate JSON document buffer
      StaticJsonDocument<512> doc;
      DeserializationError error = deserializeJson(doc, jsonPayload);

      if (!error) {
        // Extract fields
        const char* kecamatan = doc["kecamatan"] | "N/A";
        const char* kabupaten = doc["kabupaten"] | "N/A";
        const char* provinsi  = doc["provinsi"]  | "N/A";
        double latitude       = doc["latitude"]  | 0.0;
        double longitude      = doc["longitude"] | 0.0;
        long long timestamp   = doc["timestamp"] | 0;

        // Visual LED indicator (Blink on data receive)
        digitalWrite(LED_PIN, HIGH);
        delay(100);
        digitalWrite(LED_PIN, LOW);

        // Display formatted information on Serial Monitor
        Serial.println("--------------------------------------------------");
        Serial.print("📍 KECAMATAN : "); Serial.println(kecamatan);
        Serial.print("🏙️ KAB/KOTA  : "); Serial.println(kabupaten);
        Serial.print("🗺️ PROVINSI  : "); Serial.println(provinsi);
        Serial.print("🌐 KOORDINAT : "); Serial.print(latitude, 5); Serial.print(", "); Serial.println(longitude, 5);
        Serial.println("--------------------------------------------------");
      } else {
        Serial.print("❌ JSON Parsing Failed: ");
        Serial.println(error.c_str());
      }
    }
  }

  delay(10);
}
```

---

### 📲 4. Step-by-Step How to Connect Android to ESP32

1. **Flash ESP32**: Upload the Arduino code above to your ESP32 board.
2. **Open Serial Monitor**: Open Arduino IDE Serial Monitor at **115200 baud**.
3. **Pair Bluetooth on Android**:
   - Go to your Android Phone's **Settings -> Bluetooth**.
   - Scan for new devices.
   - Tap **ESP32_Geofence_Display** to pair.
4. **Connect in App**:
   - Open the **Kecamatan Geofence** Android app.
   - Tap the small **Gear Icon (Settings)** at the top-right corner.
   - Under **Koneksi ESP32 (Bluetooth)**, locate `ESP32_Geofence_Display`.
   - Tap **Hubungkan**.
5. **Verify Data Stream**:
   - Once connected, the app status changes to `Terhubung: ESP32_Geofence_Display`.
   - Watch the Arduino IDE Serial Monitor — whenever your device moves or location updates, real-time Kecamatan, Kabupaten, and Provinsi data will print automatically!

---

## 🛠️ Build & Development
- **IDE**: Android Studio Ladybug / Antigravity IDE
- **Build System**: Gradle 9.1 / Kotlin 2.x
- **Min SDK**: 24 (Android 7.0+)
- **Target SDK**: 36
