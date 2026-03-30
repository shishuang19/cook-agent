package cn.ss.cookagent.rag.retrieval;

import cn.ss.cookagent.rag.embedding.EmbeddingModel;
import cn.ss.cookagent.rag.embedding.EmbeddingModelFactory;
import cn.ss.cookagent.rag.rerank.RerankModel;
import cn.ss.cookagent.rag.rerank.RerankModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 混合检索实现（关键词匹配 + 语义向量检索 + Rerank 重排）。
 *
 * <p>检索流程:
 * <ol>
 *   <li>关键词检索（基于已有的搜索实现）获得候选文档集合</li>
 *   <li>语义向量检索补充召回候选文档</li>
 *   <li>合并去重，得到候选集合</li>
 *   <li>使用 Rerank 模型对候选集合重新排序</li>
 *   <li>返回 TopK 结果</li>
 * </ol>
 */
@Component
public class HybridRetrieval {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrieval.class);

    private final EmbeddingModelFactory embeddingFactory;
    private final RerankModelFactory rerankFactory;

    @Value("${app.rag.hybrid.keyword-weight:0.4}")
    private float keywordWeight;

    @Value("${app.rag.hybrid.semantic-weight:0.6}")
    private float semanticWeight;

    @Value("${app.rag.hybrid.rerank-enabled:true}")
    private boolean rerankEnabled;

    @Value("${app.rag.hybrid.candidate-limit:50}")
    private int candidateLimit;

    @Autowired
    public HybridRetrieval(EmbeddingModelFactory embeddingFactory, RerankModelFactory rerankFactory) {
        this.embeddingFactory = embeddingFactory;
        this.rerankFactory = rerankFactory;
    }

    /**
     * 执行混合检索，返回重排后的 TopK 结果。
     *
     * @param query        用户查询文本
     * @param topK         最终返回结果数量
     * @param documentPool 待检索的文档池（id, content 对）
     * @return 按相关性降序排列的检索结果列表
     */
    public List<RetrievalResult> retrieve(
            String query, int topK, List<DocumentEntry> documentPool) {

        if (documentPool == null || documentPool.isEmpty()) {
            return List.of();
        }

        log.debug("HybridRetrieval: query='{}', topK={}, pool={}", query, topK, documentPool.size());

        // Step 1: Keyword scoring
        List<ScoredEntry> keywordScored = scoreByKeyword(query, documentPool);

        // Step 2: Semantic scoring
        List<ScoredEntry> semanticScored = scoreBySemantics(query, documentPool);

        // Step 3: Merge and deduplicate
        List<ScoredEntry> merged = mergeScores(keywordScored, semanticScored, documentPool.size());

        // Take top candidates
        int limit = Math.min(candidateLimit, merged.size());
        List<ScoredEntry> candidates = merged.subList(0, limit);

        if (!rerankEnabled || candidates.isEmpty()) {
            return toRetrievalResults(candidates, topK);
        }

        // Step 4: Rerank
        return rerank(query, candidates, topK);
    }

    // ------------------------------------------------------------------
    // Keyword scoring (simple TF / substring match)
    // ------------------------------------------------------------------

    private List<ScoredEntry> scoreByKeyword(String query, List<DocumentEntry> docs) {
        String[] tokens = query.split("\\s+");
        List<ScoredEntry> result = new ArrayList<>(docs.size());
        for (DocumentEntry doc : docs) {
            float score = 0f;
            String lower = doc.content().toLowerCase();
            for (String token : tokens) {
                if (!token.isEmpty() && lower.contains(token.toLowerCase())) {
                    score += 1.0f;
                }
            }
            score = (tokens.length > 0) ? score / tokens.length : 0f;
            result.add(new ScoredEntry(doc, score * keywordWeight, 0f));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Semantic scoring via EmbeddingModel
    // ------------------------------------------------------------------

    private List<ScoredEntry> scoreBySemantics(String query, List<DocumentEntry> docs) {
        try {
            EmbeddingModel embeddingModel = embeddingFactory.createModel();
            List<Float> queryVec = embeddingModel.embed(query);

            List<String> docTexts = docs.stream().map(DocumentEntry::content).toList();
            List<List<Float>> docVecs = embeddingModel.embedBatch(docTexts);

            List<ScoredEntry> result = new ArrayList<>(docs.size());
            for (int i = 0; i < docs.size(); i++) {
                float sim = cosineSimilarity(queryVec, docVecs.get(i));
                result.add(new ScoredEntry(docs.get(i), 0f, sim * semanticWeight));
            }
            return result;

        } catch (Exception e) {
            log.warn("Semantic scoring failed, falling back to keyword-only: {}", e.getMessage());
            List<ScoredEntry> fallback = new ArrayList<>(docs.size());
            for (DocumentEntry doc : docs) {
                fallback.add(new ScoredEntry(doc, 0f, 0f));
            }
            return fallback;
        }
    }

    // ------------------------------------------------------------------
    // Score merging
    // ------------------------------------------------------------------

    private List<ScoredEntry> mergeScores(
            List<ScoredEntry> keyword, List<ScoredEntry> semantic, int size) {
        List<ScoredEntry> merged = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            float kw = (i < keyword.size()) ? keyword.get(i).keywordScore() : 0f;
            float sem = (i < semantic.size()) ? semantic.get(i).semanticScore() : 0f;
            DocumentEntry doc = (i < keyword.size()) ? keyword.get(i).entry() : semantic.get(i).entry();
            merged.add(new ScoredEntry(doc, kw, sem));
        }
        merged.sort(Comparator.comparingDouble(e -> -(e.keywordScore() + e.semanticScore())));
        return merged;
    }

    // ------------------------------------------------------------------
    // Rerank
    // ------------------------------------------------------------------

    private List<RetrievalResult> rerank(String query, List<ScoredEntry> candidates, int topK) {
        try {
            RerankModel rerankModel = rerankFactory.createModel();
            List<String> texts = candidates.stream()
                    .map(c -> c.entry().content())
                    .toList();

            List<RerankModel.RerankResult> reranked = rerankModel.rerank(query, texts, topK);

            List<RetrievalResult> results = new ArrayList<>(reranked.size());
            for (RerankModel.RerankResult r : reranked) {
                DocumentEntry entry = candidates.get(r.index()).entry();
                results.add(new RetrievalResult(entry.id(), entry.content(), r.score()));
            }
            return results;

        } catch (Exception e) {
            log.warn("Rerank failed, falling back to hybrid score: {}", e.getMessage());
            return toRetrievalResults(candidates, topK);
        }
    }

    private List<RetrievalResult> toRetrievalResults(List<ScoredEntry> scored, int topK) {
        int limit = (topK > 0) ? Math.min(topK, scored.size()) : scored.size();
        List<RetrievalResult> results = new ArrayList<>(limit);
        for (ScoredEntry s : scored.subList(0, limit)) {
            float combinedScore = s.keywordScore() + s.semanticScore();
            results.add(new RetrievalResult(s.entry().id(), s.entry().content(), combinedScore));
        }
        return results;
    }

    // ------------------------------------------------------------------
    // Cosine similarity (pure Java)
    // ------------------------------------------------------------------

    private static float cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) {
            return 0f;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            double ai = a.get(i);
            double bi = b.get(i);
            dot += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return (denom == 0) ? 0f : (float) (dot / denom);
    }

    // ------------------------------------------------------------------
    // Inner types
    // ------------------------------------------------------------------

    /**
     * 文档条目。
     *
     * @param id      文档唯一标识
     * @param content 文档内容
     */
    public record DocumentEntry(String id, String content) {}

    /**
     * 检索结果。
     *
     * @param id      文档唯一标识
     * @param content 文档内容
     * @param score   最终相关性得分
     */
    public record RetrievalResult(String id, String content, float score) {}

    private record ScoredEntry(DocumentEntry entry, float keywordScore, float semanticScore) {}
}
