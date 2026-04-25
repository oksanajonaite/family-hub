package com.familyhub.service;

import com.familyhub.dto.response.holiday.HolidayEntry;
import com.familyhub.dto.response.holiday.PublicHolidayApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicHolidayServiceTest {

    @Mock private RestClient.Builder restClientBuilder;
    @Mock private RestClient restClient;
    @SuppressWarnings("rawtypes")
    @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private PublicHolidayService publicHolidayService;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        publicHolidayService = new PublicHolidayService(restClientBuilder);
    }

    // Tikrina, kad iš išorinio API neimtume visų galimų "holiday" tipų aklai.
    // Mums dashboard kalendoriuje reikia tik realių valstybinių / public holiday įrašų,
    // todėl "Observance" tipo dienos neturi būti rodomos.
    @Test
    void getLithuanianPublicHolidays_filtersOutNonPublicTypes() {
        PublicHolidayApiResponse[] apiResponse = new PublicHolidayApiResponse[] {
                new PublicHolidayApiResponse(LocalDate.of(2026, 1, 1), "Naujieji metai", "New Year's Day", List.of("Public")),
                new PublicHolidayApiResponse(LocalDate.of(2026, 5, 3), "Motinos diena", "Mother's Day", List.of("Observance")),
                new PublicHolidayApiResponse(LocalDate.of(2026, 6, 7), "Tėvo diena", "Father's Day", null)
        };

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/PublicHolidays/{year}/{countryCode}", 2026, "LT"))
                .thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(PublicHolidayApiResponse[].class)).thenReturn(apiResponse);

        List<HolidayEntry> result = publicHolidayService.getLithuanianPublicHolidays(2026);

        assertEquals(2, result.size());
        assertEquals("Naujieji metai", result.get(0).localName());
        assertEquals("Tėvo diena", result.get(1).localName());
    }

    // Fail-safe testas: jei išorinis API neveikia, dashboard neturi nulūžti.
    // Vietoj exception grąžiname tuščią sąrašą ir kalendorius tiesiog rodomas be švenčių.
    @Test
    void getLithuanianPublicHolidays_whenApiFails_returnsEmptyList() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/PublicHolidays/{year}/{countryCode}", 2026, "LT"))
                .thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(PublicHolidayApiResponse[].class)).thenThrow(new RestClientException("API unavailable"));

        List<HolidayEntry> result = publicHolidayService.getLithuanianPublicHolidays(2026);

        assertTrue(result.isEmpty());
    }

    // Kalendoriaus grid'as mėnesio pradžioje / pabaigoje gali apimti dvi skirtingas metų ribas.
    // Šis testas tikrina, kad servisas moka paimti abiejų metų šventes
    // ir po to palieka tik tas, kurios patenka į prašomą intervalą.
    @Test
    void getLithuanianHolidaysBetween_combinesYearsAndFiltersRequestedRange() {
        PublicHolidayService spyService = spy(publicHolidayService);

        doReturn(List.of(
                new HolidayEntry(LocalDate.of(2025, 12, 24), "Kūčios", "Christmas Eve"),
                new HolidayEntry(LocalDate.of(2025, 12, 31), "Naujųjų metų išvakarės", "New Year's Eve")
        )).when(spyService).getLithuanianPublicHolidays(2025);

        doReturn(List.of(
                new HolidayEntry(LocalDate.of(2026, 1, 1), "Naujieji metai", "New Year's Day"),
                new HolidayEntry(LocalDate.of(2026, 1, 6), "Trys karaliai", "Epiphany")
        )).when(spyService).getLithuanianPublicHolidays(2026);

        List<HolidayEntry> result = spyService.getLithuanianHolidaysBetween(
                LocalDate.of(2025, 12, 29),
                LocalDate.of(2026, 1, 3)
        );

        assertEquals(2, result.size());
        assertEquals(LocalDate.of(2025, 12, 31), result.get(0).date());
        assertEquals(LocalDate.of(2026, 1, 1), result.get(1).date());
    }
}
