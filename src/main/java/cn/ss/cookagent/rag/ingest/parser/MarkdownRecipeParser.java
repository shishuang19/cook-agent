package cn.ss.cookagent.rag.ingest.parser;

import cn.ss.cookagent.rag.ingest.model.ParsedRecipeDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MarkdownRecipeParser {

    private static final String INGEST_FORMAT_VERSION = "chunk-v2";

    public ParsedRecipeDocument parse(Path file, Path cookRoot) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String sourcePath = cookRoot.relativize(file).toString().replace('\\', '/');
        return parse(sourcePath, content);
    }

    public ParsedRecipeDocument parse(String sourcePath, String content) {
        List<String> lines = content.lines().toList();
        String title = extractTitle(lines, sourcePath);
        String category = extractCategory(sourcePath);
        String summary = extractSummary(lines, title);
        List<ParsedRecipeDocument.Section> sections = extractSections(lines);
        String sourceVersion = sha256Hex(INGEST_FORMAT_VERSION + "\n" + content);

        if (sections.isEmpty()) {
            sections = List.of(new ParsedRecipeDocument.Section("intro", "简介", summary, 1));
        }

        String slug = buildSlug(sourcePath, title);
        return new ParsedRecipeDocument(
                slug,
                title,
                category,
                "beginner",
                30,
                summary,
                sourcePath,
                sourceVersion,
                List.of(category, "cook-doc"),
                sections
        );
    }

    private String extractTitle(List<String> lines, String sourcePath) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                String title = trimmed.substring(2).trim();
                if (title.endsWith("的做法")) {
                    return title.substring(0, title.length() - 3).trim();
                }
                return title;
            }
        }
        String fileName = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
        if (fileName.endsWith(".md")) {
            return fileName.substring(0, fileName.length() - 3);
        }
        return fileName;
    }

    private String extractSummary(List<String> lines, String title) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("#") || trimmed.startsWith("![") || trimmed.startsWith("- ")
                    || trimmed.startsWith("* ") || trimmed.matches("\\d+\\..*")) {
                continue;
            }
            return trimmed;
        }
        return "来自食谱文档：" + title;
    }

    private List<ParsedRecipeDocument.Section> extractSections(List<String> lines) {
        List<ParsedRecipeDocument.Section> sections = new ArrayList<>();
        String currentTitle = null;
        String currentType = null;
        StringBuilder contentBuilder = new StringBuilder();
        int sortOrder = 1;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                if (currentTitle != null) {
                    String sectionContent = contentBuilder.toString().trim();
                    if (!sectionContent.isBlank()) {
                        sections.add(new ParsedRecipeDocument.Section(currentType, currentTitle, sectionContent, sortOrder++));
                    }
                }
                currentTitle = trimmed.substring(3).trim();
                currentType = mapSectionType(currentTitle);
                contentBuilder = new StringBuilder();
                continue;
            }

            if (currentTitle != null && !trimmed.isBlank()) {
                if (contentBuilder.length() > 0) {
                    contentBuilder.append('\n');
                }
                contentBuilder.append(trimmed);
            }
        }

        if (currentTitle != null) {
            String sectionContent = contentBuilder.toString().trim();
            if (!sectionContent.isBlank()) {
                sections.add(new ParsedRecipeDocument.Section(currentType, currentTitle, sectionContent, sortOrder));
            }
        }

        return sections;
    }

    private String mapSectionType(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("操作") || lower.contains("步骤")) {
            return "steps";
        }
        if (lower.contains("原料") || lower.contains("食材") || lower.contains("计算")) {
            return "ingredients";
        }
        if (lower.contains("附加") || lower.contains("提示")) {
            return "tips";
        }
        return "intro";
    }

    private String extractCategory(String sourcePath) {
        String normalized = sourcePath.replace('\\', '/');
        String marker = "dishes/";
        int idx = normalized.indexOf(marker);
        if (idx < 0) {
            return "unknown";
        }
        String remain = normalized.substring(idx + marker.length());
        int slash = remain.indexOf('/');
        if (slash < 0) {
            return "unknown";
        }
        return remain.substring(0, slash);
    }

    private String buildSlug(String sourcePath, String title) {
        String normalized = sourcePath.replace('/', '-').replace(".md", "").toLowerCase(Locale.ROOT);
        int hash = Math.abs(sourcePath.hashCode());
        return normalized + "-" + hash + "-" + title.length();
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha256 unavailable", ex);
        }
    }
}
