# Cook Agent

一个基于 Spring Boot + Spring AI + PostgreSQL/PGvector 的 AI 菜谱助手，支持菜谱检索、问答、推荐、多轮会话和流式输出。

## 核心能力

- 菜谱知识库导入：Markdown -> Recipe/Section/Chunk
- 混合检索：关键词 + 元数据过滤 + 重排
- 智能编排：ReAct 工具路由（qa/search/recommend）
- 会话记忆：多轮上下文与滚动摘要
- 可解释回答：返回 citations 证据链
- 实时交互：SSE 流式输出

## 技术栈

- 后端：Java 21, Spring Boot 3.3, Spring Web, Spring AI
- 数据：PostgreSQL, PGvector, Flyway
- 前端：HTML, JavaScript
- 测试：JUnit, MockMvc

## 项目结构（简版）

```text
src/main/java/cn/ss/cookagent
  api/            # 接口层
  orchestrator/   # ReAct 编排
  rag/            # 检索与重排
  memory/         # 会话记忆
  recipe/         # 菜谱领域服务
  storage/        # 存储与基础设施
kitchen-orbit-ui/ # 前端页面与静态资源
docs/cook/        # 可提交的菜谱文档
```

## 快速启动

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

健康检查：`GET /health`

## API 概览

- `POST /api/session`：创建会话
- `POST /api/chat`：非流式问答
- `POST /api/chat/stream`：SSE 流式问答
- `POST /api/search`：菜谱检索

## 图片占位（请你后续补图）

> 下面是建议插图位置与建议内容，我已预留标题，你可以直接替换为你的截图链接。

### 1) 系统架构图（建议放 Mermaid 导出图）

![系统架构图占位](./docs/cook/_images/architecture-placeholder.png)

建议放图内容：
- 前端 -> API -> 编排层 -> RAG/Memory/Recipe -> 数据库/模型服务的完整链路

### 2) 问答页面截图（非流式）

![问答页面占位](./docs/cook/_images/qa-non-stream-placeholder.png)

建议放图内容：
- 输入问题后返回结构化回答 + 来源 citations

### 3) 流式输出截图（实时增量）

![流式输出占位](./docs/cook/_images/qa-stream-placeholder.png)

建议放图内容：
- 勾选“真实流式输出”后，逐段增量渲染过程

### 4) 检索页面截图

![检索页面占位](./docs/cook/_images/search-placeholder.png)

建议放图内容：
- 关键词检索结果、筛选项和排序效果

### 5) 数据导入/评测结果截图

![导入评测占位](./docs/cook/_images/etl-eval-placeholder.png)

建议放图内容：
- 导入统计（recipe/section/chunk）与基础评测结果

## 说明

- 当前远程提交策略：代码全量提交；文档仅保留 `docs/cook`；不提交根目录 `prd.md`。
