# SmartCane Android Frontend

This is the Android/Jetpack Compose frontend for the SmartCane backend in `../../backend`.

The app uses these backend-compatible endpoints:

- `GET /status`
- `GET /devices`
- `GET /events/latest`
- `POST /sos`

The FastAPI backend also keeps the ESP32 endpoints under `/api/...`, so the phone app and ESP32 firmware can use the same SQLite data.

## Backend Address

Edit:

```text
app/src/main/java/com/nankai/smartcane/data/network/SmartCaneApiClient.kt
```

For a real Android phone on the same Wi-Fi as this computer:

```kotlin
const val BASE_URL = "http://10.136.53.207:8000"
```

For the Android Emulator:

```kotlin
const val BASE_URL = "http://10.0.2.2:8000"
```

If the computer changes Wi-Fi, run `ipconfig` and replace the IPv4 address.

## Run Backend

```powershell
cd D:\smartcane\backend
py -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Open this on the computer:

```text
http://127.0.0.1:8000/status
```

Open this on the phone browser, replacing the IP if needed:

```text
http://10.136.53.207:8000/status
```

If the phone cannot open it, check that phone and computer are on the same network and Windows Firewall allows Python/Uvicorn on port `8000`.

## Open Android Project

Open this folder in Android Studio:

```text
D:\smartcane\frontend\SmartCane
```

The copied project intentionally excludes local cache folders:

- `.gradle`
- `.idea`
- `.kotlin`
- `build`
- `local.properties`

Android Studio will regenerate them.

## Notes

`android:usesCleartextTraffic="true"` is enabled because the local backend uses plain HTTP for the demo.
