package com.economato.inventory.application.usecase.smg.shared;

import com.economato.inventory.application.usecase.smg.model.shared.EntityMemory;
import com.economato.inventory.infrastructure.config.ai.ai.AiSmgProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ToolResultCompressor {

    private final AiSmgProperties aiSmgProperties;
    private final ObjectMapper objectMapper;

    public String compress(String toolResult, EntityMemory entityMemory) {
        if (toolResult == null || toolResult.isBlank()) {
            return "";
        }

        try {
            JsonNode node = objectMapper.readTree(toolResult);
            JsonNode cleaned = clean(node);
            String json = objectMapper.writeValueAsString(cleaned);
            if (json.length() > aiSmgProperties.getToolResultMaxChars()) {
                return json.substring(0, aiSmgProperties.getToolResultMaxChars()) + "...(truncated)";
            }
            return json;
        } catch (Exception ex) {
            if (toolResult.length() > aiSmgProperties.getToolResultMaxChars()) {
                return toolResult.substring(0, aiSmgProperties.getToolResultMaxChars()) + "...(truncated)";
            }
            return toolResult;
        }
    }

    private JsonNode clean(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isObject()) {
            ObjectNode objectNode = ((ObjectNode) node).deepCopy();
            List<String> fieldNames = new ArrayList<>();
            Iterator<String> namesIterator = objectNode.fieldNames();
            while (namesIterator.hasNext()) {
                fieldNames.add(namesIterator.next());
            }
            for (String fieldName : fieldNames) {
                JsonNode child = clean(objectNode.get(fieldName));
                if (isEmpty(child)) {
                    objectNode.remove(fieldName);
                } else {
                    objectNode.set(fieldName, child);
                }
            }
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            int size = node.size();
            int limit = size > 5 ? 3 : size;
            for (int i = 0; i < limit; i++) {
                JsonNode child = clean(node.get(i));
                if (!isEmpty(child)) {
                    arrayNode.add(child);
                }
            }
            if (size > 5) {
                arrayNode.add("...+" + (size - 3) + " more");
            }
            return arrayNode;
        }
        return node;
    }

    private boolean isEmpty(JsonNode node) {
        if (node == null || node.isNull()) {
            return true;
        }
        if (node.isTextual()) {
            return node.asText().isBlank();
        }
        if (node.isArray()) {
            return node.isEmpty();
        }
        if (node.isObject()) {
            return node.isEmpty();
        }
        return false;
    }
}
