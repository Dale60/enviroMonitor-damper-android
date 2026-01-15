# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Install debug APK on connected device
./gradlew installDebug
```

Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

This is an Android BLE scanner app for locating HVAC damper beacons with ARCore-based floor mapping. Built with Kotlin, Jetpack Compose, and Material 3.

### Key Components

- **MainActivity** (`app/src/main/java/.../MainActivity.kt`): Entry point handling permissions (BLE scan, connect, location, camera), photo capture/import, and label import/export via activity result launchers. Also manages floor mapping navigation.

- **MainViewModel** (`app/src/main/java/.../ui/MainViewModel.kt`): Core BLE scanning logic using `BluetoothLeScanner`. Manages device state with RSSI averaging (5-sample window), stale device cleanup (10s timeout), and best candidate tracking (requires 3 stable hits). Handles GATT connection for device identification via a custom characteristic write.

- **FloorMapViewModel** (`app/src/main/java/.../ui/FloorMapViewModel.kt`): Manages ARCore floor mapping sessions, pin placement, compass/level sensor data, and floor plan persistence.

- **Screen navigation**: Sealed class `Screen` with `Scan`, `Identify`, `FloorMapList`, `FloorMapCapture`, and `FloorMapPreview` states.

### Floor Mapping Module

Located in `app/src/main/java/.../`:

- **ar/ArSessionManager.kt**: ARCore session lifecycle, plane detection, and raycasting
- **ar/CoordinateTransformer.kt**: 3D-to-2D projection for fallback when no plane detected
- **floorplan/FloorPlanModels.kt**: Data classes for Vector3, Vector2, FloorPlanPin, FloorPlan
- **floorplan/FloorPlanRepository.kt**: SharedPreferences + Gson persistence
- **floorplan/PolygonCalculator.kt**: Shoelace formula for area, perimeter calculation
- **sensors/CompassManager.kt**: Accelerometer + magnetometer for heading/pitch/roll

### BLE Protocol Details

- Default filter: devices with names starting with "DAMP"
- Nordic manufacturer ID: `0x0059`
- Identify service UUID: `9f2a0001-2c3d-4e5f-8899-aabbccddeeff`
- Identify characteristic UUID: `9f2a0002-2c3d-4e5f-8899-aabbccddeeff`
- Identify payload: `0x01` (write without response)

### Data Persistence

- Device labels: SharedPreferences (`device_labels`)
- Device photos: SharedPreferences (`device_photos`) storing file paths
- Photos stored in: `filesDir/device_photos/`
- Export format: JSON with `{"labels": {"ADDRESS": "label"}}`
- Floor plans: SharedPreferences (`floor_plans`) with Gson serialization

### UI Structure

- `ScanScreen`: Permission gates, filter/sort controls, best candidate display, device list, "Map Floor" button
- `IdentifyScreen`: Device details, photo management, label editing, GATT identify trigger
- `FloorMapListScreen`: List of saved floor plans with continue/view/delete actions
- `FloorMapCaptureScreen`: AR camera view with compass/level HUD, tap to place pins
- `FloorMapPreviewScreen`: 2D polygon visualization with area/perimeter display

### Device Requirements

- ARCore-compatible Android device for floor mapping
- Minimum SDK 26, requires camera, accelerometer, gyroscope, magnetometer
