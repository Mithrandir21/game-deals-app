"""Checks sales.json, the sale dates the countdown widgets read (typed by hand; a wrong date is public), and refreshes
the copy built into the iOS app (SaleCalendar.swift). Android builds its copy from this file on its own.

Run before every deploy: python3 store/site/validate_sales.py
"""
import json
import re
from datetime import timedelta
from pathlib import Path

from validate_event import unique_keys, utc_instant

SALES_PATH = Path(__file__).with_name("sales.json")
IOS_COPY_PATH = Path(__file__).resolve().parents[2] / "iosApp" / "iosApp" / "SaleCalendar.swift"
IOS_COPY = re.compile(r'(static let bundledJSON = #"""\n).*?(\n"""#)', re.DOTALL)
STORES = {"Steam", "Epic Games Store", "GOG"}
# "big" is a store-wide seasonal sale; "fest" a smaller themed one, which gives way to a big sale a week before it.
KINDS = {"big", "fest"}
REQUIRED_KEYS = {"id", "store", "name", "start", "end"}
OPTIONAL_KEYS = {"kind"}
SALE_ID = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
MAX_SALES = 40
MAX_NAME = 60
MAX_LENGTH = timedelta(days=45)


def validate_sales(text):
    document = json.loads(text, object_pairs_hook=unique_keys)
    if not isinstance(document, dict):
        raise ValueError("Sales document must be an object")
    if type(document.get("schemaVersion")) is not int or document["schemaVersion"] != 1:
        raise ValueError("Unsupported schema version")
    sales = document.get("sales")
    if not isinstance(sales, list) or len(sales) > MAX_SALES:
        raise ValueError(f"sales must be a list of at most {MAX_SALES}")
    seen = set()
    for sale in sales:
        if not isinstance(sale, dict) or not REQUIRED_KEYS <= set(sale) <= REQUIRED_KEYS | OPTIONAL_KEYS:
            raise ValueError("Each sale needs id, store, name, start and end, and may have kind")
        sale_id = sale["id"]
        if not isinstance(sale_id, str) or not SALE_ID.match(sale_id):
            raise ValueError(f"Bad sale id: {sale_id!r}")
        if sale_id in seen:
            raise ValueError(f"Duplicate sale id: {sale_id}")
        seen.add(sale_id)
        if sale["store"] not in STORES:
            raise ValueError(f"{sale_id}: store must be one of {sorted(STORES)}")
        if sale.get("kind", "big") not in KINDS:
            raise ValueError(f"{sale_id}: kind must be one of {sorted(KINDS)}")
        name = sale["name"]
        if not isinstance(name, str) or not name.strip() or len(name) > MAX_NAME:
            raise ValueError(f"{sale_id}: name must be 1 to {MAX_NAME} characters")
        start = utc_instant(sale["start"], f"{sale_id} start")
        end = utc_instant(sale["end"], f"{sale_id} end")
        if not start < end:
            raise ValueError(f"{sale_id}: start must be before end")
        if end - start > MAX_LENGTH:
            raise ValueError(f"{sale_id}: longer than {MAX_LENGTH.days} days; check the dates")
    return document


def with_ios_copy(swift, sales_text):
    """swift with its built-in dates replaced by sales_text."""
    if not IOS_COPY.search(swift):
        raise ValueError("No bundledJSON block in SaleCalendar.swift")
    return IOS_COPY.sub(lambda match: match.group(1) + sales_text.rstrip("\n") + match.group(2), swift, count=1)


if __name__ == "__main__":
    import sys

    path = Path(sys.argv[1]) if len(sys.argv) > 1 else SALES_PATH
    text = path.read_text()
    validated = validate_sales(text)
    print(f"Valid sales document: {len(validated['sales'])} sales")
    if path == SALES_PATH:
        swift = IOS_COPY_PATH.read_text()
        updated = with_ios_copy(swift, text)
        if updated != swift:
            IOS_COPY_PATH.write_text(updated)
            print("Updated the iOS app's built-in copy")
