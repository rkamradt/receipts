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
import com.rkamradt.receipts.model.ExtractedCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uploads a check image to Google Drive (once per image) and appends one row
 * per check to the configured checks spreadsheet.
 */
@Service
public class CheckGoogleDriveSheetService {

    private static final Logger log = LoggerFactory.getLogger(CheckGoogleDriveSheetService.class);
    private static final String APP_NAME = "receipts.kamradtfamily.net";

    @Value("${receipts.google.check-sheet-id}")
    private String checkSheetId;

    @Value("${receipts.google.check-sheet-tab}")
    private String checkSheetTab;

    @Value("${receipts.google.drive-folder-id}")
    private String driveFolderId;

    /**
     * Uploads the image to Drive once, then appends one row per check.
     *
     * @param imageBytes   original image bytes
     * @param originalName original filename (for MIME type detection)
     * @param checks       reviewed/confirmed list of checks from this image
     * @param accessToken  the signed-in user's Google OAuth2 access token
     * @return the Drive web view link for the uploaded image
     */
    public String uploadAndAppend(byte[] imageBytes, String originalName,
                                  List<ExtractedCheck> checks, String accessToken)
            throws IOException, GeneralSecurityException {

        HttpRequestInitializer credentials = credentialsAdapter(accessToken);

        // Use first check's date/amount for the filename, fall back to "unknown"
        String date = checks.stream()
            .map(ExtractedCheck::date)
            .filter(d -> d != null)
            .findFirst()
            .orElse("unknown-date");
        double total = checks.stream()
            .mapToDouble(c -> c.amount() != null ? c.amount() : 0.0)
            .sum();
        String totalStr = String.format("%.2f", total);

        String driveUrl = uploadToDrive(imageBytes, originalName, date, totalStr, credentials);

        for (ExtractedCheck check : checks) {
            appendToSheet(check, driveUrl, credentials);
        }

        return driveUrl;
    }

    private String uploadToDrive(byte[] imageBytes, String originalName,
                                  String date, String total, HttpRequestInitializer creds)
            throws IOException, GeneralSecurityException {

        Drive drive = new Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            creds)
            .setApplicationName(APP_NAME)
            .build();

        String shortId = UUID.randomUUID().toString().substring(0, 6);
        String ext = guessExtension(originalName);
        String filename = "check_" + date + "_" + total + "_" + shortId + ext;

        File meta = new File();
        meta.setName(filename);
        meta.setParents(List.of(driveFolderId));

        String mimeType = guessMimeType(originalName);
        ByteArrayContent content = new ByteArrayContent(mimeType, imageBytes);

        File created = drive.files().create(meta, content)
            .setFields("id,webViewLink")
            .execute();

        log.info("Uploaded check image to Drive: {} → {}", filename, created.getWebViewLink());
        return created.getWebViewLink();
    }

    private void appendToSheet(ExtractedCheck check, String driveUrl,
                                HttpRequestInitializer creds)
            throws IOException, GeneralSecurityException {

        Sheets sheets = new Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            creds)
            .setApplicationName(APP_NAME)
            .build();

        String date        = check.date()        != null ? check.date()        : "";
        String checkNumber = check.checkNumber()  != null ? check.checkNumber() : "";
        String payee       = check.payee()        != null ? check.payee()       : "";
        String amount      = check.amount()       != null ? String.valueOf(check.amount()) : "";
        String memo        = check.memo()         != null ? check.memo()        : "";
        String imageCell   = "=HYPERLINK(\"" + driveUrl + "\",\"view\")";

        List<Object> row = new ArrayList<>();
        row.add(date);
        row.add(checkNumber);
        row.add(payee);
        row.add(amount);
        row.add(memo);
        row.add(imageCell);

        ValueRange body = new ValueRange().setValues(List.of(row));

        sheets.spreadsheets().values()
            .append(checkSheetId, checkSheetTab + "!A:F", body)
            .setValueInputOption("USER_ENTERED")
            .execute();

        log.info("Appended check row to sheet {} tab {} (check# {})",
            checkSheetId, checkSheetTab, checkNumber);
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
        if (lower.endsWith(".heic")) return ".jpg";
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
