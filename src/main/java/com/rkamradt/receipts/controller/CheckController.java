package com.rkamradt.receipts.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkamradt.receipts.model.ConfirmResult;
import com.rkamradt.receipts.model.ExtractedCheck;
import com.rkamradt.receipts.model.ExtractedChecks;
import com.rkamradt.receipts.service.CheckAnthropicService;
import com.rkamradt.receipts.service.CheckGoogleDriveSheetService;
import com.rkamradt.receipts.service.ImageProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/check")
public class CheckController {

    private static final Logger log = LoggerFactory.getLogger(CheckController.class);

    private final ImageProcessingService imageService;
    private final CheckAnthropicService checkAnthropicService;
    private final CheckGoogleDriveSheetService checkSheetsService;
    private final OAuth2AuthorizedClientService clientService;
    private final ObjectMapper mapper;

    public CheckController(ImageProcessingService imageService,
                           CheckAnthropicService checkAnthropicService,
                           CheckGoogleDriveSheetService checkSheetsService,
                           OAuth2AuthorizedClientService clientService,
                           ObjectMapper mapper) {
        this.imageService = imageService;
        this.checkAnthropicService = checkAnthropicService;
        this.checkSheetsService = checkSheetsService;
        this.clientService = clientService;
        this.mapper = mapper;
    }

    /**
     * Accepts a check image, processes it, sends to Anthropic, returns all
     * checks found in the image as structured JSON.
     */
    @PostMapping("/extract")
    public ResponseEntity<ExtractedChecks> extract(
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws Exception {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("check/extract: file={} size={} user={}",
            file.getOriginalFilename(), file.getSize(), auth.getName());

        byte[] processed = imageService.process(file.getBytes(), file.getContentType());
        ExtractedChecks result = checkAnthropicService.extract(processed);
        return ResponseEntity.ok(result);
    }

    /**
     * Uploads the original image to Drive once and appends one row per check
     * to the checks spreadsheet. Uses the signed-in user's own OAuth token.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ConfirmResult> confirm(
            @RequestParam("file") MultipartFile originalFile,
            @RequestParam("data") String dataJson,
            Authentication auth) throws Exception {

        if (originalFile.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<ExtractedCheck> checks = mapper.readValue(dataJson,
            new TypeReference<List<ExtractedCheck>>() {});

        String accessToken = getGoogleAccessToken(auth);

        log.info("check/confirm: file={} checks={} user={}",
            originalFile.getOriginalFilename(), checks.size(), auth.getName());

        String driveUrl = checkSheetsService.uploadAndAppend(
            originalFile.getBytes(),
            originalFile.getOriginalFilename(),
            checks,
            accessToken
        );

        return ResponseEntity.ok(new ConfirmResult(driveUrl));
    }

    private String getGoogleAccessToken(Authentication auth) {
        OAuth2AuthorizedClient client =
            clientService.loadAuthorizedClient("google", auth.getName());
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("Google access token not available — please sign in again");
        }
        var expiry = client.getAccessToken().getExpiresAt();
        if (expiry != null && expiry.isBefore(java.time.Instant.now())) {
            throw new IllegalStateException("Google access token expired — please sign in again");
        }
        return client.getAccessToken().getTokenValue();
    }
}
