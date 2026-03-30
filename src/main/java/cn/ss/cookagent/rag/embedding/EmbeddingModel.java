package cn.ss.cookagent.rag.embedding;

import java.util.List;

/**
 * Embedding 模型接口，封装向量编码能力。
 *
 * <p>实现类可以是本地 Sentence-Transformers 模型、远程 API（如阿里云百炼、OpenAI）
 * 或任何其他向量化服务。
 */
public interface EmbeddingModel {

    /**
     * 返回该模型的唯一标识名称，例如 {@code "BAAI/bge-large-zh-v1.5"}。
     *
     * @return 模型名称
     */
    String getModelName();

    /**
     * 将单段文本编码为浮点向量。
     *
     * @param text 输入文本（不能为 null）
     * @return 向量表示，长度取决于具体模型
     */
    List<Float> embed(String text);

    /**
     * 批量将文本列表编码为向量列表。
     *
     * <p>默认实现逐条调用 {@link #embed(String)}，子类可以覆盖此方法以利用批处理加速。
     *
     * @param texts 输入文本列表（不能为 null，列表元素也不能为 null）
     * @return 与输入列表顺序对应的向量列表
     */
    default List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> result = new java.util.ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(embed(text));
        }
        return result;
    }

    /**
     * 返回该模型输出向量的维度。
     *
     * @return 向量维度
     */
    int getDimension();
}
