import json
import unittest
from pathlib import Path

from validate_event import validate_document


VALID = json.loads(Path(__file__).with_name("winter-sale-2026.json").read_text())


class EventDocumentValidationTest(unittest.TestCase):
    def test_checked_in_document_is_valid(self):
        self.assertEqual("steam-winter-2026", validate_document(json.dumps(VALID))["eventId"])

    def test_checked_in_test_document_is_valid_and_off(self):
        text = Path(__file__).with_name("winter-sale-2026-test.json").read_text()
        document = validate_document(text, "steam-winter-2026-test")
        self.assertFalse(document["enabled"])

    def test_a_test_document_is_not_a_real_one(self):
        text = Path(__file__).with_name("winter-sale-2026-test.json").read_text()
        with self.assertRaisesRegex(ValueError, "Unexpected event ID"):
            validate_document(text)

    def test_duplicate_keys_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Duplicate key"):
            validate_document('{"enabled":true,"enabled":false}')

    def test_invalid_time_order_is_rejected(self):
        invalid = dict(VALID, saleStart=VALID["visibleFrom"])
        with self.assertRaisesRegex(ValueError, "visibleFrom < saleStart < end"):
            validate_document(json.dumps(invalid))

    def test_unknown_fields_are_allowed(self):
        older = dict(VALID, handPicks=["ignored"])
        self.assertIn("handPicks", validate_document(json.dumps(older)))


if __name__ == "__main__":
    unittest.main()
