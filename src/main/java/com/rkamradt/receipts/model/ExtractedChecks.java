package com.rkamradt.receipts.model;

import java.util.List;

public record ExtractedChecks(
    List<ExtractedCheck> checks,
    String confidenceNotes
) {}
