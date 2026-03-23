package cn.ss.cookagent.rag.ingest.parser;

import cn.ss.cookagent.rag.ingest.model.ParsedRecipeDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRecipeParserTests {

    private final MarkdownRecipeParser parser = new MarkdownRecipeParser();

    @Test
    void parse_shouldExtractBasicFieldsAndSections() {
        String markdown = """
                # 清蒸鲈鱼的做法

                这是一道非常适合新手的家常菜。

                ## 必备原料和工具
                - 鲈鱼
                - 姜

                ## 操作
                - 处理食材
                - 上锅蒸制

                ## 附加内容
                - 注意火候
                """;

        ParsedRecipeDocument document = parser.parse("dishes/aquatic/清蒸鲈鱼/清蒸鲈鱼.md", markdown);

        assertThat(document.name()).isEqualTo("清蒸鲈鱼");
        assertThat(document.category()).isEqualTo("aquatic");
        assertThat(document.summary()).contains("家常菜");
        assertThat(document.sections()).hasSize(3);
        assertThat(document.sections().get(0).sectionType()).isEqualTo("ingredients");
        assertThat(document.sections().get(1).sectionType()).isEqualTo("steps");
        assertThat(document.sections().get(2).sectionType()).isEqualTo("tips");
    }
}
