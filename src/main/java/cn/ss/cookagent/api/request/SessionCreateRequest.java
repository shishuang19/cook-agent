package cn.ss.cookagent.api.request;

import jakarta.validation.constraints.NotBlank;

public record SessionCreateRequest(
        @NotBlank(message = "userId 不能为空") String userId
) {
}
