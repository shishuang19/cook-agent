# 模型选型指南

## 概述

本指南帮助你为 Cook-Agent 的中文菜谱 RAG 场景选择最合适的 Embedding 和 Rerank 模型，包含评估指标体系、测试步骤、快速测试模板和选型决策框架。

---

## 评估指标体系

### 检索质量指标

| 指标 | 公式 | 说明 |
|------|------|------|
| **MRR** | 1/rank(first relevant) | 第一个相关文档的排名倒数，衡量"能否快速找到相关结果" |
| **NDCG@K** | DCG@K / IDCG@K | 综合考虑排名位置和相关性程度，K 通常取 5 或 10 |
| **Recall@K** | \|relevant ∩ top-K\| / \|relevant\| | 前 K 个结果中覆盖了多少相关文档 |
| **Precision@K** | \|relevant ∩ top-K\| / K | 前 K 个结果中有多少是相关的 |

### 性能指标

| 指标 | 说明 | 测量方式 |
|------|------|---------|
| **平均延迟** | 单次查询编码/重排的平均耗时 | 多次测量取均值 |
| **P95 延迟** | 95% 分位延迟，反映极端情况 | 排序后取 95 百分位 |
| **内存占用** | 模型加载后的内存增量 | 通过 psutil 测量进程 RSS |

---

## 7 步测试工作流

### 第 1 步：环境准备

```bash
# 安装依赖
pip install -r requirements-model-selection.txt

# 验证数据集
python tests/model_selection/prepare_benchmark_data.py
```

### 第 2 步：运行 Embedding 基线评估

使用最小模型建立基线，快速验证测试流程是否正常：

```bash
python tests/model_selection/embedding_rerank_benchmark.py \
  --mode embedding \
  --embedding-models BAAI/bge-base-zh-v1.5
```

### 第 3 步：运行全量 Embedding 对比

```bash
python tests/model_selection/embedding_rerank_benchmark.py --mode embedding
```

### 第 4 步：分析 Embedding 结果

查看 `tests/results/model_selection_report.md`，重点关注：
- NDCG@5：最重要的综合指标
- Recall@10：召回率是否满足业务需求
- 延迟：是否在可接受范围内

### 第 5 步：运行 Rerank 评估

```bash
python tests/model_selection/embedding_rerank_benchmark.py --mode rerank
```

### 第 6 步：测试最优组合

选定最优 Embedding 和 Rerank 模型后，测试其组合效果：

```bash
python tests/model_selection/embedding_rerank_benchmark.py \
  --mode all \
  --embedding-models BAAI/bge-large-zh-v1.5 \
  --rerank-models BAAI/bge-reranker-v2-m3
```

### 第 7 步：集成到 Spring Boot

更新配置文件（`application-model-selection.yaml` 或 `application.yml`）：

```yaml
app:
  rag:
    embedding:
      model-name: BAAI/bge-large-zh-v1.5  # 替换为你选择的最优模型
    rerank:
      model-name: BAAI/bge-reranker-v2-m3   # 替换为你选择的最优模型
```

---

## 快速测试模板（Python）

```python
"""
快速单模型评估模板
修改 EMBEDDING_MODEL 和 RERANK_MODEL 后直接运行
"""
import json
from tests.model_selection.embedding_rerank_benchmark import (
    BenchmarkRunner,
    DatasetLoader,
    EmbeddingEvaluator,
    RerankEvaluator,
)

EMBEDDING_MODEL = "BAAI/bge-large-zh-v1.5"
RERANK_MODEL = "BAAI/bge-reranker-v2-m3"

# --- Embedding 评估 ---
queries, documents = DatasetLoader.load_embedding_dataset(
    "tests/datasets/embedding_test_data.json"
)
emb_evaluator = EmbeddingEvaluator(EMBEDDING_MODEL)
emb_metrics = emb_evaluator.evaluate(queries, documents)
print(f"=== {EMBEDDING_MODEL} ===")
print(emb_metrics.summary())

# --- Rerank 评估 ---
rerank_queries = DatasetLoader.load_rerank_dataset(
    "tests/datasets/rerank_test_data.json"
)
rr_evaluator = RerankEvaluator(RERANK_MODEL)
rr_metrics = rr_evaluator.evaluate(rerank_queries)
print(f"=== {RERANK_MODEL} ===")
print(rr_metrics.summary())
```

---

## 选型决策框架

### 优先级排序

1. **NDCG@5 ≥ 0.70**：核心指标，低于此值不予考虑
2. **Recall@10 ≥ 0.75**：保证足够的召回覆盖
3. **延迟 P95 ≤ 200ms**：满足在线服务要求
4. **内存占用 ≤ 3 GB**：适配常见服务器配置

### 推荐方案

#### 方案 A：最佳效果
- **Embedding**：`BAAI/bge-large-zh-v1.5`
- **Rerank**：`BAAI/bge-reranker-v2-m3`
- **适用场景**：对检索效果要求最高，有 GPU 资源
- **预期延迟**：Embedding 80–120ms + Rerank 50–100ms（GPU）

#### 方案 B：性价比最优
- **Embedding**：`BAAI/bge-base-zh-v1.5`
- **Rerank**：`BAAI/bge-reranker-v2-m3`
- **适用场景**：兼顾效果与速度，CPU 环境
- **预期延迟**：Embedding 30–60ms + Rerank 80–150ms（CPU）

#### 方案 C：轻量化
- **Embedding**：`BAAI/bge-base-zh-v1.5`
- **Rerank**：`cross-encoder/mmarco-mMiniLMv2-L12-H384-v1`
- **适用场景**：资源受限，低延迟优先
- **预期延迟**：Embedding 30–60ms + Rerank 30–50ms（CPU）

---

## 常见问题解答

**Q: 如何启动本地 Embedding 服务？**

```python
# embedding_server.py
from fastapi import FastAPI
from sentence_transformers import SentenceTransformer
import uvicorn

app = FastAPI()
model = SentenceTransformer("BAAI/bge-large-zh-v1.5")

@app.post("/embed")
def embed(body: dict):
    texts = body["texts"]
    vecs = model.encode(texts, convert_to_numpy=True).tolist()
    return {"embeddings": vecs, "dimension": len(vecs[0]) if vecs else 0}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

```bash
pip install fastapi uvicorn sentence-transformers
python embedding_server.py
```

**Q: 如何启动本地 Rerank 服务？**

```python
# rerank_server.py
from fastapi import FastAPI
from sentence_transformers import CrossEncoder
import uvicorn

app = FastAPI()
model = CrossEncoder("BAAI/bge-reranker-v2-m3")

@app.post("/rerank")
def rerank(body: dict):
    query = body["query"]
    candidates = body["candidates"]
    pairs = [[query, c] for c in candidates]
    scores = model.predict(pairs).tolist()
    return {"scores": scores}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)
```

**Q: 模型下载太慢怎么办？**

```bash
# 使用 HuggingFace 镜像（国内加速）
export HF_ENDPOINT=https://hf-mirror.com
python tests/model_selection/embedding_rerank_benchmark.py --mode embedding
```

**Q: 如何只评估特定场景？**

修改 `tests/datasets/embedding_test_data.json`，保留你感兴趣的场景对应的查询，然后重新运行评估即可。

**Q: 在 Java 端如何调用混合检索？**

```java
@Autowired
private HybridRetrieval hybridRetrieval;

// 准备文档池
List<HybridRetrieval.DocumentEntry> pool = recipes.stream()
    .map(r -> new HybridRetrieval.DocumentEntry(r.getId(), r.getContent()))
    .toList();

// 执行混合检索
List<HybridRetrieval.RetrievalResult> results =
    hybridRetrieval.retrieve(userQuery, 10, pool);

results.forEach(r ->
    System.out.printf("  [%.3f] %s%n", r.score(), r.content().substring(0, 50))
);
```
