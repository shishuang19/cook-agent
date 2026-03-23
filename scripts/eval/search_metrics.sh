#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8123}"
QUERY_FILE="${2:-scripts/eval/phase3_query_set_v1.tsv}"

if [[ ! -f "$QUERY_FILE" ]]; then
  echo "Query file not found: $QUERY_FILE"
  exit 1
fi

total_queries=0
hit_queries=0
strict_queries=0
strict_hit=0

echo "Running Phase 3 search evaluation..."
echo "Base URL: $BASE_URL"
echo "Query file: $QUERY_FILE"
echo

while IFS=$'\t' read -r query min_total note; do
  if [[ "$query" == "query" ]]; then
    continue
  fi

  total_queries=$((total_queries + 1))
  if [[ "$min_total" -gt 0 ]]; then
    strict_queries=$((strict_queries + 1))
  fi

  payload=$(cat <<JSON
{"query":"$query","pageNo":1,"pageSize":5,"sortBy":"relevance"}
JSON
)

  resp=$(curl -sS -X POST "$BASE_URL/api/search" \
    -H 'Content-Type: application/json' \
    -d "$payload")

  total=$(echo "$resp" | python3 -c 'import sys, json
try:
    payload = json.load(sys.stdin)
    print(int(payload.get("data", {}).get("total", 0)))
except Exception:
    print(0)
')

  pass="FAIL"
  if [[ "$total" -ge "$min_total" ]]; then
    pass="PASS"
    hit_queries=$((hit_queries + 1))
    if [[ "$min_total" -gt 0 ]]; then
      strict_hit=$((strict_hit + 1))
    fi
  fi

  printf '[%s] query="%s" total=%s min_total=%s note=%s\n' "$pass" "$query" "$total" "$min_total" "$note"
done < "$QUERY_FILE"

overall_rate="0.00"
strict_rate="0.00"
if [[ "$total_queries" -gt 0 ]]; then
  overall_rate=$(awk "BEGIN {printf \"%.2f\", ($hit_queries/$total_queries)*100}")
fi
if [[ "$strict_queries" -gt 0 ]]; then
  strict_rate=$(awk "BEGIN {printf \"%.2f\", ($strict_hit/$strict_queries)*100}")
fi

echo
echo "===== Phase 3 Metrics ====="
echo "Total queries: $total_queries"
echo "Passed queries: $hit_queries"
echo "Overall pass rate: ${overall_rate}%"
echo "Strict queries(min_total>0): $strict_queries"
echo "Strict pass: $strict_hit"
echo "Strict pass rate: ${strict_rate}%"
