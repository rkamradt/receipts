package com.rkamradt.receipts.model;

public record ExtractedCheck(
    String date,           // "YYYY-MM-DD" or null
    String checkNumber,    // check number or null
    String payee,          // "Pay to the order of" name or null
    Double amount,         // dollar amount or null
    String memo,           // memo line or null
    String confidenceNotes
) {}
