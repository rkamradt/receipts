package com.rkamradt.receipts.model;

import java.util.List;

public record ExtractedReceipt(
    String date,                  // "YYYY-MM-DD" or null
    List<ReceiptItem> items,
    Double total,
    String confidenceNotes
) {}
