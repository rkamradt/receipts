package com.rkamradt.receipts.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.rkamradt.receipts.model.ExtractedReceipt;
import com.rkamradt.receipts.model.ReceiptItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Uploads the original receipt image to Google Drive and appends a row to
 * the configured Google Sheet. Uses the signed-in user's own OAuth access
 * token so writes are attributed to whoever is logged in.
 */
@Service
public class GoogleDriveSheetService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveSheetService.class);
    private static final String APP_NAME = "receipts.kamradtfamily.net";

    @Value("${receipts.google.sheet-id}")
    private String sheetId;

    @Value("${receipts.google.sheet-tab}")
    private String sheetTab;

    @Value("${receipts.google.drive-folder-id}")
    private String driveFolderId;

    /**
     * Uploads the original image to Drive, then appends a row to the sheet.
     *
     * @param imageBytes    original (not downscaled) image bytes
     * @param originalName  original filename (for MIME type detection)
     * @param receipt       reviewed/confirmed receipt data
     * @param accessToken   the signed-in user's Google OAuth2 access token
     * @return the Drive web view link for the uploaded file
     */
    public String uploadAndAppend(byte[] imageBytes, String originalName,
                                  ExtractedReceipt receipt, String accessToken)
            throws IOException, GeneralSecurityException {

        HttpRequestInitializer credentials = credentialsAdapter(accessToken);

        // 1. Upload image to Drive
        String driveUrl = uploadToDrive(imageBytes, originalName, receipt, credentials);

        // 2. Append row to Sheet
        appendToSheet(receipt, driveUrl, credentials);

        return driveUrl;
    }

    private String uploadToDrive(byte[] imageBytes, String originalName,
                                  ExtractedReceipt receipt, HttpRequestInitializer creds)
            throws IOException, GeneralSecurityException {

        Drive drive = new Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            creds)
            .setApplicationName(APP_NAME)
            .build();

        String date = receipt.date() != null ? receipt.date() : "unknown-date";
        String total = receipt.total() != null ? String.format("%.2f", receipt.total()) : "0.00";
        String shortId = UUID.randomUUID().toString().substring(0, 6);
        String ext = guessExtension(originalName);
        String filename = date + "_" + total + "_" + shortId + ext;

        File meta = new File();
        meta.setName(filename);
        meta.setParents(List.of(driveFolderId));

        String mimeType = guessMimeType(originalName);
        ByteArrayContent content = new ByteArrayContent(mimeType, imageBytes);

        File created = drive.files().create(meta, content)
            .setFields("id,webViewLink")
            .execute();

        log.info("Uploaded to Drive: {} → {}", filename, created.getWebViewLink());
        return created.getWebViewLink();
    }

    private void appendToSheet(ExtractedReceipt receipt, String driveUrl,
                                HttpRequestInitializer creds)
            throws IOException, GeneralSecurityException {

        Sheets sheets = new Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            creds)
            .setApplicationName(APP_NAME)
            .build();

        String date = receipt.date() != null ? receipt.date() : "";
        String itemsCell = receipt.items().stream()
            .map(i -> i.description() + " " +
                (i.amount() != null ? String.format("%.2f", i.amount()) : ""))
            .collect(Collectors.joining("; "));
        String totalCell = receipt.total() != null ? String.valueOf(receipt.total()) : "";
        // =HYPERLINK formula so the cell is clickable in Sheets
        String receiptCell = "=HYPERLINK(\"" + driveUrl + "\",\"view\")";

        ValueRange body = new ValueRange()
            .setValues(List.of(List.of(date, itemsCell, totalCell, receiptCell)));

        sheets.spreadsheets().values()
            .append(sheetId, sheetTab + "!A:D", body)
            .setValueInputOption("USER_ENTERED")
            .execute();

        log.info("Appended row to sheet {} tab {}", sheetId, sheetTab);
    }

    private HttpRequestInitializer credentialsAdapter(String token) {
        GoogleCredentials creds = GoogleCredentials.create(new AccessToken(token, null));
        return new HttpCredentialsAdapter(creds);
    }

    private String guessExtension(String filename) {
        if (filename == null) return ".jpg";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return ".png";
        if (lower.endsWith(".webp")) return ".webp";
        if (lower.endsWith(".heic")) return ".jpg"; // converted upstream
        return ".jpg";
    }

    private String guessMimeType(String filename) {
        if (filename == null) return "image/jpeg";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
