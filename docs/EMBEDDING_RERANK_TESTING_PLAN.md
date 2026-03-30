# Embedding & Rerank 模型选型测试计划

## 概述

本文档描述 Cook-Agent 项目中 Embedding 和 Rerank 模型的 4 周评估计划，目标是为中文菜谱 RAG 场景选出最优的模型组合。

---

## 模型候选列表

### Embedding 模型（4 个）

| 模型 | 维度 | 模型大小 | 最大序列长度 |
|------|------|----------|------------|
| `BAAI/bge-large-zh-v1.5` | 1024 | 1.3 GB | 512 |
| `BAAI/bge-base-zh-v1.5` | 768 | 0.4 GB | 512 |
| `BAAI/bge-m3` | 1024 | 2.2 GB | 8192 |
| `sentence-transformers/paraphrase-multilingual-mpnet-base-v2` | 768 | 1.1 GB | 512 |

### Rerank 模型（2 个）

| 模型 | 模型大小 | 语言 |
|------|----------|------|
| `BAAI/bge-reranker-v2-m3` | 2.2 GB | 多语言 |
| `cross-encoder/mmarco-mMiniLMv2-L12-H384-v1` | 0.4 GB | 多语言 |

---

## 评估指标

| 指标 | 说明 | 目标值 |
|------|------|--------|
| MRR | 平均倒数排名，衡量第一个相关结果的排名 | ≥ 0.75 |
| NDCG@5 | 前 5 名归一化折损累积增益 | ≥ 0.70 |
| NDCG@10 | 前 10 名归一化折损累积增益 | ≥ 0.70 |
| Recall@5 | 前 5 名召回率 | ≥ 0.65 |
| Recall@10 | 前 10 名召回率 | ≥ 0.80 |
| 平均延迟 | 单次查询平均响应时间 | ≤ 100 ms |
| P95 延迟 | 95% 分位延迟 | ≤ 200 ms |
| 内存占用 | 模型推理内存增量 | ≤ 3 GB |

---

## 4 周测试日程

### 第 1 周：环境搭建与基线建立

**目标**：搭建测试环境，运行基线评估，确认数据集质量。

**任务清单**:

- [ ] 安装 Python 依赖: `pip install -r requirements-model-selection.txt`
- [ ] 验证数据集: `python tests/model_selection/prepare_benchmark_data.py`
- [ ] 下载并验证 `bge-base-zh-v1.5`（最小候选模型，作为基线）
- [ ] 运行单模型评估: `python tests/model_selection/embedding_rerank_benchmark.py --mode embedding --embedding-models BAAI/bge-base-zh-v1.5`
- [ ] 记录基线指标，填写评估结果模板

**验收标准**:

- 数据集验证无错误
- 基线模型评估完成并有记录
- 测试环境文档完整

---

### 第 2 周：Embedding 模型全量评估

**目标**：完成 4 个 Embedding 模型的对比评估，选出最优模型。

**任务清单**:

- [ ] 下载全部 4 个 Embedding 模型（合计约 5 GB）
- [ ] 运行全量 Embedding 评估:
  ```bash
  python tests/model_selection/embedding_rerank_benchmark.py --mode embedding
  ```
- [ ] 分析对比报告: `tests/results/model_selection_report.md`
- [ ] 记录各模型在每个场景（菜谱搜索、食材查询、烹饪方法等）的表现
- [ ] 选出综合得分最高的 Embedding 模型

**验收标准**:

- 4 个模型评估完成
- 对比报告生成
- 最优 Embedding 模型已确定

---

### 第 3 周：Rerank 模型评估与组合优化

**目标**：完成 Rerank 模型评估，测试 Embedding + Rerank 最优组合。

**任务清单**:

- [ ] 下载 2 个 Rerank 模型
- [ ] 运行 Rerank 评估:
  ```bash
  python tests/model_selection/embedding_rerank_benchmark.py --mode rerank
  ```
- [ ] 测试最优 Embedding + 最优 Rerank 的组合效果
- [ ] 测试最优 Embedding + 轻量 Rerank 的组合效果
- [ ] 评估各组合的延迟-效果权衡曲线
- [ ] 确定最终推荐方案

**验收标准**:

- 2 个 Rerank 模型评估完成
- 至少 2 种组合方案的评估结果
- 最优组合方案已确定

---

### 第 4 周：集成测试与文档完善

**目标**：将选定模型集成到 Spring Boot 项目，完成端到端测试。

**任务清单**:

- [ ] 更新 `application-model-selection.yaml` 中的模型配置
- [ ] 启动本地 Embedding 服务（参考 `model_selection_guide.md`）
- [ ] 启动本地 Rerank 服务
- [ ] 运行 Spring Boot 集成测试
- [ ] 使用真实菜谱查询测试 `HybridRetrieval` 端到端效果
- [ ] 完善 `EVALUATION_RESULTS_TEMPLATE.md` 中的结果记录
- [ ] 更新 README.md 中的模型相关说明

**验收标准**:

- Spring Boot 应用可正常调用 Embedding 和 Rerank 服务
- 端到端延迟满足目标（P95 ≤ 200ms）
- 所有文档更新完毕

---

## 快速评估命令

```bash
# 0. 验证数据集
python tests/model_selection/prepare_benchmark_data.py

# 1. 仅评估 Embedding 模型
python tests/model_selection/embedding_rerank_benchmark.py --mode embedding

# 2. 仅评估 Rerank 模型
python tests/model_selection/embedding_rerank_benchmark.py --mode rerank

# 3. 完整评估（Embedding + Rerank）
python tests/model_selection/embedding_rerank_benchmark.py --mode all

# 4. 指定特定模型评估
python tests/model_selection/embedding_rerank_benchmark.py \
  --mode embedding \
  --embedding-models BAAI/bge-large-zh-v1.5 BAAI/bge-base-zh-v1.5

# 5. 查看报告
cat tests/results/model_selection_report.md
```

---

## 预期结果示例

> ⚠️ **注意**：以下数值为基于业界公开基准和社区经验的粗略估计，仅供参考。实际结果会因硬件配置、数据分布和推理环境的不同而存在显著差异，请以实际评估结果为准。

基于业界经验，以下是对各模型在中文菜谱场景下的预期表现：

| 模型 | 预期 MRR | 预期 NDCG@5 | 预期延迟 (CPU) |
|------|----------|-------------|---------------|
| bge-large-zh-v1.5 | 0.82–0.88 | 0.78–0.84 | 80–120 ms |
| bge-base-zh-v1.5 | 0.76–0.82 | 0.72–0.78 | 30–60 ms |
| bge-m3 | 0.80–0.86 | 0.76–0.82 | 120–180 ms |
| multilingual-mpnet | 0.68–0.74 | 0.64–0.70 | 40–80 ms |

---

## 检查清单

### 环境准备
- [ ] Python ≥ 3.10
- [ ] PyTorch ≥ 2.1
- [ ] sentence-transformers ≥ 2.7
- [ ] 磁盘空间 ≥ 10 GB（用于存放模型文件）
- [ ] 内存 ≥ 8 GB（推荐 16 GB）

### 数据准备
- [ ] `tests/datasets/embedding_test_data.json` 存在且验证通过
- [ ] `tests/datasets/rerank_test_data.json` 存在且验证通过

### 模型评估
- [ ] 所有 Embedding 模型评估完成
- [ ] 所有 Rerank 模型评估完成
- [ ] 对比报告生成

### 集成部署
- [ ] Spring Boot 配置更新
- [ ] 端到端测试通过
- [ ] 文档更新完毕
