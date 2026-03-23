package cn.ss.cookagent.orchestrator.tool;

import cn.ss.cookagent.rag.service.SearchService;
import cn.ss.cookagent.recipe.domain.Recipe;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class QueryIntentTool implements IntentTool {

    private final SearchService searchService;

    public QueryIntentTool(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String name() {
        return "query-tool";
    }

    @Override
    public boolean supports(String intent) {
        return "qa".equalsIgnoreCase(intent) || "search".equalsIgnoreCase(intent);
    }

    @Override
    public ToolExecutionResult execute(String message) {
        String retrievalQuery = buildRetrievalQuery(message);
        List<SearchService.SearchHit> rawHits = searchService.searchHitsForChat(retrievalQuery, 6);
        List<SearchService.SearchHit> rerankedHits = rerankByKeywords(retrievalQuery, rawHits);
        return new ToolExecutionResult(name(), rerankedHits);
    }

    String buildRetrievalQuery(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        String normalized = message
                .replace('？', ' ')
                .replace('?', ' ')
                .replace('。', ' ')
                .replace('，', ' ')
                .replace(',', ' ')
                .replace('！', ' ')
                .replace('!', ' ')
                .trim();

        String preCleaned = normalized
                .replace("请问", "")
                .replace("请教", "")
                .replace("给我", "")
                .replace("一下", "")
                .replace("一个", "")
                .replace("做什么", "")
                .trim();

        String lower = preCleaned.toLowerCase(Locale.ROOT);
        String[] splitHints = new String[]{"怎么做", "做法", "怎么弄", "如何做", "怎么烧", "怎么炒", "怎么煮", "教程"};
        for (String hint : splitHints) {
            int idx = lower.indexOf(hint);
            if (idx > 0) {
                String prefix = preCleaned.substring(0, idx).trim();
                if (!prefix.isBlank()) {
                    return prefix;
                }
            }
        }

        return preCleaned.isBlank() ? normalized : preCleaned;
    }

    private List<SearchService.SearchHit> rerankByKeywords(String query, List<SearchService.SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        List<String> keywords = extractKeywords(query);
        List<String> mandatoryKeywords = extractMandatoryKeywords(query);
        if (keywords.isEmpty()) {
            return hits;
        }

        List<ScoredHit> scoredHits = hits.stream()
            .map(hit -> new ScoredHit(
                hit,
                hitKeywordScore(hit, keywords),
                hitKeywordScore(hit, mandatoryKeywords)
            ))
            .sorted((left, right) -> {
                int mandatoryCompare = Integer.compare(right.mandatoryKeywordScore(), left.mandatoryKeywordScore());
                if (mandatoryCompare != 0) {
                    return mandatoryCompare;
                }
                int keywordCompare = Integer.compare(right.keywordScore(), left.keywordScore());
                if (keywordCompare != 0) {
                    return keywordCompare;
                }
                return Double.compare(right.hit().score(), left.hit().score());
            })
                .toList();

        if (!mandatoryKeywords.isEmpty()) {
            boolean hasMandatoryMatch = scoredHits.stream().anyMatch(scored -> scored.mandatoryKeywordScore() > 0);
            if (!hasMandatoryMatch) {
            return List.of();
            }
        }

        boolean hasMatchedHit = scoredHits.stream().anyMatch(scored -> scored.keywordScore() > 0);
        List<ScoredHit> filtered = hasMatchedHit
            ? scoredHits.stream().filter(scored -> scored.keywordScore() > 0).toList()
            : scoredHits;

        return filtered.stream().map(ScoredHit::hit).limit(6).toList();
    }

    private int hitKeywordScore(SearchService.SearchHit hit, List<String> keywords) {
        Recipe recipe = hit.recipe();
        if (recipe == null) {
            return 0;
        }
        String text = buildRecipeSearchText(recipe);
        int score = 0;
        for (String keyword : keywords) {
            if (keyword.isBlank()) {
                continue;
            }
            if (text.contains(keyword)) {
                score += 1;
            }
            if (recipe.name() != null && recipe.name().contains(keyword)) {
                score += 2;
            }
        }
        return score;
    }

    private String buildRecipeSearchText(Recipe recipe) {
        String ingredients = recipe.ingredients() == null
                ? ""
                : recipe.ingredients().stream().map(ingredient -> ingredient.name() == null ? "" : ingredient.name())
                .reduce((left, right) -> left + " " + right)
                .orElse("");

        return String.join(" ",
                safeLower(recipe.name()),
                safeLower(recipe.summary()),
                safeLower(recipe.category()),
                safeLower(ingredients));
    }

    private List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = query.toLowerCase(Locale.ROOT).replace(" ", "").trim();
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(normalized);

        String[] parts = normalized.split("[炒煮炖蒸烤拌焖炸煎卤炝熬焗]");
        for (String part : parts) {
            if (!part.isBlank()) {
                keywords.add(part);
            }
        }

        List<String> stopWords = List.of("怎么", "如何", "做", "做法", "教程", "推荐", "一下", "请问", "给我", "可以");
        List<String> result = new ArrayList<>();
        for (String word : keywords) {
            String cleaned = word;
            for (String stopWord : stopWords) {
                cleaned = cleaned.replace(stopWord, "");
            }
            cleaned = cleaned.trim();
            if (!cleaned.isBlank()) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private List<String> extractMandatoryKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = query.toLowerCase(Locale.ROOT).replace(" ", "").trim();
        String[] parts = normalized.split("[炒煮炖蒸烤拌焖炸煎卤炝熬焗]");

        List<String> candidates = new ArrayList<>();
        for (String part : parts) {
            String cleaned = part
                    .replace("怎么", "")
                    .replace("如何", "")
                    .replace("做", "")
                    .replace("做法", "")
                    .replace("教程", "")
                    .trim();
            if (cleaned.length() >= 2) {
                candidates.add(cleaned);
            }
        }

        if (!candidates.isEmpty()) {
            return candidates;
        }
        String fallback = normalized
                .replace("怎么", "")
                .replace("如何", "")
                .replace("做", "")
                .replace("做法", "")
                .replace("教程", "")
                .trim();
        if (fallback.length() >= 2) {
            return List.of(fallback);
        }
        return List.of();
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record ScoredHit(SearchService.SearchHit hit, int keywordScore, int mandatoryKeywordScore) {
    }
}
