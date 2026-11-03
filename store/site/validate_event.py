import json
from datetime import datetime, timedelta
from pathlib import Path


EVENT_PATH = Path(__file__).with_name("winter-sale-2026.json")


def unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate key: {key}")
        result[key] = value
    return result


def utc_instant(value, name):
    if not isinstance(value, str) or not value.endswith("Z"):
        raise ValueError(f"{name} must be a UTC instant ending in Z")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(f"Invalid {name}") from error
    if parsed.utcoffset() != timedelta(0):
        raise ValueError(f"{name} must be UTC")
    return parsed


def validate_document(text):
    document = json.loads(text, object_pairs_hook=unique_keys)
    if not isinstance(document, dict):
        raise ValueError("Event document must be an object")
    if type(document.get("schemaVersion")) is not int or document["schemaVersion"] != 1:
        raise ValueError("Unsupported schema version")
    if document.get("eventId") != "steam-winter-2026":
        raise ValueError("Unexpected event ID")
    if type(document.get("enabled")) is not bool:
        raise ValueError("enabled must be a boolean")
    visible_from = utc_instant(document.get("visibleFrom"), "visibleFrom")
    sale_start = utc_instant(document.get("saleStart"), "saleStart")
    end = utc_instant(document.get("end"), "end")
    if not visible_from < sale_start < end:
        raise ValueError("Expected visibleFrom < saleStart < end")
    copy = document.get("copy")
    if not isinstance(copy, dict) or any(not isinstance(copy.get(key), str) or not copy[key].strip() for key in ("title", "beforeSale", "duringSale")):
        raise ValueError("Event copy is incomplete")
    return document


if __name__ == "__main__":
    validated = validate_document(EVENT_PATH.read_text())
    print(f"Valid event document: {validated['eventId']}, enabled={validated['enabled']}")
