package cn.ss.cookagent.rag.embedding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 通过 HTTP 调用本地 Sentence-Transformers 服务获取文本向量。
 *
 * <p>依赖一个轻量的 Python FastAPI/Flask 服务，启动示例：
 * <pre>
 * pip install sentence-transformers fastapi uvicorn
 * uvicorn embedding_server:app --port 8000
 * </pre>
 *
 * <p>服务接口约定（POST /embed）:
 * <pre>
 * 请求体: {"texts": ["文本1", "文本2"]}
 * 响应体: {"embeddings": [[0.1, ...], [0.2, ...]], "dimension": 1024}
 * </pre>
 */
public class SentenceTransformersEmbedding implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(SentenceTransformersEmbedding.class);

    private static final int DEFAULT_DIMENSION = 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String modelName;
    private final String serviceUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private int dimension = DEFAULT_DIMENSION;

    public SentenceTransformersEmbedding(String modelName, String serviceUrl) {
        this.modelName = modelName;
        this.serviceUrl = serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public List<Float> embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("texts", texts));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/embed"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(TIMEOUT)
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Embedding service returned HTTP " + response.statusCode()
                                + ": " + response.body());
            }

            Map<String, Object> responseBody =
                    objectMapper.readValue(response.body(), new TypeReference<>() {});

            if (responseBody.containsKey("dimension")) {
                this.dimension = ((Number) responseBody.get("dimension")).intValue();
            }

            @SuppressWarnings("unchecked")
            List<List<Number>> rawEmbeddings =
                    (List<List<Number>>) responseBody.get("embeddings");

            List<List<Float>> result = new java.util.ArrayList<>(rawEmbeddings.size());
            for (List<Number> vec : rawEmbeddings) {
                List<Float> floatVec = new java.util.ArrayList<>(vec.size());
                for (Number v : vec) {
                    floatVec.add(v.floatValue());
                }
                result.add(floatVec);
            }
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call embedding service at " + serviceUrl, e);
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }
}
