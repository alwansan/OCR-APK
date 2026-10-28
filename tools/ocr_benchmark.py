#!/usr/bin/env python3
"""Offline OCR evaluation: exact CER/WER, line recall and selectable-box recall.

Usage: python3 tools/ocr_benchmark.py path/to/eval.json [--output report.json]
Schema:
{
  "cases": [{
    "id": "crash-001",
    "truth": "android.app.ActivityThread...",
    "gt_boxes": [[0.05, 0.10, 0.92, 0.13]],
    "outputs": {
      "mlkit": {"text": "android.app.ActivityThread...", "duration_ms": 250,
                "boxes": [[0.05, 0.10, 0.92, 0.13]]}
    }
  }]
}
Coordinates normalized to source image (0..1). Missing metrics remain null;
data from each engine must originate from the same actual source image.
"""
from __future__ import annotations

import argparse
import json
import math
import statistics
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path


def normalized(text: str) -> str:
    """NFC only: never erase Arabic diacritics, whitespace, Java punctuation/case."""
    return unicodedata.normalize("NFC", text)


def levenshtein(a: list | str, b: list | str) -> int:
    """Exact, memory-limited edit distance: O(min(len(a), len(b))) memory."""
    if len(a) < len(b):
        a, b = b, a
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(current[-1] + 1, previous[j] + 1,
                               previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def overlap(box_a: list[float], box_b: list[float]) -> float:
    if len(box_a) != 4 or len(box_b) != 4:
        raise ValueError("Boxes must be [x1,y1,x2,y2]")
    for b in (box_a, box_b):
        if not all(isinstance(v, (float, int)) and math.isfinite(v) for v in b):
            raise ValueError("Box coordinates must be finite numeric values")
        x1, y1, x2, y2 = b
        if not (0 <= x1 < x2 <= 1 and 0 <= y1 < y2 <= 1):
            raise ValueError("Boxes must be source-image normalized")
    l = max(box_a[0], box_b[0])
    t = max(box_a[1], box_b[1])
    r = min(box_a[2], box_b[2])
    bot = min(box_a[3], box_b[3])
    intersection = max(0, r - l) * max(0, bot - t)
    area_a = (box_a[2] - box_a[0]) * (box_a[3] - box_a[1])
    area_b = (box_b[2] - box_b[0]) * (box_b[3] - box_b[1])
    return intersection / (area_a + area_b - intersection) if intersection else 0.0


def matched_boxes(ground: list, predicted: list, minimum_iou: float = 0.5) -> int:
    """One-to-one greedy matching, avoids reusing one huge box for many words."""
    candidates = sorted(
        ((overlap(g, p), i, j)
         for i, g in enumerate(ground) for j, p in enumerate(predicted)),
        reverse=True
    )
    used_g: set[int] = set()
    used_p: set[int] = set()
    for iou, i, j in candidates:
        if iou < minimum_iou:
            break
        if i not in used_g and j not in used_p:
            used_g.add(i)
            used_p.add(j)
    return len(used_g)


def evaluate(data: dict) -> dict:
    cases = data.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("Supply at least one case in cases[]")
    per_engine = defaultdict(lambda: {
        "cases": 0, "char_errors": 0, "total_chars": 0,
        "word_errors": 0, "total_words": 0,
        "matched_lines": 0, "total_lines": 0,
        "matched_boxes": 0, "total_boxes": 0,
        "durations_ms": [], "cases_missing_boxes": 0
    })
    seen_ids: set[str] = set()
    case_reports = []
    for case in cases:
        key = case.get("id")
        if not isinstance(key, str) or not key or key in seen_ids:
            raise ValueError("Each case needs a unique nonempty id")
        seen_ids.add(key)
        truth = normalized(case["truth"])
        gt_lines = [s.strip() for s in truth.splitlines() if s.strip()]
        gt_words = truth.split()
        outputs = case.get("outputs", {})
        if not isinstance(outputs, dict) or not outputs:
            raise ValueError(f"{key}: outputs must contain engine results")
        report = {"id": key, "engines": {}}
        for engine, result in outputs.items():
            predicted = normalized(result["text"])
            hyp_words = predicted.split()
            hyp_lines = [s.strip() for s in predicted.splitlines() if s.strip()]
            c_err = levenshtein(truth, predicted)
            w_err = levenshtein(gt_words, hyp_words)
            lines_present = sum((Counter(gt_lines) & Counter(hyp_lines)).values())
            stats = per_engine[engine]
            stats["cases"] += 1
            stats["char_errors"] += c_err
            stats["total_chars"] += len(truth)
            stats["word_errors"] += w_err
            stats["total_words"] += len(gt_words)
            stats["matched_lines"] += lines_present
            stats["total_lines"] += len(gt_lines)
            duration = result.get("duration_ms")
            if duration is not None:
                if not isinstance(duration, (int, float)) or not math.isfinite(duration) or duration < 0:
                    raise ValueError("duration_ms must be finite and nonnegative")
                stats["durations_ms"].append(duration)
            gt_boxes = case.get("gt_boxes")
            pred_boxes = result.get("boxes")
            box_recall = None
            if gt_boxes is not None and pred_boxes is not None:
                box_match = matched_boxes(gt_boxes, pred_boxes)
                stats["matched_boxes"] += box_match
                stats["total_boxes"] += len(gt_boxes)
                box_recall = box_match / len(gt_boxes) if gt_boxes else None
            else:
                stats["cases_missing_boxes"] += 1
            report["engines"][engine] = {
                "cer": c_err / max(1, len(truth)),
                "wer": w_err / max(1, len(gt_words)),
                "exact_line_recall": lines_present / len(gt_lines) if gt_lines else None,
                "box_recall_iou_0_5": box_recall,
                "duration_ms": duration
            }
        case_reports.append(report)
    summary = {}
    for engine, stats in sorted(per_engine.items()):
        durations = sorted(stats["durations_ms"])
        summary[engine] = {
            "cases": stats["cases"],
            "cer": stats["char_errors"] / max(1, stats["total_chars"]),
            "wer": stats["word_errors"] / max(1, stats["total_words"]),
            "exact_line_recall": stats["matched_lines"] / stats["total_lines"] if stats["total_lines"] else None,
            "box_recall_iou_0_5": stats["matched_boxes"] / stats["total_boxes"] if stats["total_boxes"] else None,
            "cases_missing_boxes": stats["cases_missing_boxes"],
            "median_duration_ms": statistics.median(durations) if durations else None,
            "p95_duration_ms": durations[math.ceil(0.95 * len(durations)) - 1] if durations else None
        }
    return {"summary": summary, "cases": case_reports}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path, help="Local JSON evaluation dataset")
    parser.add_argument("--output", type=Path, help="Save report JSON (otherwise stdout)")
    args = parser.parse_args()
    report = evaluate(json.loads(args.dataset.read_text(encoding="utf-8")))
    output = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(output, encoding="utf-8")
    else:
        print(output, end="")


if __name__ == "__main__":
    main()
