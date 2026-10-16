package com.rkamradt.receipts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkamradt.receipts.model.ConfirmResult;
import com.rkamradt.receipts.model.ExtractedReceipt;
import com.rkamradt.receipts.model.UserInfo;
import com.rkamradt.receipts.service.AnthropicService;
import com.rkamradt.receipts.service.GoogleDriveSheetService;
import com.rkamradt.receipts.service.ImageProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api")
public class ReceiptController {

    private static final Logger log = LoggerFactory.getLogger(ReceiptController.class);

    private final ImageProcessingService imageService;
    private final AnthropicService anthropicService;
    private final GoogleDriveSheetService sheetsService;
    private final OAuth2AuthorizedClientService clientService;
    private final ObjectMapper mapper;

    public ReceiptController(ImageProcessingService imageService,
                             AnthropicService anthropicService,
                             GoogleDriveSheetService sheetsService,
                             OAuth2AuthorizedClientService clientService,
                             ObjectMapper mapper) {
        this.imageService = imageService;
        this.anthropicService = anthropicService;
        this.sheetsService = sheetsService;
        this.clientService = clientService;
        this.mapper = mapper;
    }

    /**
     * Returns the signed-in user's profile, or {signedIn:false} if not logged in.
     * The frontend calls this on load to determine which screen to show.
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfo> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.ok(new UserInfo(false, null, null, null));
        }
        if (auth.getPrincipal() instanceof OidcUser user) {
            return ResponseEntity.ok(new UserInfo(true, user.getFullName(),
                user.getEmail(), user.getPicture()));
        }
        return ResponseEntity.ok(new UserInfo(true, auth.getName(), null, null));
    }

    /**
     * Accepts a receipt image, processes it (EXIF fix + resize), sends to
     * Anthropic for extraction, returns structured JSON.
     */
    @PostMapping("/extract")
    public ResponseEntity<ExtractedReceipt> extract(
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws Exception {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("extract: file={} size={} user={}",
            file.getOriginalFilename(), file.getSize(), auth.getName());

        byte[] processed = imageService.process(file.getBytes(), file.getContentType());
        ExtractedReceipt result = anthropicService.extract(processed);
        return ResponseEntity.ok(result);
    }

    /**
     * Uploads the original image to Google Drive and appends a row to the sheet.
     * Uses the signed-in user's own OAuth token so writes are attributed to them.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ConfirmResult> confirm(
            @RequestParam("file") MultipartFile originalFile,
            @RequestParam("data") String dataJson,
            Authentication auth) throws Exception {

        if (originalFile.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ExtractedReceipt data = mapper.readValue(dataJson, ExtractedReceipt.class);

        // Retrieve this user's Google access token from the session
        String accessToken = getGoogleAccessToken(auth);

        log.info("confirm: file={} date={} total={} user={}",
            originalFile.getOriginalFilename(), data.date(), data.total(), auth.getName());

        String driveUrl = sheetsService.uploadAndAppend(
            originalFile.getBytes(),
            originalFile.getOriginalFilename(),
            data,
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
