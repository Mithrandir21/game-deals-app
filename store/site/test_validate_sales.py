import json
import unittest
from pathlib import Path

from validate_sales import validate_sales


VALID = json.loads(Path(__file__).with_name("sales.json").read_text())


def with_sale(**changes):
    sale = dict(VALID["sales"][0], **changes)
    return json.dumps(dict(VALID, sales=[sale]))


class SalesDocumentValidationTest(unittest.TestCase):
    def test_checked_in_document_is_valid(self):
        self.assertGreater(len(validate_sales(json.dumps(VALID))["sales"]), 0)

    def test_an_empty_list_is_valid(self):
        self.assertEqual([], validate_sales('{"schemaVersion":1,"sales":[]}')["sales"])

    def test_dates_must_be_utc_and_in_order(self):
        with self.assertRaisesRegex(ValueError, "UTC"):
            validate_sales(with_sale(start="2026-12-10T16:00:00+01:00"))
        with self.assertRaisesRegex(ValueError, "before end"):
            validate_sales(with_sale(end=VALID["sales"][0]["start"]))
        with self.assertRaisesRegex(ValueError, "longer than"):
            validate_sales(with_sale(start="2026-01-01T00:00:00Z", end="2026-03-01T00:00:00Z"))

    def test_only_known_stores_and_tidy_ids(self):
        with self.assertRaisesRegex(ValueError, "store must be"):
            validate_sales(with_sale(store="Humble"))
        with self.assertRaisesRegex(ValueError, "Bad sale id"):
            validate_sales(with_sale(id="Steam Winter"))

    def test_ids_are_unique_and_fields_exact(self):
        sale = VALID["sales"][0]
        with self.assertRaisesRegex(ValueError, "Duplicate sale id"):
            validate_sales(json.dumps(dict(VALID, sales=[sale, sale])))
        with self.assertRaisesRegex(ValueError, "exactly"):
            validate_sales(with_sale(extra=1))

    def test_names_are_short_and_present(self):
        with self.assertRaisesRegex(ValueError, "name"):
            validate_sales(with_sale(name=" "))
        with self.assertRaisesRegex(ValueError, "name"):
            validate_sales(with_sale(name="x" * 61))

    def test_unknown_schema_and_duplicate_keys_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "schema"):
            validate_sales('{"schemaVersion":2,"sales":[]}')
        with self.assertRaisesRegex(ValueError, "Duplicate key"):
            validate_sales('{"schemaVersion":1,"schemaVersion":1,"sales":[]}')


if __name__ == "__main__":
    unittest.main()
