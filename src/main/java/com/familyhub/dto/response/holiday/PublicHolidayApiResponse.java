package com.familyhub.dto.response.holiday;

import java.time.LocalDate;
import java.util.List;

public record PublicHolidayApiResponse(
        LocalDate date,
        String localName,
        String name,
        List<String> types
) {}
