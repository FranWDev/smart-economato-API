package com.economato.inventory.infrastructure.config.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ai.chat")
public class AiChatProperties {

    @NotBlank
    private String defaultProvider = "OPENAI";

    @NotBlank
    private String defaultLanguage = "es";

    private List<String> supportedLanguages = new ArrayList<>(
            List.of("es", "en", "fr", "de", "it", "pt", "ca", "eu", "gl")
    );

    @NotNull
    @Min(1)
    private Integer titleMaxLength = 200;

    @NotNull
    @Min(1)
    private Integer maxConcurrentStreamsPerUser = 2;

    private Boolean autoArchiveOnLimit = true;
}