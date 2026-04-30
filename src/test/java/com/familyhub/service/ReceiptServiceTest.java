package com.familyhub.service;

import com.familyhub.entity.Family;
import com.familyhub.entity.Receipt;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.ReceiptStatus;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.exception.RateLimitExceededException;
import com.familyhub.mapper.ReceiptMapper;
import com.familyhub.repository.ReceiptRepository;
import com.familyhub.repository.UserRepository;
import com.familyhub.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// LENIENT: the userDetails() helper stubs getFamilyId() for convenience across all tests,
// but the service never calls currentUser.getFamilyId() — it loads the user from DB instead.
// Similarly, nonEmptyFile() stubs isEmpty() which is unused when an earlier guard throws.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiptServiceTest {

    @Mock private ReceiptRepository       receiptRepository;
    @Mock private UserRepository          userRepository;
    @Mock private ReceiptParsingService   receiptParsingService;
    @Mock private ReceiptRateLimiterService rateLimiterService;
    @Mock private ReceiptMapper           receiptMapper;

    @InjectMocks private ReceiptService receiptService;

    // ── Pagalbiniai metodai ───────────────────────────────────────────────────

    private CustomUserDetails userDetails(Long userId, Long familyId) {
        CustomUserDetails details = mock(CustomUserDetails.class);
        when(details.getId()).thenReturn(userId);
        when(details.getFamilyId()).thenReturn(familyId);
        return details;
    }

    private MultipartFile nonEmptyFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        return file;
    }

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Rate limiter grąžina false — upload turi būti atmestas iš karto,
    // prieš pasiekiant DB. Fail fast principas.
    @Test
    void uploadAndParse_whenRateLimitExceeded_throwsRateLimitException() {
        CustomUserDetails user = userDetails(1L, 10L);
        when(rateLimiterService.tryConsume(1L)).thenReturn(false);

        assertThrows(RateLimitExceededException.class,
                () -> receiptService.uploadAndParse(List.of(nonEmptyFile()), user));

        verifyNoInteractions(userRepository, receiptRepository, receiptParsingService);
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Tuščias failų sąrašas (arba visi failai tušti) → IllegalArgumentException.
    // Naršyklė kartais prideda tuščius MultipartFile slots.
    @Test
    void uploadAndParse_whenNoValidFiles_throwsIllegalArgument() {
        CustomUserDetails user = userDetails(1L, 10L);
        when(rateLimiterService.tryConsume(1L)).thenReturn(true);

        MultipartFile emptyFile = mock(MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> receiptService.uploadAndParse(List.of(emptyFile), user));
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // Daugiau nei 5 nuotraukos → IllegalArgumentException.
    // Vieno kvito limitas — 5 puslapiai.
    @Test
    void uploadAndParse_whenTooManyFiles_throwsIllegalArgument() {
        CustomUserDetails user = userDetails(1L, 10L);
        when(rateLimiterService.tryConsume(1L)).thenReturn(true);

        List<MultipartFile> sixFiles = List.of(
                nonEmptyFile(), nonEmptyFile(), nonEmptyFile(),
                nonEmptyFile(), nonEmptyFile(), nonEmptyFile()
        );

        assertThrows(IllegalArgumentException.class,
                () -> receiptService.uploadAndParse(sixFiles, user));
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Vartotojas be šeimos negali įkelti kvitų.
    // FamilyRequiredInterceptor saugo /receipts/upload, bet servisas taip pat
    // tikrina — gynybinis programavimas (defense in depth).
    @Test
    void uploadAndParse_whenUserHasNoFamily_throwsFamilyNotFoundException() {
        CustomUserDetails userDetails = userDetails(1L, null);
        when(rateLimiterService.tryConsume(1L)).thenReturn(true);

        User user = User.builder().id(1L).family(null).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(FamilyNotFoundException.class,
                () -> receiptService.uploadAndParse(List.of(nonEmptyFile()), userDetails));
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    // Happy path: kvitas sukuriamas su PROCESSING statusu ir išsaugomas DB
    // prieš pradedant parsavimą — vartotojas mato kvitą sąraše iš karto.
    @Test
    void uploadAndParse_savesReceiptWithProcessingStatusBeforeParsing() {
        CustomUserDetails userDetails = userDetails(1L, 10L);
        when(rateLimiterService.tryConsume(1L)).thenReturn(true);

        Family family = Family.builder().id(10L).build();
        User user = User.builder().id(1L).family(family).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(i -> i.getArgument(0));
        when(receiptMapper.toDetailResponse(any())).thenReturn(null);

        receiptService.uploadAndParse(List.of(nonEmptyFile()), userDetails);

        // Kvitas išsaugomas su PROCESSING statusu prieš parsavimą
        verify(receiptRepository).save(argThat(r -> r.getStatus() == ReceiptStatus.PROCESSING));
        verify(receiptParsingService).parseAndPopulate(any(Receipt.class), any());
    }
}
