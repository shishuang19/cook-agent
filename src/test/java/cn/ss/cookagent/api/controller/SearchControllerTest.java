package cn.ss.cookagent.api.controller;

import cn.ss.cookagent.rag.service.SearchService;
import cn.ss.cookagent.recipe.domain.Recipe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SearchService searchService;

    @Test
    void search_shouldSortByCookTimeAscAndApplyPagination() throws Exception {
        Recipe quick = recipe(1L, "快手菜", 10);
        Recipe medium = recipe(2L, "中等菜", 20);
        Recipe slow = recipe(3L, "慢炖菜", 30);

        given(searchService.search(any())).willReturn(List.of(slow, quick, medium));
        given(searchService.score(any(), any())).willReturn(0.6D);

        mockMvc.perform(post("/api/search")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(new SearchBody("", 1, 2, "time_asc")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pageNo").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.items[0].recipeId").value(1))
                .andExpect(jsonPath("$.data.items[1].recipeId").value(2));
    }

    @Test
    void search_shouldSortByScoreDesc() throws Exception {
        Recipe a = recipe(11L, "A", 30);
        Recipe b = recipe(12L, "B", 30);
        Recipe c = recipe(13L, "C", 30);

        given(searchService.search(any())).willReturn(List.of(a, b, c));
        given(searchService.score(eq(a), eq("鱼"))).willReturn(0.2D);
        given(searchService.score(eq(b), eq("鱼"))).willReturn(0.9D);
        given(searchService.score(eq(c), eq("鱼"))).willReturn(0.5D);

        mockMvc.perform(post("/api/search")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(new SearchBody("鱼", 1, 3, "score_desc")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.items[0].recipeId").value(12))
                .andExpect(jsonPath("$.data.items[1].recipeId").value(13))
                .andExpect(jsonPath("$.data.items[2].recipeId").value(11));
    }

    private static Recipe recipe(long id, String name, int time) {
        return new Recipe(
                id,
                name,
                "test",
                "beginner",
                time,
                "summary",
                List.of("tag"),
                List.of(),
                List.of(),
                List.of(),
                "source.md"
        );
    }

    private record SearchBody(String query, Integer pageNo, Integer pageSize, String sortBy) {
    }
}
