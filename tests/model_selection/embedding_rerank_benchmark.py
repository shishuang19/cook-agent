#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
中文菜谱 Embedding & Rerank 模型基准测试框架

支持以下功能:
  - 多个 Embedding 模型的对比评估 (MRR, NDCG, Recall, Precision)
  - 多个 Rerank 模型的对比评估
  - Embedding + Rerank 组合评估
  - 延迟和内存占用监控
  - 生成对比报告
"""

import json
import os
import time
import math
import traceback
import argparse
from pathlib import Path
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass, field
from datetime import datetime

# ---------------------------------------------------------------------------
# Optional heavy dependencies — imported lazily so the file can be imported
# even when the ML libraries are not installed.
# ---------------------------------------------------------------------------
try:
    import numpy as np
    _HAS_NUMPY = True
except ImportError:
    _HAS_NUMPY = False

try:
    import psutil
    _HAS_PSUTIL = True
except ImportError:
    _HAS_PSUTIL = False


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class EmbeddingQuery:
    """Embedding 评估查询"""
    id: str
    query: str
    scenario: str
    relevant_doc_ids: List[str]
    relevance_scores: Dict[str, int]


@dataclass
class EmbeddingDocument:
    """Embedding 评估文档"""
    id: str
    title: str
    content: str
    tags: List[str]


@dataclass
class RerankQuery:
    """Rerank 评估查询"""
    id: str
    query: str
    candidates: List[Dict]
    ideal_ranking: List[str]


@dataclass
class EvaluationMetrics:
    """评估指标"""
    model_name: str
    mrr: float = 0.0
    ndcg_at_5: float = 0.0
    ndcg_at_10: float = 0.0
    recall_at_5: float = 0.0
    recall_at_10: float = 0.0
    precision_at_5: float = 0.0
    precision_at_10: float = 0.0
    avg_latency_ms: float = 0.0
    p95_latency_ms: float = 0.0
    memory_mb: float = 0.0
    query_count: int = 0
    errors: List[str] = field(default_factory=list)

    def summary(self) -> str:
        return (
            f"  MRR:          {self.mrr:.4f}\n"
            f"  NDCG@5:       {self.ndcg_at_5:.4f}\n"
            f"  NDCG@10:      {self.ndcg_at_10:.4f}\n"
            f"  Recall@5:     {self.recall_at_5:.4f}\n"
            f"  Recall@10:    {self.recall_at_10:.4f}\n"
            f"  Precision@5:  {self.precision_at_5:.4f}\n"
            f"  Precision@10: {self.precision_at_10:.4f}\n"
            f"  Avg Latency:  {self.avg_latency_ms:.1f} ms\n"
            f"  P95 Latency:  {self.p95_latency_ms:.1f} ms\n"
            f"  Memory:       {self.memory_mb:.1f} MB\n"
        )


# ---------------------------------------------------------------------------
# Metric helpers
# ---------------------------------------------------------------------------

def _dcg(relevances: List[int]) -> float:
    """Compute Discounted Cumulative Gain."""
    return sum(
        (2 ** r - 1) / math.log2(i + 2)
        for i, r in enumerate(relevances)
    )


def _ndcg(retrieved_ids: List[str], relevance_map: Dict[str, int], k: int) -> float:
    retrieved_ks = retrieved_ids[:k]
    gains = [relevance_map.get(doc_id, 0) for doc_id in retrieved_ks]
    ideal_gains = sorted(relevance_map.values(), reverse=True)[:k]
    ideal_dcg = _dcg(ideal_gains)
    if ideal_dcg == 0:
        return 0.0
    return _dcg(gains) / ideal_dcg


def _mrr(retrieved_ids: List[str], relevant_ids: set) -> float:
    for rank, doc_id in enumerate(retrieved_ids, start=1):
        if doc_id in relevant_ids:
            return 1.0 / rank
    return 0.0


def _recall_at_k(retrieved_ids: List[str], relevant_ids: set, k: int) -> float:
    if not relevant_ids:
        return 0.0
    hits = sum(1 for doc_id in retrieved_ids[:k] if doc_id in relevant_ids)
    return hits / len(relevant_ids)


def _precision_at_k(retrieved_ids: List[str], relevant_ids: set, k: int) -> float:
    if k == 0:
        return 0.0
    hits = sum(1 for doc_id in retrieved_ids[:k] if doc_id in relevant_ids)
    return hits / k


# ---------------------------------------------------------------------------
# Cosine similarity (pure Python fallback)
# ---------------------------------------------------------------------------

def _cosine_similarity(vec_a: List[float], vec_b: List[float]) -> float:
    if _HAS_NUMPY:
        import numpy as np  # noqa: F811
        a = np.array(vec_a, dtype=float)
        b = np.array(vec_b, dtype=float)
        norm_a = np.linalg.norm(a)
        norm_b = np.linalg.norm(b)
        if norm_a == 0 or norm_b == 0:
            return 0.0
        return float(np.dot(a, b) / (norm_a * norm_b))

    # Pure Python fallback
    dot = sum(x * y for x, y in zip(vec_a, vec_b))
    norm_a = math.sqrt(sum(x ** 2 for x in vec_a))
    norm_b = math.sqrt(sum(y ** 2 for y in vec_b))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


def _get_memory_mb() -> float:
    if _HAS_PSUTIL:
        process = psutil.Process(os.getpid())
        return process.memory_info().rss / (1024 * 1024)
    return 0.0


# ---------------------------------------------------------------------------
# Dataset loader
# ---------------------------------------------------------------------------

class DatasetLoader:
    """加载评估数据集"""

    @staticmethod
    def load_embedding_dataset(path: str) -> Tuple[List[EmbeddingQuery], List[EmbeddingDocument]]:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)

        queries = [
            EmbeddingQuery(
                id=q["id"],
                query=q["query"],
                scenario=q["scenario"],
                relevant_doc_ids=q["relevant_doc_ids"],
                relevance_scores=q["relevance_scores"],
            )
            for q in data["queries"]
        ]

        documents = [
            EmbeddingDocument(
                id=d["id"],
                title=d["title"],
                content=d["content"],
                tags=d.get("tags", []),
            )
            for d in data["documents"]
        ]

        return queries, documents

    @staticmethod
    def load_rerank_dataset(path: str) -> List[RerankQuery]:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)

        return [
            RerankQuery(
                id=q["id"],
                query=q["query"],
                candidates=q["candidates"],
                ideal_ranking=q["ideal_ranking"],
            )
            for q in data["queries"]
        ]


# ---------------------------------------------------------------------------
# Embedding evaluator
# ---------------------------------------------------------------------------

class EmbeddingEvaluator:
    """评估 Embedding 模型的检索质量"""

    def __init__(self, model_name: str):
        self.model_name = model_name
        self._model = None

    def _load_model(self):
        """延迟加载模型"""
        if self._model is not None:
            return
        try:
            from sentence_transformers import SentenceTransformer  # type: ignore
            print(f"  正在加载模型: {self.model_name} ...")
            self._model = SentenceTransformer(self.model_name)
            print(f"  模型加载完成: {self.model_name}")
        except ImportError:
            raise RuntimeError(
                "sentence-transformers 未安装，请运行: "
                "pip install sentence-transformers"
            )

    def encode(self, texts: List[str]) -> List[List[float]]:
        self._load_model()
        embeddings = self._model.encode(texts, show_progress_bar=False, convert_to_numpy=True)
        return embeddings.tolist()

    def evaluate(
        self,
        queries: List[EmbeddingQuery],
        documents: List[EmbeddingDocument],
    ) -> EvaluationMetrics:
        metrics = EvaluationMetrics(model_name=self.model_name)
        latencies: List[float] = []

        try:
            mem_before = _get_memory_mb()

            # Encode all documents (batch)
            doc_texts = [f"{d.title} {d.content}" for d in documents]
            print(f"  编码 {len(doc_texts)} 个文档...")
            doc_embeddings = self.encode(doc_texts)
            doc_id_to_idx = {d.id: i for i, d in enumerate(documents)}

            mrr_values, ndcg5_values, ndcg10_values = [], [], []
            recall5_values, recall10_values = [], []
            precision5_values, precision10_values = [], []

            for q in queries:
                start = time.perf_counter()
                query_embedding = self.encode([q.query])[0]
                elapsed_ms = (time.perf_counter() - start) * 1000
                latencies.append(elapsed_ms)

                # Compute similarities
                sims = [
                    (_cosine_similarity(query_embedding, doc_embeddings[i]), doc.id)
                    for i, doc in enumerate(documents)
                ]
                sims.sort(key=lambda x: x[0], reverse=True)
                retrieved_ids = [doc_id for _, doc_id in sims]

                relevant_ids = set(q.relevant_doc_ids)

                mrr_values.append(_mrr(retrieved_ids, relevant_ids))
                ndcg5_values.append(_ndcg(retrieved_ids, q.relevance_scores, 5))
                ndcg10_values.append(_ndcg(retrieved_ids, q.relevance_scores, 10))
                recall5_values.append(_recall_at_k(retrieved_ids, relevant_ids, 5))
                recall10_values.append(_recall_at_k(retrieved_ids, relevant_ids, 10))
                precision5_values.append(_precision_at_k(retrieved_ids, relevant_ids, 5))
                precision10_values.append(_precision_at_k(retrieved_ids, relevant_ids, 10))

            mem_after = _get_memory_mb()

            def _mean(values: List[float]) -> float:
                return sum(values) / len(values) if values else 0.0

            def _p95(values: List[float]) -> float:
                if not values:
                    return 0.0
                sorted_v = sorted(values)
                idx = max(0, int(len(sorted_v) * 0.95) - 1)
                return sorted_v[idx]

            metrics.mrr = _mean(mrr_values)
            metrics.ndcg_at_5 = _mean(ndcg5_values)
            metrics.ndcg_at_10 = _mean(ndcg10_values)
            metrics.recall_at_5 = _mean(recall5_values)
            metrics.recall_at_10 = _mean(recall10_values)
            metrics.precision_at_5 = _mean(precision5_values)
            metrics.precision_at_10 = _mean(precision10_values)
            metrics.avg_latency_ms = _mean(latencies)
            metrics.p95_latency_ms = _p95(latencies)
            metrics.memory_mb = max(0.0, mem_after - mem_before)
            metrics.query_count = len(queries)

        except Exception as exc:
            metrics.errors.append(f"{type(exc).__name__}: {exc}")
            traceback.print_exc()

        return metrics


# ---------------------------------------------------------------------------
# Rerank evaluator
# ---------------------------------------------------------------------------

class RerankEvaluator:
    """评估 Rerank 模型的重排质量"""

    def __init__(self, model_name: str):
        self.model_name = model_name
        self._model = None

    def _load_model(self):
        if self._model is not None:
            return
        try:
            from sentence_transformers import CrossEncoder  # type: ignore
            print(f"  正在加载重排模型: {self.model_name} ...")
            self._model = CrossEncoder(self.model_name)
            print(f"  模型加载完成: {self.model_name}")
        except ImportError:
            raise RuntimeError(
                "sentence-transformers 未安装，请运行: "
                "pip install sentence-transformers"
            )

    def rerank(self, query: str, candidates: List[str]) -> List[float]:
        self._load_model()
        pairs = [[query, c] for c in candidates]
        scores = self._model.predict(pairs)
        return scores.tolist() if hasattr(scores, "tolist") else list(scores)

    def _kendall_tau(self, ranking_a: List[str], ranking_b: List[str]) -> float:
        """Compute Kendall's Tau between two ranked lists."""
        n = len(ranking_a)
        if n <= 1:
            return 1.0
        pos_b = {doc_id: i for i, doc_id in enumerate(ranking_b)}
        concordant = 0
        discordant = 0
        for i in range(n):
            for j in range(i + 1, n):
                a_i, a_j = ranking_a[i], ranking_a[j]
                if a_i in pos_b and a_j in pos_b:
                    if pos_b[a_i] < pos_b[a_j]:
                        concordant += 1
                    else:
                        discordant += 1
        total = concordant + discordant
        return (concordant - discordant) / total if total > 0 else 0.0

    def evaluate(self, queries: List[RerankQuery]) -> EvaluationMetrics:
        metrics = EvaluationMetrics(model_name=self.model_name)
        latencies: List[float] = []
        ndcg5_values, ndcg_full_values, tau_values = [], [], []

        try:
            mem_before = _get_memory_mb()

            for q in queries:
                candidate_texts = [c["content"] for c in q.candidates]
                candidate_ids = [c["doc_id"] for c in q.candidates]
                relevance_map = {c["doc_id"]: c["relevance"] for c in q.candidates}

                start = time.perf_counter()
                scores = self.rerank(q.query, candidate_texts)
                elapsed_ms = (time.perf_counter() - start) * 1000
                latencies.append(elapsed_ms)

                ranked = sorted(
                    zip(scores, candidate_ids),
                    key=lambda x: x[0],
                    reverse=True,
                )
                predicted_ranking = [doc_id for _, doc_id in ranked]

                ndcg5_values.append(_ndcg(predicted_ranking, relevance_map, 5))
                ndcg_full_values.append(_ndcg(predicted_ranking, relevance_map, len(q.candidates)))
                tau_values.append(self._kendall_tau(predicted_ranking, q.ideal_ranking))

            mem_after = _get_memory_mb()

            def _mean(values: List[float]) -> float:
                return sum(values) / len(values) if values else 0.0

            def _p95(values: List[float]) -> float:
                if not values:
                    return 0.0
                sorted_v = sorted(values)
                idx = max(0, int(len(sorted_v) * 0.95) - 1)
                return sorted_v[idx]

            metrics.ndcg_at_5 = _mean(ndcg5_values)
            metrics.ndcg_at_10 = _mean(ndcg_full_values)
            metrics.mrr = _mean(tau_values)   # reused field: Kendall's Tau
            metrics.avg_latency_ms = _mean(latencies)
            metrics.p95_latency_ms = _p95(latencies)
            metrics.memory_mb = max(0.0, mem_after - mem_before)
            metrics.query_count = len(queries)

        except Exception as exc:
            metrics.errors.append(f"{type(exc).__name__}: {exc}")
            traceback.print_exc()

        return metrics


# ---------------------------------------------------------------------------
# Benchmark runner
# ---------------------------------------------------------------------------

class BenchmarkRunner:
    """统一的基准测试运行器"""

    # Default model candidates
    EMBEDDING_MODELS = [
        "BAAI/bge-large-zh-v1.5",
        "BAAI/bge-base-zh-v1.5",
        "BAAI/bge-m3",
        "sentence-transformers/paraphrase-multilingual-mpnet-base-v2",
    ]

    RERANK_MODELS = [
        "BAAI/bge-reranker-v2-m3",
        "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1",
    ]

    def __init__(
        self,
        embedding_dataset_path: str,
        rerank_dataset_path: str,
        output_dir: str = "tests/results",
    ):
        self.embedding_dataset_path = embedding_dataset_path
        self.rerank_dataset_path = rerank_dataset_path
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

    # ------------------------------------------------------------------
    # Embedding benchmark
    # ------------------------------------------------------------------

    def run_embedding_benchmark(
        self, model_names: Optional[List[str]] = None
    ) -> List[EvaluationMetrics]:
        model_names = model_names or self.EMBEDDING_MODELS
        queries, documents = DatasetLoader.load_embedding_dataset(self.embedding_dataset_path)

        print(f"\n{'='*60}")
        print(f"Embedding 模型基准测试")
        print(f"  查询数量: {len(queries)}")
        print(f"  文档数量: {len(documents)}")
        print(f"  待测模型: {len(model_names)}")
        print(f"{'='*60}\n")

        results: List[EvaluationMetrics] = []
        for model_name in model_names:
            print(f">>> 评估模型: {model_name}")
            evaluator = EmbeddingEvaluator(model_name)
            metrics = evaluator.evaluate(queries, documents)
            results.append(metrics)
            print(metrics.summary())

        return results

    # ------------------------------------------------------------------
    # Rerank benchmark
    # ------------------------------------------------------------------

    def run_rerank_benchmark(
        self, model_names: Optional[List[str]] = None
    ) -> List[EvaluationMetrics]:
        model_names = model_names or self.RERANK_MODELS
        queries = DatasetLoader.load_rerank_dataset(self.rerank_dataset_path)

        print(f"\n{'='*60}")
        print(f"Rerank 模型基准测试")
        print(f"  查询数量: {len(queries)}")
        print(f"  待测模型: {len(model_names)}")
        print(f"{'='*60}\n")

        results: List[EvaluationMetrics] = []
        for model_name in model_names:
            print(f">>> 评估模型: {model_name}")
            evaluator = RerankEvaluator(model_name)
            metrics = evaluator.evaluate(queries)
            results.append(metrics)
            print(metrics.summary())

        return results

    # ------------------------------------------------------------------
    # Report generation
    # ------------------------------------------------------------------

    def _format_table(self, headers: List[str], rows: List[List[str]]) -> str:
        col_widths = [len(h) for h in headers]
        for row in rows:
            for i, cell in enumerate(row):
                if i < len(col_widths):
                    col_widths[i] = max(col_widths[i], len(cell))

        sep = "+" + "+".join("-" * (w + 2) for w in col_widths) + "+"
        header_line = (
            "|"
            + "|".join(f" {h:<{col_widths[i]}} " for i, h in enumerate(headers))
            + "|"
        )
        lines = [sep, header_line, sep]
        for row in rows:
            padded = list(row) + [""] * (len(headers) - len(row))
            line = (
                "|"
                + "|".join(f" {padded[i]:<{col_widths[i]}} " for i in range(len(headers)))
                + "|"
            )
            lines.append(line)
        lines.append(sep)
        return "\n".join(lines)

    def generate_report(
        self,
        embedding_results: List[EvaluationMetrics],
        rerank_results: List[EvaluationMetrics],
    ) -> str:
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        lines = [
            f"# Cook-Agent 模型选型评估报告",
            f"",
            f"**生成时间**: {now}",
            f"",
            f"---",
            f"",
        ]

        # Embedding section
        if embedding_results:
            lines += [
                "## Embedding 模型对比",
                "",
            ]
            headers = ["模型", "MRR", "NDCG@5", "NDCG@10", "Recall@5", "Recall@10", "延迟(ms)", "内存(MB)"]
            rows = []
            for m in embedding_results:
                rows.append([
                    m.model_name.split("/")[-1],
                    f"{m.mrr:.4f}",
                    f"{m.ndcg_at_5:.4f}",
                    f"{m.ndcg_at_10:.4f}",
                    f"{m.recall_at_5:.4f}",
                    f"{m.recall_at_10:.4f}",
                    f"{m.avg_latency_ms:.1f}",
                    f"{m.memory_mb:.1f}",
                ])
            lines.append(self._format_table(headers, rows))
            lines.append("")

            if embedding_results:
                best = max(embedding_results, key=lambda m: m.ndcg_at_5)
                lines += [
                    f"**推荐模型**: `{best.model_name}` (NDCG@5={best.ndcg_at_5:.4f})",
                    "",
                ]

        # Rerank section
        if rerank_results:
            lines += [
                "## Rerank 模型对比",
                "",
            ]
            headers = ["模型", "NDCG@5", "NDCG@全量", "Kendall Tau", "延迟(ms)", "内存(MB)"]
            rows = []
            for m in rerank_results:
                rows.append([
                    m.model_name.split("/")[-1],
                    f"{m.ndcg_at_5:.4f}",
                    f"{m.ndcg_at_10:.4f}",
                    f"{m.mrr:.4f}",
                    f"{m.avg_latency_ms:.1f}",
                    f"{m.memory_mb:.1f}",
                ])
            lines.append(self._format_table(headers, rows))
            lines.append("")

            if rerank_results:
                best = max(rerank_results, key=lambda m: m.ndcg_at_5)
                lines += [
                    f"**推荐模型**: `{best.model_name}` (NDCG@5={best.ndcg_at_5:.4f})",
                    "",
                ]

        return "\n".join(lines)

    def save_report(
        self,
        embedding_results: List[EvaluationMetrics],
        rerank_results: List[EvaluationMetrics],
    ) -> str:
        report = self.generate_report(embedding_results, rerank_results)
        report_path = self.output_dir / "model_selection_report.md"
        report_path.write_text(report, encoding="utf-8")
        print(f"\n报告已保存至: {report_path}")

        # Also save raw metrics as JSON
        def metrics_to_dict(m: EvaluationMetrics) -> dict:
            return {
                "model_name": m.model_name,
                "mrr": m.mrr,
                "ndcg_at_5": m.ndcg_at_5,
                "ndcg_at_10": m.ndcg_at_10,
                "recall_at_5": m.recall_at_5,
                "recall_at_10": m.recall_at_10,
                "precision_at_5": m.precision_at_5,
                "precision_at_10": m.precision_at_10,
                "avg_latency_ms": m.avg_latency_ms,
                "p95_latency_ms": m.p95_latency_ms,
                "memory_mb": m.memory_mb,
                "query_count": m.query_count,
                "errors": m.errors,
            }

        raw_path = self.output_dir / "metrics_raw.json"
        raw_path.write_text(
            json.dumps(
                {
                    "embedding": [metrics_to_dict(m) for m in embedding_results],
                    "rerank": [metrics_to_dict(m) for m in rerank_results],
                    "generated_at": datetime.now().isoformat(),
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
        print(f"原始指标已保存至: {raw_path}")
        return str(report_path)


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Cook-Agent Embedding & Rerank 模型基准测试"
    )
    parser.add_argument(
        "--embedding-data",
        default="tests/datasets/embedding_test_data.json",
        help="Embedding 评估数据集路径",
    )
    parser.add_argument(
        "--rerank-data",
        default="tests/datasets/rerank_test_data.json",
        help="Rerank 评估数据集路径",
    )
    parser.add_argument(
        "--output-dir",
        default="tests/results",
        help="报告输出目录",
    )
    parser.add_argument(
        "--mode",
        choices=["embedding", "rerank", "all"],
        default="all",
        help="评估模式",
    )
    parser.add_argument(
        "--embedding-models",
        nargs="*",
        help="指定要评估的 Embedding 模型列表",
    )
    parser.add_argument(
        "--rerank-models",
        nargs="*",
        help="指定要评估的 Rerank 模型列表",
    )
    args = parser.parse_args()

    runner = BenchmarkRunner(
        embedding_dataset_path=args.embedding_data,
        rerank_dataset_path=args.rerank_data,
        output_dir=args.output_dir,
    )

    embedding_results: List[EvaluationMetrics] = []
    rerank_results: List[EvaluationMetrics] = []

    if args.mode in ("embedding", "all"):
        embedding_results = runner.run_embedding_benchmark(args.embedding_models)

    if args.mode in ("rerank", "all"):
        rerank_results = runner.run_rerank_benchmark(args.rerank_models)

    runner.save_report(embedding_results, rerank_results)


if __name__ == "__main__":
    main()
