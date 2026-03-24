import math
import re
import sys
from pathlib import Path


def percentile(sorted_vals: list[int], p: float) -> int:
    if not sorted_vals:
        return -1
    n = len(sorted_vals)
    idx = max(0, min(n - 1, math.ceil(p * n) - 1))
    return sorted_vals[idx]


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: measure_fanout_latency.py <log_file>")
        return 1

    log_file = Path(sys.argv[1])
    if not log_file.exists():
        print(f"log file not found: {log_file}")
        return 1

    text = log_file.read_text(encoding="utf-8", errors="ignore")
    vals = [int(m.group(1)) for m in re.finditer(r"delayMs=(\d+)", text)]
    vals.sort()

    if not vals:
        print("count=0 min=-1 p50=-1 p95=-1 p99=-1 max=-1")
        return 0

    print(
        f"count={len(vals)} min={vals[0]} "
        f"p50={percentile(vals, 0.50)} "
        f"p95={percentile(vals, 0.95)} "
        f"p99={percentile(vals, 0.99)} "
        f"max={vals[-1]}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
