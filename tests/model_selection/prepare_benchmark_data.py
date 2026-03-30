#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试数据生成脚本

从 tests/datasets/ 目录下的 JSON 文件加载数据，执行基本的完整性校验，
并输出统计信息，方便在运行正式评估之前确认数据质量。

用法:
    python tests/model_selection/prepare_benchmark_data.py
    python tests/model_selection/prepare_benchmark_data.py --validate-only
"""

import json
import argparse
from pathlib import Path
from typing import List, Dict, Any


# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DATASETS_DIR = REPO_ROOT / "tests" / "datasets"
EMBEDDING_DATA = DATASETS_DIR / "embedding_test_data.json"
RERANK_DATA = DATASETS_DIR / "rerank_test_data.json"


# ---------------------------------------------------------------------------
# Validation helpers
# ---------------------------------------------------------------------------

def _check_field(obj: Dict[str, Any], field: str, context: str) -> List[str]:
    if field not in obj:
        return [f"[{context}] 缺少必填字段: '{field}'"]
    return []


def validate_embedding_data(data: Dict[str, Any]) -> List[str]:
    errors: List[str] = []

    if "queries" not in data:
        errors.append("缺少顶层字段: 'queries'")
        return errors
    if "documents" not in data:
        errors.append("缺少顶层字段: 'documents'")
        return errors

    doc_ids = {d["id"] for d in data["documents"] if "id" in d}

    for q in data["queries"]:
        ctx = f"query:{q.get('id', '?')}"
        for field in ("id", "query", "scenario", "relevant_doc_ids", "relevance_scores"):
            errors.extend(_check_field(q, field, ctx))

        # Every relevant doc ID should exist in the documents list
        for doc_id in q.get("relevant_doc_ids", []):
            if doc_id not in doc_ids:
                errors.append(
                    f"[{ctx}] relevant_doc_ids 中的文档 '{doc_id}' 不在 documents 列表中"
                )

    for d in data["documents"]:
        ctx = f"doc:{d.get('id', '?')}"
        for field in ("id", "title", "content"):
            errors.extend(_check_field(d, field, ctx))

    return errors


def validate_rerank_data(data: Dict[str, Any]) -> List[str]:
    errors: List[str] = []

    if "queries" not in data:
        errors.append("缺少顶层字段: 'queries'")
        return errors

    for q in data["queries"]:
        ctx = f"query:{q.get('id', '?')}"
        for field in ("id", "query", "candidates", "ideal_ranking"):
            errors.extend(_check_field(q, field, ctx))

        candidate_ids = {c["doc_id"] for c in q.get("candidates", []) if "doc_id" in c}
        for doc_id in q.get("ideal_ranking", []):
            if doc_id not in candidate_ids:
                errors.append(
                    f"[{ctx}] ideal_ranking 中的文档 '{doc_id}' 不在 candidates 中"
                )

        for c in q.get("candidates", []):
            cctx = f"query:{q.get('id', '?')}/candidate:{c.get('doc_id', '?')}"
            for field in ("doc_id", "content", "relevance"):
                errors.extend(_check_field(c, field, cctx))

    return errors


# ---------------------------------------------------------------------------
# Statistics
# ---------------------------------------------------------------------------

def print_embedding_stats(data: Dict[str, Any]) -> None:
    queries = data.get("queries", [])
    documents = data.get("documents", [])

    print("\n=== Embedding 数据集统计 ===")
    print(f"  查询总数: {len(queries)}")
    print(f"  文档总数: {len(documents)}")

    if queries:
        avg_rel = sum(len(q.get("relevant_doc_ids", [])) for q in queries) / len(queries)
        print(f"  平均相关文档数/查询: {avg_rel:.1f}")

    scenario_counts: Dict[str, int] = {}
    for q in queries:
        s = q.get("scenario", "未知")
        scenario_counts[s] = scenario_counts.get(s, 0) + 1
    print("  场景分布:")
    for scenario, count in scenario_counts.items():
        print(f"    - {scenario}: {count}")


def print_rerank_stats(data: Dict[str, Any]) -> None:
    queries = data.get("queries", [])
    print("\n=== Rerank 数据集统计 ===")
    print(f"  查询总数: {len(queries)}")

    total_candidates = sum(len(q.get("candidates", [])) for q in queries)
    if queries:
        print(f"  平均候选文档数/查询: {total_candidates / len(queries):.1f}")

    relevance_dist: Dict[int, int] = {}
    for q in queries:
        for c in q.get("candidates", []):
            r = c.get("relevance", -1)
            relevance_dist[r] = relevance_dist.get(r, 0) + 1
    print("  相关性分布:")
    for score in sorted(relevance_dist.keys(), reverse=True):
        label = {3: "高度相关", 2: "相关", 1: "低相关", 0: "不相关"}.get(score, str(score))
        print(f"    - {label} (={score}): {relevance_dist[score]}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description="准备并验证基准测试数据集")
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="仅执行数据校验，不打印统计信息",
    )
    args = parser.parse_args()

    all_ok = True

    # --- Embedding data ---
    if not EMBEDDING_DATA.exists():
        print(f"[ERROR] 找不到 Embedding 数据集: {EMBEDDING_DATA}")
        all_ok = False
    else:
        with open(EMBEDDING_DATA, "r", encoding="utf-8") as f:
            emb_data = json.load(f)

        errors = validate_embedding_data(emb_data)
        if errors:
            all_ok = False
            print(f"\n[Embedding 数据校验失败] {len(errors)} 个问题:")
            for e in errors:
                print(f"  ✗ {e}")
        else:
            print(f"\n[Embedding 数据] ✓ 校验通过: {EMBEDDING_DATA}")
            if not args.validate_only:
                print_embedding_stats(emb_data)

    # --- Rerank data ---
    if not RERANK_DATA.exists():
        print(f"[ERROR] 找不到 Rerank 数据集: {RERANK_DATA}")
        all_ok = False
    else:
        with open(RERANK_DATA, "r", encoding="utf-8") as f:
            rr_data = json.load(f)

        errors = validate_rerank_data(rr_data)
        if errors:
            all_ok = False
            print(f"\n[Rerank 数据校验失败] {len(errors)} 个问题:")
            for e in errors:
                print(f"  ✗ {e}")
        else:
            print(f"\n[Rerank 数据] ✓ 校验通过: {RERANK_DATA}")
            if not args.validate_only:
                print_rerank_stats(rr_data)

    print()
    if all_ok:
        print("✅ 所有数据集校验通过，可以开始运行基准测试。")
        print()
        print("运行基准测试命令:")
        print("  python tests/model_selection/embedding_rerank_benchmark.py")
    else:
        print("❌ 存在数据质量问题，请修复后再运行基准测试。")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
