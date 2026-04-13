package com.economato.inventory.infrastructure.config.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ai.rate-limit")
public class AiRateLimitProperties {

    @NotNull
    @Min(1)
    private Integer messagesPerMinute = 10;

    @NotNull
    @Min(1)
    private Integer maxChatsPerUser = 50;

    @NotNull
    @Min(1)
    private Integer maxMessagesPerChat = 500;

    @NotNull
    @Min(1)
    private Integer maxApiKeysPerUser = 5;

    private Boolean failOpen = true;
}