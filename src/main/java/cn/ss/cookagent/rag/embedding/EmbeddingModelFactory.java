package cn.ss.cookagent.rag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Embedding 模型工厂。
 *
 * <p>根据配置属性 {@code app.rag.embedding.provider} 创建相应的 {@link EmbeddingModel} 实例。
 * 目前支持：
 * <ul>
 *   <li>{@code sentence-transformers}（默认）— 调用本地 Python HTTP 服务</li>
 *   <li>{@code spring-ai} — 委托给 Spring AI 的 EmbeddingModel</li>
 * </ul>
 */
@Component
public class EmbeddingModelFactory {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelFactory.class);

    @Value("${app.rag.embedding.provider:sentence-transformers}")
    private String provider;

    @Value("${app.rag.embedding.model-name:BAAI/bge-large-zh-v1.5}")
    private String modelName;

    @Value("${app.rag.embedding.service-url:http://localhost:8000}")
    private String serviceUrl;

    /** Spring AI EmbeddingModel (optional — injected only when the bean is present). */
    private final org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel;

    public EmbeddingModelFactory(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel) {
        this.springAiEmbeddingModel = springAiEmbeddingModel;
    }

    /**
     * 根据当前配置创建 {@link EmbeddingModel}。
     *
     * @return 配置对应的 EmbeddingModel 实例
     */
    public EmbeddingModel createModel() {
        log.info("Creating EmbeddingModel: provider={}, model={}", provider, modelName);
        return switch (provider.toLowerCase()) {
            case "spring-ai" -> createSpringAiModel();
            default -> new SentenceTransformersEmbedding(modelName, serviceUrl);
        };
    }

    /**
     * 使用指定模型名称覆盖配置，创建 {@link SentenceTransformersEmbedding}。
     *
     * @param overrideModelName 模型名称
     * @return 对应的 EmbeddingModel 实例
     */
    public EmbeddingModel createModel(String overrideModelName) {
        log.info("Creating SentenceTransformersEmbedding: model={}", overrideModelName);
        return new SentenceTransformersEmbedding(overrideModelName, serviceUrl);
    }

    private EmbeddingModel createSpringAiModel() {
        if (springAiEmbeddingModel == null) {
            log.warn("Spring AI EmbeddingModel bean not found; falling back to SentenceTransformers");
            return new SentenceTransformersEmbedding(modelName, serviceUrl);
        }
        return new SpringAiEmbeddingAdapter(springAiEmbeddingModel, modelName);
    }

    // ------------------------------------------------------------------
    // Inner adapter: wraps Spring AI EmbeddingModel
    // ------------------------------------------------------------------

    private static final class SpringAiEmbeddingAdapter implements EmbeddingModel {

        private final org.springframework.ai.embedding.EmbeddingModel delegate;
        private final String name;

        SpringAiEmbeddingAdapter(
                org.springframework.ai.embedding.EmbeddingModel delegate, String name) {
            this.delegate = delegate;
            this.name = name;
        }

        @Override
        public String getModelName() {
            return name;
        }

        @Override
        public java.util.List<Float> embed(String text) {
            float[] raw = delegate.embed(text);
            java.util.List<Float> result = new java.util.ArrayList<>(raw.length);
            for (float v : raw) {
                result.add(v);
            }
            return result;
        }

        @Override
        public int getDimension() {
            return delegate.dimensions();
        }
    }
}
