package cn.ss.cookagent.api;

import cn.ss.cookagent.api.response.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.llm.mock-enabled=true",
        "app.llm.enabled=true",
        "spring.ai.openai.api-key=test-key"
})
@AutoConfigureMockMvc
class ApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthCheck_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void sessionApi_shouldCreateAndGetSession() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/session")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("""
                                {
                                  "userId": "u_test_1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andReturn();

        JsonNode createBody = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String sessionId = createBody.path("data").path("sessionId").asText();

        mockMvc.perform(get("/api/session/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.messages").isArray());
    }

    @Test
    void searchChatAndRecipeApi_shouldWorkAsMainFlow() throws Exception {
        MvcResult searchResult = mockMvc.perform(post("/api/search")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("""
                                {
                                  "query": "",
                                  "pageNo": 1,
                                  "pageSize": 5,
                                  "sortBy": "relevance"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.items").isArray())
                .andReturn();

        JsonNode searchBody = objectMapper.readTree(searchResult.getResponse().getContentAsString());
        JsonNode firstItem = searchBody.path("data").path("items").path(0);
        long recipeId = firstItem.path("recipeId").asLong();
        assertThat(recipeId).isGreaterThan(0);

        MvcResult createSessionResult = mockMvc.perform(post("/api/session")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("""
                                {
                                  "userId": "u_test_2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();

        String sessionId = objectMapper.readTree(createSessionResult.getResponse().getContentAsString())
                .path("data")
                .path("sessionId")
                .asText();

        MvcResult chatResult = mockMvc.perform(post("/api/chat")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "sessionId": "%s",
                                  "userId": "u_test_2",
                                  "message": "想做咖喱炒蟹，给我做法",
                                  "stream": false,
                                  "context": {
                                    "page": "qa"
                                  }
                                }
                                """.formatted(sessionId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.answer").isNotEmpty())
                .andExpect(jsonPath("$.data.citations").isArray())
                .andExpect(jsonPath("$.data.followups").isArray())
                .andReturn();

        JsonNode chatBody = objectMapper.readTree(chatResult.getResponse().getContentAsString());
        JsonNode citations = chatBody.path("data").path("citations");
                JsonNode followups = chatBody.path("data").path("followups");
        assertThat(citations.isArray()).isTrue();
                assertThat(followups.isArray()).isTrue();
                assertThat(followups.size()).isBetween(1, 3);
                followups.forEach(node -> assertThat(node.asText()).isNotBlank());
        if (!citations.isEmpty()) {
            JsonNode firstCitation = citations.get(0);
            assertThat(firstCitation.path("recipeId").asLong()).isGreaterThan(0);
            assertThat(firstCitation.path("chunkId").asText()).isNotBlank();
            assertThat(firstCitation.path("sectionType").asText()).isNotBlank();
            assertThat(firstCitation.path("score").asDouble()).isGreaterThan(0D);
            assertThat(firstCitation.path("hitSource").asText()).isIn("chunk", "metadata");
        }

        mockMvc.perform(get("/api/recipes/{recipeId}", recipeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.recipeId").value(recipeId))
                .andExpect(jsonPath("$.data.name").isNotEmpty())
                .andExpect(jsonPath("$.data.steps").isArray());
    }

    @Test
    void chatResponseSerialization_shouldContainHitSourceField() throws Exception {
        ChatResponse response = new ChatResponse(
                "s_test",
                "qa",
                "answer",
                List.of(new ChatResponse.CitationItem(101L, "咖喱炒蟹", "101_summary_1_0", "summary", 0.93D, "chunk")),
                List.of("followup"),
                new ChatResponse.DebugInfo("qa.hybrid", 1)
        );

        JsonNode body = objectMapper.readTree(objectMapper.writeValueAsString(response));
        JsonNode citation = body.path("citations").get(0);
        assertThat(citation.path("hitSource").asText()).isEqualTo("chunk");
    }

    @Test
    void ingestApi_shouldRunInDryRunMode() throws Exception {
        mockMvc.perform(post("/api/admin/ingest/recipes")
                        .param("limit", "5")
                        .param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.scannedFiles").isNumber())
                .andExpect(jsonPath("$.data.parsedRecipes").isNumber());
    }

    @Test
    void chatApi_shouldKeepMultiTurnSessionState() throws Exception {
        MvcResult createSessionResult = mockMvc.perform(post("/api/session")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("""
                                {
                                  "userId": "u_multi_turn"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();

        String sessionId = objectMapper.readTree(createSessionResult.getResponse().getContentAsString())
                .path("data")
                .path("sessionId")
                .asText();

        mockMvc.perform(post("/api/chat")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "userId": "u_multi_turn",
                                  "message": "我不吃辣，想吃鸡肉",
                                  "stream": false,
                                  "context": {
                                    "page": "qa"
                                  }
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mockMvc.perform(post("/api/chat")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "userId": "u_multi_turn",
                                  "message": "再给我一个 20 分钟内能做完的",
                                  "stream": false,
                                  "context": {
                                    "page": "qa"
                                  }
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        MvcResult detailResult = mockMvc.perform(get("/api/session/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.messages.length()").value(4))
                .andExpect(jsonPath("$.data.messages[0].role").value("user"))
                .andExpect(jsonPath("$.data.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.data.messages[2].role").value("user"))
                .andExpect(jsonPath("$.data.messages[3].role").value("assistant"))
                .andReturn();

        JsonNode detailBody = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        String rollingSummary = detailBody.path("data").path("rollingSummary").asText();
                assertThat(rollingSummary).contains("mock-model");
                assertThat(rollingSummary).contains("20");
    }

                @Test
                void chatApi_shouldRouteToRecommendIntentWhenAskingWhatToEat() throws Exception {
                                MvcResult createSessionResult = mockMvc.perform(post("/api/session")
                                                                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                                    .content("""
                                                                        {
                                                                                "userId": "u_intent_recommend"
                                                                        }
                                                                        """))
                                                                    .andExpect(status().isOk())
                                                                    .andExpect(jsonPath("$.code").value("0"))
                                                                    .andReturn();

                                String sessionId = objectMapper.readTree(createSessionResult.getResponse().getContentAsString())
                                                                .path("data")
                                                                .path("sessionId")
                                                                .asText();

                                mockMvc.perform(post("/api/chat")
                                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                        .content("""
                                            {
                                                    "sessionId": "%s",
                                                    "userId": "u_intent_recommend",
                                                    "message": "今天吃什么",
                                                    "stream": false,
                                                    "context": {
                                                            "page": "qa"
                                                    }
                                            }
                                            """.formatted(sessionId)))
                                                                .andExpect(status().isOk())
                                                                .andExpect(jsonPath("$.code").value("0"))
                                                                .andExpect(jsonPath("$.data.intent").value("recommend"))
                                                                .andExpect(jsonPath("$.data.debug.route").value("recommend.recommend-tool"));
                }

                @Test
                void chatApi_shouldRouteToQueryIntentWhenAskingHowToCook() throws Exception {
                                MvcResult createSessionResult = mockMvc.perform(post("/api/session")
                                                                                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "userId": "u_intent_query"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk())
                                                                .andExpect(jsonPath("$.code").value("0"))
                                                                .andReturn();

                                String sessionId = objectMapper.readTree(createSessionResult.getResponse().getContentAsString())
                                                                .path("data")
                                                                .path("sessionId")
                                                                .asText();

                                mockMvc.perform(post("/api/chat")
                                                                                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "sessionId": "%s",
                                                                                                                                        "userId": "u_intent_query",
                                                                                                                                        "message": "番茄炒蛋怎么做",
                                                                                                                                        "stream": false,
                                                                                                                                        "context": {
                                                                                                                                                "page": "qa"
                                                                                                                                        }
                                                                                                                                }
                                                                                                                                """.formatted(sessionId)))
                                                                .andExpect(status().isOk())
                                                                .andExpect(jsonPath("$.code").value("0"))
                                                                .andExpect(jsonPath("$.data.intent").value("qa"))
                                                                .andExpect(jsonPath("$.data.debug.route").value("qa.query-tool"));
                }
}