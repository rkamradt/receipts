package com.rkamradt.receipts;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "GOOGLE_CLIENT_ID=test-client-id",
    "GOOGLE_CLIENT_SECRET=test-client-secret",
    "ANTHROPIC_API_KEY=test-key",
    "SHEET_ID=test-sheet-id",
    "DRIVE_FOLDER_ID=test-folder-id"
})
class ReceiptsApplicationTests {
    @Test
    void contextLoads() {}
}
