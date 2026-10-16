package com.rkamradt.receipts.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkamradt.receipts.model.ExtractedReceipt;
import com.rkamradt.receipts.model.ReceiptItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Calls the Anthropic Messages API with tool_use to extract structured data
 * from a receipt image. The model is forced to call the extract_receipt tool
 * so the response is always well-formed JSON.
 */
@Service
public class AnthropicService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicService.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private static final String PROMPT =
        "You are reading a receipt photo. The receipt may be wrinkled, faded, skewed, " +
        "or partially cut off — read as carefully as possible. " +
        "Never invent values: use null when a field is unreadable. " +
        "The total is the final amount paid (after tax and discounts). " +
        "Call the extract_receipt tool with everything you can read.";

    @Value("${receipts.anthropic.api-key}")
    private String apiKey;

    @Value("${receipts.anthropic.model}")
    private String model;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public ExtractedReceipt extract(byte[] jpegBytes) {
        String b64 = Base64.getEncoder().encodeToString(jpegBytes);

        Map<String, Object> request = Map.of(
            "model", model,
            "max_tokens", 1024,
            "tools", List.of(toolDefinition()),
            "tool_choice", Map.of("type", "tool", "name", "extract_receipt"),
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of(
                        "type", "image",
                        "source", Map.of(
                            "type", "base64",
                            "media_type", "image/jpeg",
                            "data", b64
                        )
                    ),
                    Map.of("type", "text", "text", PROMPT)
                )
            ))
        );

        String responseBody = restClient.post()
            .uri(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(request)
            .retrieve()
            .body(String.class);

        return parseToolUseResponse(responseBody);
    }

    private ExtractedReceipt parseToolUseResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode content = root.path("content");
            for (JsonNode block : content) {
                if ("tool_use".equals(block.path("type").asText())
                        && "extract_receipt".equals(block.path("name").asText())) {
                    JsonNode input = block.path("input");
                    String date = input.path("date").isNull() ? null : input.path("date").asText(null);
                    Double total = input.path("total").isNull() ? null : input.path("total").asDouble();
                    String notes = input.path("confidence_notes").asText("");

                    List<ReceiptItem> items = new ArrayList<>();
                    for (JsonNode item : input.path("items")) {
                        String desc = item.path("description").asText("");
                        Double amt = item.path("amount").isNull() ? null : item.path("amount").asDouble();
                        items.add(new ReceiptItem(desc, amt));
                    }
                    return new ExtractedReceipt(date, items, total, notes);
                }
            }
            log.warn("No tool_use block in Anthropic response");
            return new ExtractedReceipt(null, List.of(), null,
                "Extraction failed — no structured output returned");
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response", e);
            throw new RuntimeException("Failed to parse extraction response", e);
        }
    }

    private Map<String, Object> toolDefinition() {
        return Map.of(
            "name", "extract_receipt",
            "description", "Extract structured data from a receipt image",
            "input_schema", Map.of(
                "type", "object",
                "properties", Map.of(
                    "date", Map.of(
                        "type", List.of("string", "null"),
                        "description", "Date on the receipt in YYYY-MM-DD format, or null if unreadable"
                    ),
                    "items", Map.of(
                        "type", "array",
                        "description", "Line items on the receipt",
                        "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                "description", Map.of("type", "string", "description", "Item description"),
                                "amount", Map.of(
                                    "type", List.of("number", "null"),
                                    "description", "Item amount in dollars, or null if unreadable"
                                )
                            ),
                            "required", List.of("description", "amount")
                        )
                    ),
                    "total", Map.of(
                        "type", List.of("number", "null"),
                        "description", "Final amount paid (after tax/discounts), or null if unreadable"
                    ),
                    "confidence_notes", Map.of(
                        "type", "string",
                        "description", "Any caveats about readability or uncertain values"
                    )
                ),
                "required", List.of("date", "items", "total", "confidence_notes")
            )
        );
    }
}
