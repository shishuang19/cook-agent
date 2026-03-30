package cn.ss.cookagent.rag.rerank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rerank 模型工厂。
 *
 * <p>根据配置属性 {@code app.rag.rerank.provider} 创建相应的 {@link RerankModel} 实例。
 */
@Component
public class RerankModelFactory {

    private static final Logger log = LoggerFactory.getLogger(RerankModelFactory.class);

    @Value("${app.rag.rerank.provider:sentence-transformers}")
    private String provider;

    @Value("${app.rag.rerank.model-name:BAAI/bge-reranker-v2-m3}")
    private String modelName;

    @Value("${app.rag.rerank.service-url:http://localhost:8001}")
    private String serviceUrl;

    /**
     * 根据当前配置创建 {@link RerankModel}。
     *
     * @return 配置对应的 RerankModel 实例
     */
    public RerankModel createModel() {
        log.info("Creating RerankModel: provider={}, model={}", provider, modelName);
        return new HttpRerankModel(modelName, serviceUrl);
    }

    /**
     * 使用指定模型名称覆盖配置，创建 RerankModel。
     *
     * @param overrideModelName 模型名称
     * @return 对应的 RerankModel 实例
     */
    public RerankModel createModel(String overrideModelName) {
        log.info("Creating HttpRerankModel: model={}", overrideModelName);
        return new HttpRerankModel(overrideModelName, serviceUrl);
    }

    // ------------------------------------------------------------------
    // Default implementation: calls an HTTP rerank microservice
    // ------------------------------------------------------------------

    /**
     * 通过 HTTP 调用本地 Cross-Encoder 服务进行重排。
     *
     * <p>服务接口约定（POST /rerank）:
     * <pre>
     * 请求体: {"query": "...", "candidates": ["文档1", "文档2", ...]}
     * 响应体: {"scores": [0.95, 0.42, ...]}
     * </pre>
     */
    static final class HttpRerankModel implements RerankModel {

        private static final Logger log = LoggerFactory.getLogger(HttpRerankModel.class);
        private static final Duration TIMEOUT = Duration.ofSeconds(30);

        private final String modelName;
        private final String serviceUrl;
        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        HttpRerankModel(String modelName, String serviceUrl) {
            this.modelName = modelName;
            this.serviceUrl = serviceUrl.endsWith("/")
                    ? serviceUrl.substring(0, serviceUrl.length() - 1)
                    : serviceUrl;
            this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            this.objectMapper = new ObjectMapper();
        }

        @Override
        public String getModelName() {
            return modelName;
        }

        @Override
        public List<RerankResult> rerank(String query, List<String> candidates, int topK) {
            if (candidates == null || candidates.isEmpty()) {
                return List.of();
            }
            try {
                String body = objectMapper.writeValueAsString(
                        Map.of("query", query, "candidates", candidates));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serviceUrl + "/rerank"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(TIMEOUT)
                        .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException(
                            "Rerank service returned HTTP " + response.statusCode()
                                    + ": " + response.body());
                }

                Map<String, Object> responseBody =
                        objectMapper.readValue(response.body(), new TypeReference<>() {});

                @SuppressWarnings("unchecked")
                List<Number> scores = (List<Number>) responseBody.get("scores");

                List<RerankResult> results = new ArrayList<>(candidates.size());
                for (int i = 0; i < candidates.size(); i++) {
                    float score = (i < scores.size()) ? scores.get(i).floatValue() : 0f;
                    results.add(new RerankResult(i, candidates.get(i), score));
                }

                results.sort(Comparator.comparingDouble(RerankResult::score).reversed());

                return (topK > 0 && topK < results.size())
                        ? results.subList(0, topK)
                        : results;

            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to call rerank service at " + serviceUrl, e);
            }
        }
    }
}
