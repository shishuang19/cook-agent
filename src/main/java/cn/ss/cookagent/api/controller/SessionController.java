package cn.ss.cookagent.api.controller;

import cn.ss.cookagent.api.request.SessionCreateRequest;
import cn.ss.cookagent.api.response.SessionCreateResponse;
import cn.ss.cookagent.api.response.SessionDetailResponse;
import cn.ss.cookagent.common.response.ApiResponse;
import cn.ss.cookagent.memory.model.UserSession;
import cn.ss.cookagent.memory.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/session")
    public ApiResponse<SessionCreateResponse> createSession(@Valid @RequestBody SessionCreateRequest request) {
        UserSession session = sessionService.createSession(request.userId());
        return ApiResponse.success(new SessionCreateResponse(session.getSessionId()));
    }

    @GetMapping("/session/{sessionId}")
    public ApiResponse<SessionDetailResponse> getSession(@PathVariable String sessionId) {
        UserSession session = sessionService.getSession(sessionId);
        SessionDetailResponse response = new SessionDetailResponse(
                session.getSessionId(),
                session.getRollingSummary(),
                session.getMessages().stream()
                        .map(message -> new SessionDetailResponse.MessageItem(message.role(), message.content()))
                        .toList()
        );
        return ApiResponse.success(response);
    }
}
