package com.familyhub.service;

import com.familyhub.dto.response.holiday.HolidayEntry;
import com.familyhub.dto.response.holiday.PublicHolidayApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Service
@Slf4j
public class PublicHolidayService {

    private static final String COUNTRY_CODE = "LT";
    private static final String BASE_URL = "https://date.nager.at/api/v3";

    private final RestClient restClient;

    public PublicHolidayService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    public List<HolidayEntry> getLithuanianHolidaysBetween(LocalDate start, LocalDate end) {
        // The visible calendar grid can span two different years (for example December -> January),
        // so we load each required year separately and then keep only holidays inside the requested range.
        return IntStream.rangeClosed(start.getYear(), end.getYear())
                .mapToObj(this::getLithuanianPublicHolidays)
                .flatMap(List::stream)
                .filter(holiday -> !holiday.date().isBefore(start) && !holiday.date().isAfter(end))
                .sorted(Comparator.comparing(HolidayEntry::date))
                .toList();
    }

    @Cacheable(cacheNames = "publicHolidaysByYear", key = "#year")
    public List<HolidayEntry> getLithuanianPublicHolidays(int year) {
        try {
            // Nager.Date is used as a read-only reference source for Lithuanian public holidays.
            // We store only the fields needed by the dashboard instead of persisting these holidays in our DB.
            PublicHolidayApiResponse[] response = restClient.get()
                    .uri("/PublicHolidays/{year}/{countryCode}", year, COUNTRY_CODE)
                    .retrieve()
                    .body(PublicHolidayApiResponse[].class);

            if (response == null) {
                return List.of();
            }

            return List.of(response).stream()
                    // Keep only actual public holidays; other API types (observance, school, optional, etc.)
                    // should not appear in the shared family calendar.
                    .filter(holiday -> holiday.types() == null || holiday.types().isEmpty() || holiday.types().contains("Public"))
                    .map(holiday -> new HolidayEntry(
                            holiday.date(),
                            holiday.localName(),
                            holiday.name()
                    ))
                    .sorted(Comparator.comparing(HolidayEntry::date))
                    .toList();
        } catch (RestClientException ex) {
            // External data should enhance the dashboard, not break it.
            // If the API is unavailable, we simply render the calendar without holidays.
            log.warn("Failed to fetch Lithuanian public holidays for year {}. Dashboard will continue without them.", year, ex);
            return List.of();
        }
    }
}
