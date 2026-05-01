package com.familyhub.event;

import org.springframework.context.ApplicationEvent;

// Observer pattern — decouples ReceiptService from SpendingService cache management
public class ReceiptParsedEvent extends ApplicationEvent {

    private final Long familyId;

    public ReceiptParsedEvent(Object source, Long familyId) {
        super(source);
        this.familyId = familyId;
    }

    public Long getFamilyId() {
        return familyId;
    }
}
