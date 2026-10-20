package com.rkamradt.receipts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkamradt.receipts.model.ExtractedCheck;
import com.rkamradt.receipts.model.ExtractedChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Calls the Anthropic Messages API with tool_use to extract all checks from a
 * single image. One image may contain multiple checks; the tool always returns
 * an array so the caller doesn't need to handle the single-check case specially.
 */
@Service
public class CheckAnthropicService {

    private static final Logger log = LoggerFactory.getLogger(CheckAnthropicService.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private static final String PROMPT =
        "You are reading an image that may contain one or more personal or business checks. " +
        "Identify every check visible in the image and extract the information from each one. " +
        "Never invent values: use null when a field is unreadable or absent. " +
        "Call the extract_checks tool with all checks you can find.";

    @Value("${receipts.anthropic.api-key}")
    private String apiKey;

    @Value("${receipts.anthropic.model}")
    private String model;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public ExtractedChecks extract(byte[] jpegBytes) {
        String b64 = Base64.getEncoder().encodeToString(jpegBytes);

        Map<String, Object> request = Map.of(
            "model", model,
            "max_tokens", 1024,
            "tools", List.of(toolDefinition()),
            "tool_choice", Map.of("type", "tool", "name", "extract_checks"),
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

    private ExtractedChecks parseToolUseResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode content = root.path("content");
            for (JsonNode block : content) {
                if ("tool_use".equals(block.path("type").asText())
                        && "extract_checks".equals(block.path("name").asText())) {
                    JsonNode input = block.path("input");
                    String overallNotes = input.path("confidence_notes").asText("");

                    List<ExtractedCheck> checks = new ArrayList<>();
                    for (JsonNode c : input.path("checks")) {
                        checks.add(new ExtractedCheck(
                            c.path("date").isNull() ? null : c.path("date").asText(null),
                            c.path("check_number").isNull() ? null : c.path("check_number").asText(null),
                            c.path("payee").isNull() ? null : c.path("payee").asText(null),
                            c.path("amount").isNull() ? null : c.path("amount").asDouble(),
                            c.path("memo").isNull() ? null : c.path("memo").asText(null),
                            c.path("confidence_notes").asText("")
                        ));
                    }
                    return new ExtractedChecks(checks, overallNotes);
                }
            }
            log.warn("No tool_use block in Anthropic response for checks");
            return new ExtractedChecks(List.of(),
                "Extraction failed — no structured output returned");
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response for checks", e);
            throw new RuntimeException("Failed to parse check extraction response", e);
        }
    }

    private Map<String, Object> toolDefinition() {
        Map<String, Object> checkItemSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                "date", Map.of(
                    "type", List.of("string", "null"),
                    "description", "Date on the check in YYYY-MM-DD format, or null if unreadable"
                ),
                "check_number", Map.of(
                    "type", List.of("string", "null"),
                    "description", "Check number printed on the check, or null if absent"
                ),
                "payee", Map.of(
                    "type", List.of("string", "null"),
                    "description", "Name on the 'Pay to the order of' line, or null if unreadable"
                ),
                "amount", Map.of(
                    "type", List.of("number", "null"),
                    "description", "Dollar amount of the check, or null if unreadable"
                ),
                "memo", Map.of(
                    "type", List.of("string", "null"),
                    "description", "Text on the memo line, or null if blank or unreadable"
                ),
                "confidence_notes", Map.of(
                    "type", "string",
                    "description", "Any caveats about readability or uncertain values for this check"
                )
            ),
            "required", List.of("date", "check_number", "payee", "amount", "memo", "confidence_notes")
        );

        return Map.of(
            "name", "extract_checks",
            "description", "Extract structured data from all checks visible in an image",
            "input_schema", Map.of(
                "type", "object",
                "properties", Map.of(
                    "checks", Map.of(
                        "type", "array",
                        "description", "All checks found in the image",
                        "items", checkItemSchema
                    ),
                    "confidence_notes", Map.of(
                        "type", "string",
                        "description", "Overall notes about image quality or issues affecting multiple checks"
                    )
                ),
                "required", List.of("checks", "confidence_notes")
            )
        );
    }
}
