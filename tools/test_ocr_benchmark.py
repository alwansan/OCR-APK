import unittest

from ocr_benchmark import evaluate, levenshtein, matched_boxes, overlap


class OcrBenchmarkTest(unittest.TestCase):
    def test_edit_distance_preserves_code_case_and_punctuation(self):
        self.assertEqual(0, levenshtein("android.app.ActivityThread", "android.app.ActivityThread"))
        self.assertEqual(1, levenshtein("ActivityThread", "activityThread"))
        self.assertEqual(4, levenshtein("android", "roid"))

    def test_arabic_nfc_matches_and_line_recall(self):
        data = {"cases": [{
            "id": "arabic-1",
            "truth": "\u0625\u0631\u0633\u0627\u0644 \u062a\u0639\u0644\u064a\u0642\nhello",
            "gt_boxes": [[0.1, 0.1, 0.5, 0.2]],
            "outputs": {
                "ocr": {
                    "text": "\u0625\u0631\u0633\u0627\u0644 \u062a\u0639\u0644\u064a\u0642\nhello",
                    "boxes": [[0.1, 0.1, 0.5, 0.2]],
                    "duration_ms": 20
                }
            }
        }]}
        result = evaluate(data)["summary"]["ocr"]
        self.assertEqual(0.0, result["cer"])
        self.assertEqual(0.0, result["wer"])
        self.assertEqual(1.0, result["exact_line_recall"])
        self.assertEqual(1.0, result["box_recall_iou_0_5"])
        self.assertEqual(20, result["median_duration_ms"])

    def test_incomplete_detected_word_increases_error(self):
        data = {"cases": [{
            "id": "java",
            "truth": "android.app.ActivityThread",
            "outputs": {"mlkit": {"text": "d.app.ActivityThread"}}
        }]}
        r = evaluate(data)["summary"]["mlkit"]
        self.assertGreater(r["cer"], 0)
        self.assertEqual(1, r["cases_missing_boxes"])
        self.assertIsNone(r["box_recall_iou_0_5"])

    def test_box_matching_does_not_reuse_a_single_region(self):
        ground = [[0, 0, .4, .4], [0, 0, .4, .4]]
        pred = [[0, 0, .4, .4]]
        self.assertEqual(1, matched_boxes(ground, pred))
        self.assertEqual(1, overlap(ground[0], pred[0]))

    def test_missing_and_bad_data(self):
        with self.assertRaises(ValueError):
            evaluate({"cases": []})
        with self.assertRaises(ValueError):
            evaluate({"cases": [{"id": "x", "truth": "a", "outputs": {"x": {"text": "a"}}},
                                {"id": "x", "truth": "b", "outputs": {"x": {"text": "b"}}}]})
        with self.assertRaises(ValueError):
            overlap([0.8, 0, 0.1, 1], [0, 0, 1, 1])


if __name__ == "__main__":
    unittest.main()
