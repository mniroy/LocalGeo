# PROJECT BRIEF: Offline Sub-District (Kecamatan) Geofencing App

> **Deployment Target:** Android 10 (API Level 29) and above  
> **Architecture Paradigm:** 100% Offline-First, Zero External API Dependency  
> **Core Engine:** Spatial Indexing (SQLite R-Tree) + Ray-Casting Point-in-Polygon (PIP)

---

## 0. AGENT INITIALIZATION & DATA PROVISIONING (ACTION REQUIRED)

**Attention Agent (Antigravity):** The user will provide a specific GeoJSON file named **`gadm41_IDN_3.json`** containing the official Indonesian Sub-District (Kecamatan / ADM3) boundaries from GADM.

*   **Prerequisite:** Ensure `gadm41_IDN_3.json` is located in your working directory.
*   **Step 1 (Parse & Column Mapping):** Write a Python script (`scripts/prep_spatial_data.py`) using `geopandas` to load the GeoJSON.
    *   Extract the properties. In GADM datasets, the standard column mapping is:
        *   `NAME_3` -> Sub-District (`name_kecamatan`)
        *   `NAME_2` -> Regency/City (`name_kabupaten`)
        *   `NAME_1` -> Province (`name_provinsi`)
    *   *Note: Because this is already Level 3 data, no spatial `dissolve` operation is required.*
*   **Step 2 (Simplify Geometry):** Apply the Douglas-Peucker algorithm (e.g., `gdf.simplify(tolerance=0.005)`) to reduce vertices dramatically. The target database size MUST be under 20 MB for Android asset bundling.
*   **Step 3 (Package to SQLite):** Generate an SQLite database named `indonesia_kecamatan.db`.
    *   Create a main table `sub_districts` (id, name_kecamatan, name_kabupaten, name_provinsi, polygon_blob). 
    *   Create a spatial index using the SQLite R-Tree module: `CREATE VIRTUAL TABLE idx_subdistricts_bbox USING rtree(id, min_lon, max_lon, min_lat, max_lat);`
*   **Step 4 (Inject Asset):** Save the output database directly into the Android project directory at `app/src/main/assets/indonesia_kecamatan.db`.

---

## 1. Executive Summary & Objective

Build an ultra-lightweight, 100% offline Android application that tracks device location via GPS hardware and dynamically displays the current **Kecamatan (Sub-District / ADM3)**, **Kabupaten/Kota (Regency/City / ADM2)**, and **Provinsi (Province / ADM1)**.

### Primary Constraints
- **Zero Network API Calls:** No reliance on Google Maps Geocoding API, Mapbox, or external REST endpoints at runtime.
- **Android 10 (API 29) Full Compatibility:** Strictly compliant with Android 10 location permission models and foreground service lifecycle.
- **Instant Response:** Spatial point lookup in under **50 ms** on standard Android 10 mobile chipsets.

---

## 2. Technical Stack & Target Specification

| Component | Specification | Description / Notes |
| :--- | :--- | :--- |
| **Minimum SDK** | `minSdkVersion 29` (Android 10) | Ensures full hardware & permission compatibility with Android 10+ |
| **Target SDK** | `targetSdkVersion 34` | Modern standard compliance while maintaining Android 10 backward compatibility |
| **Core Framework** | Native Android (Kotlin) / Flutter | Direct access to LocationManager / FusedLocationProviderClient |
| **Database Engine** | SQLite (with R-Tree Module) | Hardware-accelerated 2D bounding box spatial index |
| **Spatial Computation**| Custom Ray-Casting PIP | Pure algorithmic point-in-polygon without map canvas overhead |
| **Service Model** | Android Foreground Service | Continuous GPS listening with sticky notification when app is backgrounded |

---

## 3. Android 10 (API Level 29) Specific Implementation Requirements

Android 10 introduced strict location privacy and background execution constraints that must be explicitly configured:

### 3.1 Permission Manifest Architecture
```xml
<!-- Required for high-precision satellite fix -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Android 10 Specific Background Location -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Foreground Service for persistent offline tracking -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

### 3.2 Foreground Service Declaration
Under Android 10, foreground services that access location must specify the `foregroundServiceType`:
```xml
<service
    android:name=".services.GeofenceTrackingService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="location" />
```

### 3.3 Runtime Permission Workflow (Android 10 Model)
1. Request `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` in-app first.
2. If background tracking is toggled on, prompt user with explicit disclosure to select *"Allow all the time"* in system settings.
3. Use a persistent notification with `NotificationChannel` (Importance LOW/DEFAULT) displaying current sub-district name.

---

## 4. Algorithmic Execution Workflow

```text
[GPS Hardware Stream (Lat, Lon, Accuracy)]
                   │
                   ▼ (Check if Accuracy <= 50m)
[Stage 1: R-Tree Bounding Box Filter]
       Query: SELECT id FROM idx_subdistricts_bbox 
              WHERE min_lon <= :lon AND max_lon >= :lon 
                AND min_lat <= :lat AND max_lat >= :lat;
                   │
                   ▼ (Returns 1 - 3 Candidate Polygons)
[Stage 2: Point-in-Polygon Ray-Casting Engine]
       Evaluate coordinate against candidate vertices (including MultiPolygons)
                   │
                   ▼ (Match Identified)
[Stage 3: Hysteresis / Anti-Flicker Filter]
       Confirm match across 2 consecutive readings before UI state update
                   │
                   ▼
[UI Layer Update & Persistent Notification Sync]
```

---

## 5. UI & UX Specifications

### Main Screen (Zero Map Canvas, Pure Typography & Status)
- **Header:** GPS Status badge (e.g., `Satellite Locked (±8m)`, `Searching...`, `Permission Denied`).
- **Primary Display Card:**
  - **Kecamatan Name:** Bold Headline (28sp - 34sp).
  - **Kabupaten / Kota & Provinsi:** Sub-headline (16sp - 18sp).
- **Secondary Telemetry Card:**
  - Current Coordinates: Latitude, Longitude (formatted decimal).
  - Total polygon vertices evaluated & search latency in milliseconds.
- **Status States:**
  - `NO_FIX`: "Mencari Sinyal GPS..."
  - `OUT_OF_BOUNDS`: "Di Luar Batas Wilayah Darat / Perairan"
  - `LOW_ACCURACY`: "Akurasi Rendah (>50m), Menunggu Sinyal Stabil..."

---

## 6. Step-by-Step Implementation Tasks

- [ ] **Task 1 (Data Prep):** Agent to execute Step 0. Wait for user to provide `gadm41_IDN_3.json`, map GADM attributes (`NAME_3`, `NAME_2`, `NAME_1`), simplify geometry, generate R-Tree SQLite DB, and place in assets.
- [ ] **Task 2 (Android Core):** Scaffold Android project with `minSdkVersion 29` and configure SQLite asset reader.
- [ ] **Task 3 (Spatial Engine):** Implement the two-stage locator (R-Tree BBox query -> Ray-Casting validator for MultiPolygons).
- [ ] **Task 4 (Location Service):** Build `GeofenceTrackingService` foreground service with Android 10 notification channel and permission flow.
- [ ] **Task 5 (UI & Hysteresis):** Implement reactive UI state with a 2-tick hysteresis filter to prevent bouncing at borders.
