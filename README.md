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

## docker 部署

### 方式 A：仅数据库容器化（推荐先用这个）

当前仓库已提供 `docker/docker-compose.yml`，可以先把 PostgreSQL + PGvector 跑起来，再本地启动后端。

1. 启动数据库容器

```bash
docker compose -f docker/docker-compose.yml up -d
```

2. 检查容器状态

```bash
docker ps | grep cook-agent-postgres
```

3. 启动后端（连接 Docker 中的 PostgreSQL）

```bash
# 真实模型
QWEN_API_KEY=你的Key ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres

# 或本地演示（不调真实模型）
LLM_MOCK_ENABLED=true ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

```

## 图片占位（请你后续补图）

> 下面是建议插图位置与建议内容，我已预留标题，你可以直接替换为你的截图链接。


建议放图内容：
- 前端 -> API -> 编排层 -> RAG/Memory/Recipe -> 数据库/模型服务的完整链路

### 1) 问答页面截图

![问答页面占位](./docs/images/qa.png)


### 2) 检索页面截图

![检索页面占位](./docs/images/search.png)


