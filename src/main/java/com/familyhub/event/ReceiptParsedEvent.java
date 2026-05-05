package com.familyhub.event;

import org.springframework.context.ApplicationEvent;

// Observer pattern — decouples ReceiptService from SpendingService cache management
//ReceiptParsedEvent klausosi SpendingService. Kai čekis apdorojamas, ReceiptService paskelbia event’ą,
// o SpendingService per @EventListener sureaguoja ir išvalo išlaidų cache, kad statistika būtų perskaičiuota su nauju čekiu.
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
