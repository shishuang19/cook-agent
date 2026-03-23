package cn.ss.cookagent.api.controller;

import cn.ss.cookagent.api.request.ChatRequest;
import cn.ss.cookagent.api.response.ChatResponse;
import cn.ss.cookagent.common.response.ApiResponse;
import cn.ss.cookagent.orchestrator.facade.ConversationFacade;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ConversationFacade conversationFacade;

    public ChatController(ConversationFacade conversationFacade) {
        this.conversationFacade = conversationFacade;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(conversationFacade.handleChat(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody chatStream(@Valid @RequestBody ChatRequest request) {
        return outputStream -> conversationFacade.streamChat(request, outputStream);
    }
}
