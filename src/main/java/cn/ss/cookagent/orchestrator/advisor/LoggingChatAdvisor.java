package cn.ss.cookagent.orchestrator.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(200)
public class LoggingChatAdvisor implements ChatAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatAdvisor.class);

    @Override
    public AdvisorInput beforeCall(AdvisorInput input) {
        log.info(
                "LLM beforeCall model={} userMsgLen={} contextLen={} preview={} ",
                input.model(),
                safeLen(input.userMessage()),
                safeLen(input.context()),
                preview(input.userMessage())
        );
        return input;
    }

    @Override
    public void afterCall(AdvisorInput input, String response, long elapsedMs) {
        log.info(
                "LLM afterCall model={} elapsedMs={} responseLen={} ",
                input.model(),
                elapsedMs,
                safeLen(response)
        );
    }

    @Override
    public void onError(AdvisorInput input, Exception ex, long elapsedMs) {
        log.warn(
                "LLM onError model={} elapsedMs={} errorType={} errorMsg={} preview={}",
                input.model(),
                elapsedMs,
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                preview(input.userMessage())
        );
    }

    private static int safeLen(String value) {
        return value == null ? 0 : value.length();
    }

    private static String preview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\n', ' ').trim();
        return normalized.length() <= 60 ? normalized : normalized.substring(0, 60) + "...";
    }
}
