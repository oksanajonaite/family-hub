package com.familyhub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReceiptRateLimiterService testai.
 *
 * Testuojame Bucket4j logika tiesiogiai — be Mockito,
 * nes servisas neturi išorinių priklausomybių.
 * @Value laukai nustatomi rankiniu būdu per @BeforeEach.
 */
class ReceiptRateLimiterServiceTest {

    private ReceiptRateLimiterService rateLimiter;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter = new ReceiptRateLimiterService();

        // Inject @Value fields manually (Spring nėra — grynai unit testas)
        Field capacityField = ReceiptRateLimiterService.class.getDeclaredField("capacity");
        Field refillField   = ReceiptRateLimiterService.class.getDeclaredField("refillHours");
        capacityField.setAccessible(true);
        refillField.setAccessible(true);
        capacityField.set(rateLimiter, 3);
        refillField.set(rateLimiter,   1);

        // Simuliuojame @PostConstruct
        Method init = ReceiptRateLimiterService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(rateLimiter);
    }

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Pirmas bandymas visada leistinas — bucket ką tik sukurtas, visas talpas.
    @Test
    void tryConsume_firstAttempt_returnsTrue() {
        assertTrue(rateLimiter.tryConsume(1L));
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Kai vartotojas pasiekia limitą (3 iš 3 panaudota) — kitas bandymas atmetamas.
    @Test
    void tryConsume_whenLimitExhausted_returnsFalse() {
        rateLimiter.tryConsume(1L); // 1
        rateLimiter.tryConsume(1L); // 2
        rateLimiter.tryConsume(1L); // 3 — limitas pasiektas

        assertFalse(rateLimiter.tryConsume(1L)); // 4 — atmesta
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // Kiekvienas vartotojas turi savo nepriklausomą bucket'ą.
    // Vartotojo 1 limito išnaudojimas neturi įtakos vartotojui 2.
    @Test
    void tryConsume_differentUsers_haveIndependentBuckets() {
        rateLimiter.tryConsume(1L);
        rateLimiter.tryConsume(1L);
        rateLimiter.tryConsume(1L); // user 1 limitą išnaudojo

        assertTrue(rateLimiter.tryConsume(2L)); // user 2 vis dar gali
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Vartotojas gali naudoti visus leidžiamus bandymus (capacity = 3).
    // Skaičiuojame kiek iš eilės grąžina true prieš grąžinant false.
    @Test
    void tryConsume_allowsExactlyCapacityAttempts() {
        int allowed = 0;
        for (int i = 0; i < 5; i++) {
            if (rateLimiter.tryConsume(42L)) allowed++;
        }
        assertEquals(3, allowed); // capacity = 3
    }
}
