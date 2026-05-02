package com.familyhub.service;

import com.familyhub.dto.gemini.GeminiReceiptResult;
import com.familyhub.entity.Receipt;
import com.familyhub.entity.enums.ReceiptStatus;
import com.familyhub.exception.GeminiParsingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptParsingServiceTest {

    @Mock private GeminiClient geminiClient;
    @InjectMocks private ReceiptParsingService parsingService;

    // ── Pagalbiniai metodai ───────────────────────────────────────────────────

    private MultipartFile mockFile() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(file.getContentType()).thenReturn("image/jpeg");
        return file;
    }

    private GeminiReceiptResult emptyResult() {
        return new GeminiReceiptResult(null, null, null, List.of());
    }

    private GeminiReceiptResult.Item item(String name, String price) {
        return new GeminiReceiptResult.Item(name, BigDecimal.ONE,
                new BigDecimal(price), "FOOD_OTHER");
    }

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Jei visi puslapiai neparsinti (GeminiParsingException kiekvienam) —
    // kvito statusas turi būti FAILED, ne DONE ar PROCESSING.
    @Test
    void parseAndPopulate_whenAllPagesFail_setsStatusFailed() throws Exception {
        MultipartFile file = mockFile();
        when(geminiClient.parseReceipt(any(), anyString()))
                .thenThrow(new GeminiParsingException("API error", null));

        Receipt receipt = Receipt.builder().build();
        parsingService.parseAndPopulate(receipt, List.of(file));

        assertEquals(ReceiptStatus.FAILED, receipt.getStatus());
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Jei bent vienas puslapis sėkmingai parsinti — statusas DONE.
    // Realus atvejis: 2 puslapių kvitas, vienas neparsinti — vis tiek DONE.
    @Test
    void parseAndPopulate_whenAtLeastOnePageSucceeds_setsStatusDone() throws Exception {
        MultipartFile page1 = mockFile();
        MultipartFile page2 = mockFile();

        when(geminiClient.parseReceipt(any(), anyString()))
                .thenThrow(new GeminiParsingException("fail", null))  // page1 nepavyko
                .thenReturn(emptyResult());                             // page2 pavyko

        Receipt receipt = Receipt.builder().build();
        parsingService.parseAndPopulate(receipt, List.of(page1, page2));

        assertEquals(ReceiptStatus.DONE, receipt.getStatus());
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // vendorName strategija: pirmasis ne-null laimi.
    // Pirmo puslapio viršus turi tinkamą parduotuvės pavadinimą.
    @Test
    void parseAndPopulate_vendorName_takesFirstNonNull() throws Exception {
        MultipartFile p1 = mockFile();
        MultipartFile p2 = mockFile();

        when(geminiClient.parseReceipt(any(), anyString()))
                .thenReturn(new GeminiReceiptResult("MAXIMA", null, null, List.of()))
                .thenReturn(new GeminiReceiptResult("IKI", null, null, List.of()));

        Receipt receipt = Receipt.builder().build();
        parsingService.parseAndPopulate(receipt, List.of(p1, p2));

        assertEquals("MAXIMA", receipt.getVendorName());
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // totalAmount strategija: paskutinis ne-null laimi.
    // Bendra suma visada yra paskutiniame kvito puslapyje.
    @Test
    void parseAndPopulate_totalAmount_takesLastNonNull() throws Exception {
        MultipartFile p1 = mockFile();
        MultipartFile p2 = mockFile();

        when(geminiClient.parseReceipt(any(), anyString()))
                .thenReturn(new GeminiReceiptResult(null, null, new BigDecimal("10.00"), List.of()))
                .thenReturn(new GeminiReceiptResult(null, null, new BigDecimal("25.50"), List.of()));

        Receipt receipt = Receipt.builder().build();
        parsingService.parseAndPopulate(receipt, List.of(p1, p2));

        assertEquals(new BigDecimal("25.50"), receipt.getTotalAmount());
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    // Items strategija: union iš visų puslapių.
    // 2 prekės iš 1-o puslapio + 1 iš 2-o = 3 iš viso.
    @Test
    void parseAndPopulate_items_mergesFromAllPages() throws Exception {
        MultipartFile p1 = mockFile();
        MultipartFile p2 = mockFile();

        when(geminiClient.parseReceipt(any(), anyString()))
                .thenReturn(new GeminiReceiptResult(null, null, null,
                        List.of(item("Milk", "1.20"), item("Bread", "0.90"))))
                .thenReturn(new GeminiReceiptResult(null, null, null,
                        List.of(item("Eggs", "2.50"))));

        Receipt receipt = Receipt.builder().build();
        parsingService.parseAndPopulate(receipt, List.of(p1, p2));

        assertEquals(3, receipt.getItems().size());
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────
    // Items filtravimas: prekės be pavadinimo arba be kainos ignoruojamos.
    // Gemini kartais grąžina neišbaigtas eilutes — jos neturėtų patekti į DB.
    @Test
    void parseAndPopulate_items_filtersOutItemsWithNullNameOrPrice() throws Exception {
        MultipartFile file = mockFile();

        GeminiReceiptResult.Item validItem   = item("Milk", "1.20");
        GeminiReceiptResult.Item noName      = new GeminiReceiptResult.Item(null, BigDecimal.ONE, new BigDecimal("1.00"), "OTHER");
        GeminiReceiptResult.Item noPrice     = new GeminiReceiptResult.Item("Bread", BigDecimal.ONE, null, "OTHER");

        when(geminiClient.parseReceipt(any(), anyString()))
                .thenReturn(new GeminiReceiptResult(null, null, null,
                        List.of(validItem, noName, noPrice)));

        Receipt receipt = Receipt.builder().build();
        parsingService.parseAndPopulate(receipt, List.of(file));

        // Tik validItem turi ir pavadinimą, ir kainą
        assertEquals(1, receipt.getItems().size());
        assertEquals("Milk", receipt.getItems().get(0).getProductName());
    }

    // ── Test 7 ────────────────────────────────────────────────────────────────
    // Neteisinga data iš Gemini (ne YYYY-MM-DD formatas) → purchaseDate = today.
    // Kvitas išsaugomas DONE ir patenka į įkėlimo dienos išlaidų mėnesį.
    @Test
    void parseAndPopulate_whenGeminiReturnsInvalidDate_purchaseDateFallsBackToToday() throws Exception {
        MultipartFile file = mockFile();

        when(geminiClient.parseReceipt(any(), anyString()))
                .thenReturn(new GeminiReceiptResult(null, "not-a-date", null, List.of()));

        Receipt receipt = Receipt.builder().build();
        parsingService.parseAndPopulate(receipt, List.of(file));

        assertEquals(LocalDate.now(), receipt.getPurchaseDate());
        assertEquals(ReceiptStatus.DONE, receipt.getStatus());
    }
}
