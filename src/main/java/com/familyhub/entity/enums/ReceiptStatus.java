package com.familyhub.entity.enums;

public enum ReceiptStatus {
    /** Gemini API call is in progress — image has been uploaded to S3. */
    PROCESSING,
    /** Parsing succeeded — all receipt items have been extracted and categorized. */
    DONE,
    /** Parsing failed after one retry — user is shown an error and can re-upload. */
    FAILED
}
