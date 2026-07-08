package com.economato.inventory.application.usecase.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiIntentDispatcher {

    public void dispatchIntent(String intent, Object context) {
        log.info("Dispatching intent: {} with context: {}", intent, context);
        // Skeleton for future command execution based on LLM intent detection
    }
}
