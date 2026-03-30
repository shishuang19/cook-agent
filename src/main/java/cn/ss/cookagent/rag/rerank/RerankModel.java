package cn.ss.cookagent.rag.rerank;

import java.util.List;

/**
 * Rerank 模型接口，对候选文档列表按相关性重新排序。
 *
 * <p>实现类可以是本地 Cross-Encoder 模型、远程 Rerank API 或基于规则的排序策略。
 */
public interface RerankModel {

    /**
     * 返回该模型的唯一标识名称，例如 {@code "BAAI/bge-reranker-v2-m3"}。
     *
     * @return 模型名称
     */
    String getModelName();

    /**
     * 对候选文档进行重排，返回按相关性降序排列的结果列表。
     *
     * @param query      用户查询文本（不能为 null）
     * @param candidates 候选文档列表（不能为 null）
     * @param topK       最多返回的结果数量（&lt;= 0 表示返回全部）
     * @return 重排后的结果列表
     */
    List<RerankResult> rerank(String query, List<String> candidates, int topK);

    /**
     * 返回全部候选文档的重排结果（不截断）。
     *
     * @param query      用户查询文本
     * @param candidates 候选文档列表
     * @return 重排后的结果列表
     */
    default List<RerankResult> rerank(String query, List<String> candidates) {
        return rerank(query, candidates, 0);
    }

    // ------------------------------------------------------------------
    // Nested result type
    // ------------------------------------------------------------------

    /**
     * 单条重排结果。
     *
     * @param index     原始候选列表中的索引
     * @param content   文档内容
     * @param score     相关性得分（越高越相关）
     */
    record RerankResult(int index, String content, float score) {}
}
