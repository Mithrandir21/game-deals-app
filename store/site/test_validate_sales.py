import json
import unittest
from pathlib import Path

from validate_sales import IOS_COPY_PATH, validate_sales, with_ios_copy


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
        with self.assertRaisesRegex(ValueError, "may have kind"):
            validate_sales(with_sale(extra=1))
        missing = {key: value for key, value in VALID["sales"][0].items() if key != "end"}
        with self.assertRaisesRegex(ValueError, "may have kind"):
            validate_sales(json.dumps(dict(VALID, sales=[missing])))

    def test_kind_is_optional_and_big_or_fest(self):
        no_kind = {key: value for key, value in VALID["sales"][0].items() if key != "kind"}
        self.assertEqual(1, len(validate_sales(json.dumps(dict(VALID, sales=[no_kind])))["sales"]))
        self.assertEqual(1, len(validate_sales(with_sale(kind="fest"))["sales"]))
        with self.assertRaisesRegex(ValueError, "kind must be"):
            validate_sales(with_sale(kind="huge"))

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

    def test_the_ios_copy_is_swapped_in_place(self):
        swift = 'enum A {\n    static let bundledJSON = #"""\n{"old": 1}\n"""#\n}\n'
        self.assertEqual(
            'enum A {\n    static let bundledJSON = #"""\n{"new": 2}\n"""#\n}\n',
            with_ios_copy(swift, '{"new": 2}\n'),
        )
        with self.assertRaisesRegex(ValueError, "bundledJSON"):
            with_ios_copy("enum A {}", "{}")

    def test_the_ios_copy_matches_the_file(self):
        swift = IOS_COPY_PATH.read_text()
        self.assertEqual(swift, with_ios_copy(swift, Path(__file__).with_name("sales.json").read_text()))


if __name__ == "__main__":
    unittest.main()
