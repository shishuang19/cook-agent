package cn.ss.cookagent.orchestrator.tool;

import cn.ss.cookagent.rag.service.SearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryIntentToolTests {

    @Test
    void execute_shouldUseDishNameAsRetrievalQuery() {
        SearchService searchService = mock(SearchService.class);
        when(searchService.searchHitsForChat("番茄炒蛋", 6)).thenReturn(List.of());

        QueryIntentTool tool = new QueryIntentTool(searchService);
        tool.execute("番茄炒蛋怎么做");

        verify(searchService).searchHitsForChat("番茄炒蛋", 6);
    }

    @Test
    void execute_shouldRemoveCommonFillerWords() {
        SearchService searchService = mock(SearchService.class);
        when(searchService.searchHitsForChat("鸡肉", 6)).thenReturn(List.of());

        QueryIntentTool tool = new QueryIntentTool(searchService);
        tool.execute("请问鸡肉做法");

        verify(searchService).searchHitsForChat("鸡肉", 6);
    }

    @Test
    void execute_shouldFallbackToNormalizedSentenceWhenNoHintMatched() {
        SearchService searchService = mock(SearchService.class);
        when(searchService.searchHitsForChat("清淡 晚餐 推荐", 6)).thenReturn(List.of());

        QueryIntentTool tool = new QueryIntentTool(searchService);
        tool.execute("清淡，晚餐？推荐！");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(searchService).searchHitsForChat(captor.capture(), org.mockito.ArgumentMatchers.eq(6));
        assertThat(captor.getValue()).isEqualTo("清淡 晚餐 推荐");
    }
}
