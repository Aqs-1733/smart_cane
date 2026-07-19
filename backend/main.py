from __future__ import annotations

import json
import math
import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional

import httpx
from deep_model import score_deep_risk
from fastapi import FastAPI, File, Form, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field


BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "smartcane.db"
LEVEL_RANK = {"low": 0, "medium": 1, "high": 2}
DEVICE_OFFLINE_SECONDS = 60


try:
    from dotenv import load_dotenv

    load_dotenv(BASE_DIR / ".env")
    load_dotenv(BASE_DIR.parent / ".env", override=False)
except ImportError:
    pass


class EventCreate(BaseModel):
    device_id: str = Field(..., min_length=1)
    lat: float
    lng: float
    risk_type: str = Field(..., min_length=1)
    risk_level: Optional[str] = Field(None, pattern="^(low|medium|high)$")
    level: Optional[str] = Field(None, pattern="^(low|medium|high)$")
    direction: Optional[str] = None
    sensor: Optional[str] = None
    distance_mm: Optional[int] = None
    battery: Optional[float] = None
    battery_percent: Optional[float] = None
    front_cm: Optional[int] = None
    left_cm: Optional[int] = None
    right_cm: Optional[int] = None
    down_cm: Optional[int] = None
    extra_json: Optional[Any] = None
    timestamp: Optional[str] = None


class LocationCreate(BaseModel):
    device_id: str = Field(..., min_length=1)
    lat: float
    lng: float
    source: str = "gps"
    provider: Optional[str] = None
    quality: Optional[str] = None
    accuracy_m: Optional[float] = None
    hdop: Optional[float] = None
    fix_quality: Optional[int] = None
    satellite_count: Optional[int] = None
    timestamp: Optional[str] = None


class AdviceRequest(BaseModel):
    device_id: str = Field(..., min_length=1)
    lat: float
    lng: float
    risk_type: str = "none"
    risk_level: str = Field("low", pattern="^(low|medium|high)$")
    front_cm: Optional[int] = None
    left_cm: Optional[int] = None
    right_cm: Optional[int] = None
    down_cm: Optional[int] = None
    accuracy_m: Optional[float] = None
    location_quality: Optional[str] = None
    extra: Optional[str] = None
    nearby_radius_m: float = Field(80.0, gt=0, le=5000)


class DeepRiskRequest(BaseModel):
    device_id: str = Field(..., min_length=1)
    lat: float
    lng: float
    risk_type: str = "none"
    risk_level: str = Field("low", pattern="^(low|medium|high)$")
    front_cm: Optional[int] = None
    left_cm: Optional[int] = None
    right_cm: Optional[int] = None
    down_cm: Optional[int] = None
    accuracy_m: Optional[float] = None
    location_quality: Optional[str] = None
    nearby_radius_m: float = Field(80.0, gt=0, le=5000)


class TextCommandRequest(BaseModel):
    device_id: str = Field(..., min_length=1)
    text: str = Field(..., min_length=1)
    lat: Optional[float] = None
    lng: Optional[float] = None


class LegacySosCreate(BaseModel):
    deviceId: str = Field(..., min_length=1)
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    message: str = "\u7528\u6237\u901a\u8fc7 Android App \u53d1\u8d77\u7d27\u6025\u6c42\u52a9"


class LegacyTelemetryCreate(BaseModel):
    deviceId: str = Field(..., min_length=1)
    battery: Optional[int] = Field(None, ge=0, le=100)
    frontDistanceMm: Optional[int] = Field(None, ge=0)
    leftDistanceMm: Optional[int] = Field(None, ge=0)
    rightDistanceMm: Optional[int] = Field(None, ge=0)
    downDistanceMm: Optional[int] = Field(None, ge=0)
    latitude: Optional[float] = Field(None, ge=-90, le=90)
    longitude: Optional[float] = Field(None, ge=-180, le=180)
    timestamp: Optional[str] = None


app = FastAPI(title="Smart Cane Collaborative Risk Backend", version="2.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


def env(name: str, default: str = "") -> str:
    return os.getenv(name, default).strip()


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with db() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS risk_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                risk_type TEXT NOT NULL,
                risk_level TEXT NOT NULL,
                direction TEXT,
                sensor TEXT,
                distance_mm INTEGER,
                battery REAL,
                front_cm INTEGER,
                left_cm INTEGER,
                right_cm INTEGER,
                down_cm INTEGER,
                extra_json TEXT
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS device_locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                source TEXT NOT NULL,
                provider TEXT,
                quality TEXT,
                accuracy_m REAL,
                hdop REAL,
                fix_quality INTEGER,
                satellite_count INTEGER
            )
            """
        )
        conn.execute("CREATE INDEX IF NOT EXISTS idx_risk_events_lat_lng ON risk_events(lat, lng)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_risk_events_level ON risk_events(risk_level)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_device_locations_device ON device_locations(device_id)")
        ensure_column(conn, "risk_events", "direction", "TEXT")
        ensure_column(conn, "risk_events", "sensor", "TEXT")
        ensure_column(conn, "risk_events", "distance_mm", "INTEGER")
        ensure_column(conn, "risk_events", "battery", "REAL")
        ensure_column(conn, "device_locations", "provider", "TEXT")
        ensure_column(conn, "device_locations", "quality", "TEXT")
        ensure_column(conn, "device_locations", "hdop", "REAL")
        ensure_column(conn, "device_locations", "fix_quality", "INTEGER")


def ensure_column(conn: sqlite3.Connection, table: str, column: str, column_type: str) -> None:
    columns = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})").fetchall()}
    if column not in columns:
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {column_type}")


def row_to_dict(row: sqlite3.Row) -> dict[str, Any]:
    return {key: row[key] for key in row.keys()}


def event_to_dict(row: sqlite3.Row) -> dict[str, Any]:
    item = row_to_dict(row)
    item["level"] = item.get("risk_level")
    return item


def normalize_extra(value: Any) -> Optional[str]:
    if value is None:
        return None
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False)


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    radius_m = 6371000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lam = math.radians(lng2 - lng1)
    a = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lam / 2) ** 2
    return 2 * radius_m * math.asin(math.sqrt(a))


def nearby_summary(lat: float, lng: float, radius: float) -> dict[str, Any]:
    with db() as conn:
        rows = conn.execute("SELECT * FROM risk_events WHERE lat IS NOT NULL AND lng IS NOT NULL").fetchall()

    nearby: list[dict[str, Any]] = []
    for row in rows:
        item = event_to_dict(row)
        distance_m = haversine_m(lat, lng, float(item["lat"]), float(item["lng"]))
        if distance_m <= radius:
            item["distance_m"] = round(distance_m, 2)
            nearby.append(item)

    nearby.sort(key=lambda item: item["id"], reverse=True)
    high_count = sum(1 for item in nearby if item["risk_level"] == "high")
    medium_count = sum(1 for item in nearby if item["risk_level"] == "medium")
    max_level = "low"
    for item in nearby:
        if LEVEL_RANK[item["risk_level"]] > LEVEL_RANK[max_level]:
            max_level = item["risk_level"]

    return {
        "risk_count": len(nearby),
        "high_count": high_count,
        "medium_count": medium_count,
        "max_level": max_level,
        "recent_events": nearby[:10],
    }


def parse_time(value: str | None) -> Optional[datetime]:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed


def event_distance_mm(item: dict[str, Any]) -> Optional[int]:
    if item.get("distance_mm") is not None:
        return int(item["distance_mm"])
    risk_type = str(item.get("risk_type") or "")
    if "front" in risk_type and item.get("front_cm") is not None:
        return int(item["front_cm"]) * 10
    if "left" in risk_type and item.get("left_cm") is not None:
        return int(item["left_cm"]) * 10
    if "right" in risk_type and item.get("right_cm") is not None:
        return int(item["right_cm"]) * 10
    if ("ground" in risk_type or "drop" in risk_type) and item.get("down_cm") is not None:
        return int(item["down_cm"]) * 10
    return None


def extra_message(item: dict[str, Any]) -> Optional[str]:
    raw = item.get("extra_json")
    if not raw:
        return None
    if isinstance(raw, dict):
        message = raw.get("message")
        return str(message) if message else None
    if isinstance(raw, str):
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            return raw if raw.startswith("message=") else None
        if isinstance(parsed, dict) and parsed.get("message"):
            return str(parsed["message"])
    return None


def legacy_event_message(item: dict[str, Any]) -> str:
    custom_message = extra_message(item)
    if custom_message:
        return custom_message.removeprefix("message=")

    risk_type = str(item.get("risk_type") or "none")
    distance = event_distance_mm(item)
    cm_text = f"{int(round(distance / 10))} \u5398\u7c73" if distance is not None else "\u672a\u77e5\u8ddd\u79bb"
    if risk_type == "sos":
        return "\u6536\u5230 Android App \u7d27\u6025\u6c42\u52a9\uff0c\u8bf7\u5c3d\u5feb\u8054\u7cfb\u4f7f\u7528\u8005\u3002"
    if risk_type == "ground_drop":
        return f"\u4e0b\u89c6\u8ddd\u79bb\u7ea6 {cm_text}\uff0c\u53ef\u80fd\u6709\u53f0\u9636\u3001\u5751\u6d3c\u6216\u843d\u5dee\uff0c\u8bf7\u505c\u6b62\u524d\u8fdb\u3002"
    if risk_type == "front_obstacle":
        return f"\u524d\u65b9\u7ea6 {cm_text} \u6709\u969c\u788d\uff0c\u8bf7\u51cf\u901f\u5e76\u51c6\u5907\u7ed5\u884c\u3002"
    if risk_type == "left_obstacle":
        return f"\u5de6\u4fa7\u7ea6 {cm_text} \u6709\u969c\u788d\uff0c\u8bf7\u5411\u53f3\u4fa7\u4fdd\u6301\u8ddd\u79bb\u3002"
    if risk_type == "right_obstacle":
        return f"\u53f3\u4fa7\u7ea6 {cm_text} \u6709\u969c\u788d\uff0c\u8bf7\u5411\u5de6\u4fa7\u4fdd\u6301\u8ddd\u79bb\u3002"
    if risk_type == "user_mark":
        return "\u7528\u6237\u624b\u52a8\u6807\u8bb0\u4e86\u4e00\u4e2a\u98ce\u9669\u70b9\u3002"
    if risk_type == "history_risk":
        return "\u9644\u8fd1\u5b58\u5728\u5386\u53f2\u9ad8\u98ce\u9669\u70b9\uff0c\u8bf7\u51cf\u901f\u786e\u8ba4\u3002"
    return "\u6682\u65e0\u660e\u786e\u98ce\u9669\uff0c\u8bf7\u4fdd\u6301\u8c28\u614e\u3002"


def legacy_event_dict(row: sqlite3.Row) -> dict[str, Any]:
    item = event_to_dict(row)
    return {
        "id": item["id"],
        "deviceId": item["device_id"],
        "riskType": item["risk_type"],
        "riskLevel": item["risk_level"],
        "distance": event_distance_mm(item),
        "message": legacy_event_message(item),
        "latitude": item.get("lat"),
        "longitude": item.get("lng"),
        "timestamp": item.get("timestamp"),
    }


def latest_location_for_device(device_id: str) -> Optional[dict[str, Any]]:
    with db() as conn:
        row = conn.execute(
            "SELECT * FROM device_locations WHERE device_id = ? ORDER BY id DESC LIMIT 1",
            (device_id,),
        ).fetchone()
    return row_to_dict(row) if row else None


def resolve_legacy_location(device_id: str, lat: Optional[float], lng: Optional[float]) -> tuple[float, float]:
    if lat is not None and lng is not None:
        return float(lat), float(lng)
    latest = latest_location_for_device(device_id)
    if latest:
        return float(latest["lat"]), float(latest["lng"])
    return 0.0, 0.0


def legacy_device_list() -> list[dict[str, Any]]:
    devices: dict[str, dict[str, Any]] = {}
    with db() as conn:
        location_rows = conn.execute("SELECT * FROM device_locations ORDER BY id DESC").fetchall()
        event_rows = conn.execute("SELECT * FROM risk_events ORDER BY id DESC").fetchall()

    for row in location_rows:
        item = row_to_dict(row)
        device_id = item["device_id"]
        if device_id in devices:
            continue
        devices[device_id] = {
            "deviceId": device_id,
            "name": f"SmartCane {device_id}",
            "online": False,
            "battery": None,
            "lastSeen": item["timestamp"],
        }

    for row in event_rows:
        item = event_to_dict(row)
        device_id = item["device_id"]
        device = devices.setdefault(
            device_id,
            {
                "deviceId": device_id,
                "name": f"SmartCane {device_id}",
                "online": False,
                "battery": None,
                "lastSeen": item["timestamp"],
            },
        )
        if item.get("timestamp") and parse_time(item["timestamp"]) and (
            not parse_time(device.get("lastSeen")) or parse_time(item["timestamp"]) > parse_time(device.get("lastSeen"))
        ):
            device["lastSeen"] = item["timestamp"]
        battery = item.get("battery")
        if battery is not None and float(battery) >= 0:
            device["battery"] = int(float(battery))

    now = datetime.now(timezone.utc)
    for device in devices.values():
        last_seen = parse_time(device.get("lastSeen"))
        device["online"] = bool(last_seen and (now - last_seen).total_seconds() <= DEVICE_OFFLINE_SECONDS)

    return sorted(devices.values(), key=lambda item: item.get("lastSeen") or "", reverse=True)


def store_legacy_location(device_id: str, lat: Optional[float], lng: Optional[float], battery: Optional[int] = None) -> None:
    if lat is None or lng is None:
        return
    create_location(
        LocationCreate(
            device_id=device_id,
            lat=float(lat),
            lng=float(lng),
            source="app",
            provider="phone",
            quality="usable",
            accuracy_m=None,
            timestamp=now_iso(),
        )
    )


def chat_config() -> dict[str, str]:
    provider = env("LLM_PROVIDER", "ark").lower()
    if provider == "openai":
        return {
            "provider": "openai",
            "api_key": env("OPENAI_API_KEY"),
            "base_url": env("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/"),
            "model": env("OPENAI_MODEL", "gpt-4.1-mini"),
        }
    return {
        "provider": "ark",
        "api_key": env("ARK_API_KEY"),
        "base_url": env("ARK_OPENAI_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3").rstrip("/"),
        "model": env("ARK_MODEL", "doubao-seed-2-1-pro-260628"),
    }


def stt_config() -> dict[str, str]:
    provider = env("STT_PROVIDER", "openai").lower()
    if provider == "ark":
        return {
            "provider": "ark",
            "api_key": env("ARK_API_KEY"),
            "base_url": env("ARK_OPENAI_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3").rstrip("/"),
            "model": env("ARK_STT_MODEL", env("STT_MODEL", "")),
        }
    return {
        "provider": "openai",
        "api_key": env("OPENAI_API_KEY"),
        "base_url": env("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/"),
        "model": env("OPENAI_STT_MODEL", env("STT_MODEL", "whisper-1")),
    }


def ai_enabled() -> bool:
    cfg = chat_config()
    return bool(cfg["api_key"] and cfg["model"] and cfg["base_url"])


def fallback_advice(req: AdviceRequest, history: dict[str, Any]) -> str:
    if req.risk_type == "sos":
        return "SOS already sent. Stay where you are if safe."
    if req.risk_type == "ground_drop" or (req.down_cm is not None and req.down_cm > 75):
        return "Stop. Check the ground ahead before moving."
    if req.risk_level == "high":
        if req.left_cm is not None and req.right_cm is not None:
            if req.left_cm > req.right_cm and req.left_cm > 90:
                return "High risk ahead. Turn left slowly."
            if req.right_cm > req.left_cm and req.right_cm > 90:
                return "High risk ahead. Turn right slowly."
        return "High risk ahead. Stop and probe carefully."
    if req.risk_level == "medium":
        return "Slow down. Keep scanning left and right."
    if history["high_count"] >= 2:
        return "Nearby history has high risks. Slow down."
    return "Path looks clear. Continue carefully."


def deep_advice(req: AdviceRequest, deep: dict[str, Any]) -> str:
    level = deep.get("level", "low")
    if level == "high":
        if req.risk_type == "ground_drop" or (req.down_cm is not None and req.down_cm > 75):
            return "\u6df1\u5ea6\u6a21\u578b\u63d0\u793a\u843d\u5dee\u98ce\u9669\uff0c\u8bf7\u505c\u6b62\u63a2\u8def\u3002"
        if req.left_cm is not None and req.right_cm is not None:
            if req.left_cm > req.right_cm and req.left_cm > 90:
                return "\u6df1\u5ea6\u6a21\u578b\u63d0\u793a\u9ad8\u98ce\u9669\uff0c\u8bf7\u5411\u5de6\u6162\u884c\u3002"
            if req.right_cm > req.left_cm and req.right_cm > 90:
                return "\u6df1\u5ea6\u6a21\u578b\u63d0\u793a\u9ad8\u98ce\u9669\uff0c\u8bf7\u5411\u53f3\u6162\u884c\u3002"
        return "\u6df1\u5ea6\u6a21\u578b\u63d0\u793a\u9ad8\u98ce\u9669\uff0c\u8bf7\u505c\u6b62\u3002"
    if level == "medium":
        return "\u6df1\u5ea6\u6a21\u578b\u63d0\u793a\u4e2d\u98ce\u9669\uff0c\u8bf7\u51cf\u901f\u786e\u8ba4\u3002"
    return "\u6df1\u5ea6\u6a21\u578b\u63d0\u793a\u98ce\u9669\u8f83\u4f4e\uff0c\u8bf7\u8c28\u614e\u524d\u8fdb\u3002"


async def call_chat_completion(messages: list[dict[str, str]], temperature: float = 0.2) -> tuple[Optional[str], dict[str, Any]]:
    cfg = chat_config()
    meta = {"provider": cfg["provider"], "model": cfg["model"], "enabled": bool(cfg["api_key"])}
    if not cfg["api_key"]:
        return None, meta

    payload = {
        "model": cfg["model"],
        "messages": messages,
        "temperature": temperature,
    }
    headers = {
        "Authorization": f"Bearer {cfg['api_key']}",
        "Content-Type": "application/json",
    }
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(f"{cfg['base_url']}/chat/completions", headers=headers, json=payload)
    response.raise_for_status()
    data = response.json()
    content = data["choices"][0]["message"]["content"]
    return str(content).strip(), meta


async def generate_advice(req: AdviceRequest, history: dict[str, Any], deep: dict[str, Any]) -> dict[str, Any]:
    fallback = deep_advice(req, deep) if deep.get("level") != "low" else fallback_advice(req, history)
    messages = [
        {
            "role": "system",
            "content": (
                "You are a safety assistant for a smart cane. "
                "Return one short practical instruction in Chinese, no markdown, no diagnosis, under 40 Chinese characters. "
                "Prefer stop/slow/left/right guidance based on sensor data."
            ),
        },
        {
            "role": "user",
            "content": json.dumps(
                {
                    "device_id": req.device_id,
                    "risk_type": req.risk_type,
                    "risk_level": req.risk_level,
                    "front_cm": req.front_cm,
                    "left_cm": req.left_cm,
                    "right_cm": req.right_cm,
                    "down_cm": req.down_cm,
                    "nearby_history": {
                        "risk_count": history["risk_count"],
                        "high_count": history["high_count"],
                        "medium_count": history["medium_count"],
                        "max_level": history["max_level"],
                    },
                    "deep_learning": {
                        "model": deep["model"],
                        "score": deep["score"],
                        "level": deep["level"],
                        "confidence": deep["confidence"],
                    },
                    "extra": req.extra,
                },
                ensure_ascii=False,
            ),
        },
    ]

    try:
        content, meta = await call_chat_completion(messages)
    except Exception as exc:
        return {
            "advice": fallback,
            "fallback": True,
            "error": str(exc),
            "provider": chat_config()["provider"],
            "model": chat_config()["model"],
        }

    if not content:
        meta = meta if "meta" in locals() else {"provider": chat_config()["provider"], "model": chat_config()["model"]}
        return {**meta, "advice": fallback, "fallback": True}
    return {**meta, "advice": content, "fallback": False}


def fallback_command(text: str) -> dict[str, Any]:
    normalized = text.lower()
    if any(word in normalized for word in ["sos", "help", "\u6551\u547d", "\u6c42\u52a9"]):
        return {"intent": "sos", "action": "trigger_sos", "confidence": 0.95, "reply": "\u5df2\u8bc6\u522b\u6c42\u52a9\u6307\u4ee4"}
    if any(word in normalized for word in ["upload", "mark", "record", "\u6807\u8bb0", "\u8bb0\u5f55"]):
        return {"intent": "mark_risk", "action": "upload_user_mark", "confidence": 0.8, "reply": "\u5df2\u8bc6\u522b\u98ce\u9669\u6807\u8bb0\u6307\u4ee4"}
    if any(word in normalized for word in ["nearby", "risk", "\u9644\u8fd1", "\u98ce\u9669"]):
        return {"intent": "query_risk", "action": "query_nearby_risks", "confidence": 0.75, "reply": "\u5df2\u8bc6\u522b\u98ce\u9669\u67e5\u8be2\u6307\u4ee4"}
    if any(word in normalized for word in ["repeat", "again", "\u91cd\u590d", "\u518d\u8bf4"]):
        return {"intent": "repeat", "action": "repeat_last_prompt", "confidence": 0.7, "reply": "\u5df2\u8bc6\u522b\u91cd\u590d\u63d0\u793a\u6307\u4ee4"}
    return {"intent": "unknown", "action": "none", "confidence": 0.35, "reply": "\u672a\u8bc6\u522b\u660e\u786e\u6307\u4ee4"}


async def parse_command_with_llm(text: str, device_id: str) -> dict[str, Any]:
    fallback = fallback_command(text)
    messages = [
        {
            "role": "system",
            "content": (
                "Classify a smart cane voice command. "
                "Return compact JSON only with keys: intent, action, confidence, reply. "
                "Allowed actions: trigger_sos, upload_user_mark, query_nearby_risks, repeat_last_prompt, switch_mode, none."
            ),
        },
        {"role": "user", "content": json.dumps({"device_id": device_id, "text": text}, ensure_ascii=False)},
    ]
    try:
        content, meta = await call_chat_completion(messages, temperature=0.0)
        if not content:
            return {**fallback, "fallback": True, "provider": meta["provider"], "model": meta["model"]}
        parsed = json.loads(content)
        return {**parsed, "fallback": False, "provider": meta["provider"], "model": meta["model"]}
    except Exception as exc:
        return {**fallback, "fallback": True, "error": str(exc), "provider": chat_config()["provider"], "model": chat_config()["model"]}


@app.on_event("startup")
def on_startup() -> None:
    init_db()


@app.get("/api/health")
def health() -> dict[str, Any]:
    return {"ok": True, "time": now_iso(), "database": str(DB_PATH)}


@app.get("/api/ai/status")
def ai_status() -> dict[str, Any]:
    chat = chat_config()
    stt = stt_config()
    return {
        "llm_provider": chat["provider"],
        "llm_model": chat["model"],
        "llm_configured": bool(chat["api_key"]),
        "deep_learning_model": "tiny-mlp-risk-v1",
        "deep_learning_enabled": True,
        "stt_provider": stt["provider"],
        "stt_model": stt["model"],
        "stt_configured": bool(stt["api_key"] and stt["model"]),
    }


def store_event(event: EventCreate) -> dict[str, Any]:
    timestamp = event.timestamp or now_iso()
    risk_level = (event.risk_level or event.level or "").lower()
    if risk_level not in LEVEL_RANK:
        raise HTTPException(status_code=400, detail="risk_level or level must be low, medium, or high")
    battery = event.battery if event.battery is not None else event.battery_percent

    with db() as conn:
        cur = conn.execute(
            """
            INSERT INTO risk_events (
                device_id, timestamp, lat, lng, risk_type, risk_level,
                direction, sensor, distance_mm, battery,
                front_cm, left_cm, right_cm, down_cm, extra_json
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                event.device_id,
                timestamp,
                event.lat,
                event.lng,
                event.risk_type,
                risk_level,
                event.direction,
                event.sensor,
                event.distance_mm,
                battery,
                event.front_cm,
                event.left_cm,
                event.right_cm,
                event.down_cm,
                normalize_extra(event.extra_json),
            ),
        )
        row = conn.execute("SELECT * FROM risk_events WHERE id = ?", (cur.lastrowid,)).fetchone()

    return event_to_dict(row)


@app.post("/api/risk-events", status_code=201)
def create_risk_event(event: EventCreate) -> dict[str, Any]:
    return store_event(event)


@app.post("/api/events", status_code=201)
def create_event(event: EventCreate) -> dict[str, Any]:
    return store_event(event)


@app.get("/api/events")
def list_events(limit: int = Query(200, ge=1, le=1000)) -> list[dict[str, Any]]:
    with db() as conn:
        rows = conn.execute("SELECT * FROM risk_events ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    return [event_to_dict(row) for row in rows]


@app.get("/api/risk-events")
def list_risk_events(limit: int = Query(200, ge=1, le=1000)) -> list[dict[str, Any]]:
    return list_events(limit)


@app.post("/api/locations", status_code=201)
def create_location(location: LocationCreate) -> dict[str, Any]:
    timestamp = location.timestamp or now_iso()
    with db() as conn:
        cur = conn.execute(
            """
            INSERT INTO device_locations (
                device_id, timestamp, lat, lng, source, provider, quality,
                accuracy_m, hdop, fix_quality, satellite_count
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                location.device_id,
                timestamp,
                location.lat,
                location.lng,
                location.source,
                location.provider,
                location.quality,
                location.accuracy_m,
                location.hdop,
                location.fix_quality,
                location.satellite_count,
            ),
        )
        row = conn.execute("SELECT * FROM device_locations WHERE id = ?", (cur.lastrowid,)).fetchone()
    return row_to_dict(row)


@app.get("/status")
def legacy_status() -> dict[str, Any]:
    devices = legacy_device_list()
    return {
        "online": True,
        "message": "\u540e\u7aef\u5df2\u8fde\u63a5\uff0c\u6b63\u5728\u8bb0\u5f55\u8def\u7ebf\u548c\u98ce\u9669\u70b9",
        "deviceCount": len(devices),
    }


@app.get("/devices")
def legacy_devices() -> dict[str, Any]:
    return {"devices": legacy_device_list()}


@app.get("/events/latest")
def legacy_latest_events(limit: int = Query(50, ge=1, le=200)) -> dict[str, Any]:
    with db() as conn:
        rows = conn.execute("SELECT * FROM risk_events ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    return {"events": [legacy_event_dict(row) for row in rows]}


@app.post("/sos", status_code=201)
def legacy_sos(request: LegacySosCreate) -> dict[str, Any]:
    lat, lng = resolve_legacy_location(request.deviceId, request.latitude, request.longitude)
    store_legacy_location(request.deviceId, request.latitude, request.longitude)
    stored = store_event(
        EventCreate(
            device_id=request.deviceId,
            lat=lat,
            lng=lng,
            risk_type="sos",
            risk_level="high",
            level="high",
            direction="stop",
            sensor="android_app",
            distance_mm=None,
            battery=None,
            front_cm=None,
            left_cm=None,
            right_cm=None,
            down_cm=None,
            extra_json={"message": request.message, "source": "android_app_sos"},
            timestamp=now_iso(),
        )
    )
    return {
        "success": True,
        "message": "\u7d27\u6025\u6c42\u52a9\u5df2\u8bb0\u5f55",
        "sos": {
            "id": stored["id"],
            "deviceId": stored["device_id"],
            "latitude": stored["lat"],
            "longitude": stored["lng"],
            "message": request.message,
            "receivedAt": stored["timestamp"],
        },
    }


@app.post("/telemetry", status_code=201)
def legacy_telemetry(request: LegacyTelemetryCreate) -> dict[str, Any]:
    store_legacy_location(request.deviceId, request.latitude, request.longitude, request.battery)
    lat, lng = resolve_legacy_location(request.deviceId, request.latitude, request.longitude)

    generated: list[dict[str, Any]] = []
    candidates: list[tuple[str, str, str, Optional[int]]] = []
    if request.frontDistanceMm is not None:
        if request.frontDistanceMm < 500:
            candidates.append(("front_obstacle", "high", "tof_front", request.frontDistanceMm))
        elif request.frontDistanceMm <= 1200:
            candidates.append(("front_obstacle", "medium", "tof_front", request.frontDistanceMm))
    if request.leftDistanceMm is not None and request.leftDistanceMm < 500:
        candidates.append(("left_obstacle", "high", "tof_left", request.leftDistanceMm))
    if request.rightDistanceMm is not None and request.rightDistanceMm < 500:
        candidates.append(("right_obstacle", "high", "tof_right", request.rightDistanceMm))
    if request.downDistanceMm is not None and request.downDistanceMm > 750:
        candidates.append(("ground_drop", "high", "tof_down", request.downDistanceMm))

    for risk_type, level, sensor, distance_mm in candidates:
        front_cm = int(request.frontDistanceMm / 10) if request.frontDistanceMm is not None else None
        left_cm = int(request.leftDistanceMm / 10) if request.leftDistanceMm is not None else None
        right_cm = int(request.rightDistanceMm / 10) if request.rightDistanceMm is not None else None
        down_cm = int(request.downDistanceMm / 10) if request.downDistanceMm is not None else None
        stored = store_event(
            EventCreate(
                device_id=request.deviceId,
                lat=lat,
                lng=lng,
                risk_type=risk_type,
                risk_level=level,
                level=level,
                direction="stop" if level == "high" else "slow",
                sensor=sensor,
                distance_mm=distance_mm,
                battery=request.battery,
                front_cm=front_cm,
                left_cm=left_cm,
                right_cm=right_cm,
                down_cm=down_cm,
                extra_json={"source": "legacy_telemetry"},
                timestamp=request.timestamp or now_iso(),
            )
        )
        generated.append(stored)

    return {
        "success": True,
        "message": "\u9065\u6d4b\u5df2\u63a5\u6536",
        "generatedEvents": len(generated),
        "events": [
            {
                "id": event["id"],
                "deviceId": event["device_id"],
                "riskType": event["risk_type"],
                "riskLevel": event["risk_level"],
                "distance": event.get("distance_mm"),
                "message": legacy_event_message(event),
                "latitude": event.get("lat"),
                "longitude": event.get("lng"),
                "timestamp": event.get("timestamp"),
            }
            for event in generated
        ],
    }


@app.get("/api/locations/latest")
def latest_location(device_id: str = Query(..., min_length=1)) -> Optional[dict[str, Any]]:
    with db() as conn:
        row = conn.execute(
            "SELECT * FROM device_locations WHERE device_id = ? ORDER BY id DESC LIMIT 1",
            (device_id,),
        ).fetchone()
    return row_to_dict(row) if row else None


@app.get("/api/locations/history")
def location_history(
    device_id: str = Query(..., min_length=1),
    limit: int = Query(200, ge=1, le=1000),
) -> list[dict[str, Any]]:
    with db() as conn:
        rows = conn.execute(
            "SELECT * FROM device_locations WHERE device_id = ? ORDER BY id DESC LIMIT ?",
            (device_id, limit),
        ).fetchall()
    return [row_to_dict(row) for row in rows]


@app.get("/api/risks/nearby")
def nearby_risks(
    lat: float = Query(...),
    lng: float = Query(...),
    radius: float = Query(80.0, gt=0, le=5000),
) -> dict[str, Any]:
    return nearby_summary(lat, lng, radius)


@app.post("/api/ai/deep-risk")
def deep_risk(req: DeepRiskRequest) -> dict[str, Any]:
    history = nearby_summary(req.lat, req.lng, req.nearby_radius_m)
    deep = score_deep_risk(req, history)
    return {
        "device_id": req.device_id,
        "lat": req.lat,
        "lng": req.lng,
        "deep_learning": deep,
        "nearby": history,
    }


@app.post("/api/ai/advice")
async def ai_advice(req: AdviceRequest) -> dict[str, Any]:
    history = nearby_summary(req.lat, req.lng, req.nearby_radius_m)
    deep = score_deep_risk(req, history)
    result = await generate_advice(req, history, deep)
    return {**result, "nearby": history, "deep_learning": deep}


@app.post("/api/voice/text-command")
async def text_command(req: TextCommandRequest) -> dict[str, Any]:
    result = await parse_command_with_llm(req.text, req.device_id)
    return {"text": req.text, **result}


@app.post("/api/voice/transcribe")
async def transcribe_voice(
    file: UploadFile = File(...),
    language: Optional[str] = Form(None),
    prompt: Optional[str] = Form(None),
) -> dict[str, Any]:
    cfg = stt_config()
    if not cfg["api_key"] or not cfg["model"]:
        raise HTTPException(status_code=503, detail="speech recognition is not configured")

    content = await file.read()
    data: dict[str, str] = {"model": cfg["model"]}
    if language:
        data["language"] = language
    if prompt:
        data["prompt"] = prompt

    files = {
        "file": (
            file.filename or "audio.wav",
            content,
            file.content_type or "application/octet-stream",
        )
    }
    headers = {"Authorization": f"Bearer {cfg['api_key']}"}

    async with httpx.AsyncClient(timeout=90.0) as client:
        response = await client.post(f"{cfg['base_url']}/audio/transcriptions", headers=headers, data=data, files=files)
    if response.status_code >= 400:
        raise HTTPException(status_code=response.status_code, detail=response.text)
    payload = response.json()
    return {
        "provider": cfg["provider"],
        "model": cfg["model"],
        "text": payload.get("text", ""),
        "raw": payload,
    }


@app.post("/api/voice/command")
async def voice_command(
    device_id: str = Form(...),
    file: UploadFile = File(...),
    language: Optional[str] = Form(None),
) -> dict[str, Any]:
    transcript = await transcribe_voice(file=file, language=language, prompt=None)
    parsed = await parse_command_with_llm(transcript["text"], device_id)
    return {"device_id": device_id, "transcript": transcript["text"], **parsed}


if __name__ == "__main__":
    import uvicorn

    init_db()
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
