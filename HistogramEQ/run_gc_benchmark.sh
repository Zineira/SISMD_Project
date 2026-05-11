#!/usr/bin/env bash
# Runs the benchmark with three different GC algorithms and saves results
# Usage: ./run_gc_benchmark.sh [image_path]
set -e

IMAGE="${1:-../StarterCode/src.jpg}"
OUT_DIR="gc_results"
mkdir -p "$OUT_DIR"

SUMMARY_FILE="$OUT_DIR/summary.txt"
echo "GC Benchmark Summary" > "$SUMMARY_FILE"
echo "Date: $(date)"        >> "$SUMMARY_FILE"
echo ""                     >> "$SUMMARY_FILE"

run_gc() {
    local gc_name="$1"
    local flag="$2"
    local log_file="$OUT_DIR/gc_${gc_name}.log"
    local out_file="$OUT_DIR/out_${gc_name}.txt"

    echo "========================================"
    echo " Running with $gc_name ($flag)"
    echo "========================================"

    java $flag \
         -Xms256m -Xmx512m \
         "-Xlog:gc*:file=${log_file}:time,uptime,level,tags:filecount=1" \
         -cp out Main "$IMAGE" \
         2>/dev/null | tee "$out_file"

    echo ""
    echo "--- GC pauses ($gc_name) ---"
    if [[ -f "$log_file" ]]; then
        grep -E "Pause" "$log_file" | tail -10 || echo "  (no pause events)"
        pause_count=$(grep -c "Pause" "$log_file" 2>/dev/null || echo 0)
        total_gc_ms=$(grep -oE "[0-9]+\.[0-9]+ms" "$log_file" \
                      | awk -F'ms' '{s+=$1} END{printf "%.1f", s}' 2>/dev/null || echo "n/a")
        echo "  Total pauses : $pause_count"
        echo "  Total GC time: ${total_gc_ms} ms"
    fi
    echo ""

    seq_line=$(grep "Sequential" "$out_file" | head -1)
    echo "$gc_name | $seq_line" >> "$SUMMARY_FILE"
}

run_gc "G1GC"      "-XX:+UseG1GC"
run_gc "ParallelGC" "-XX:+UseParallelGC"
run_gc "ZGC"       "-XX:+UseZGC"

echo "========================================"
echo " GC Comparison (Sequential baseline)"
echo "========================================"
cat "$SUMMARY_FILE"
echo ""
echo "Full results saved in $OUT_DIR/"
