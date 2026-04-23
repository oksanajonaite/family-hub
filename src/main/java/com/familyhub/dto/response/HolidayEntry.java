package com.familyhub.dto.response;

import java.time.LocalDate;

public record HolidayEntry(
        LocalDate date,
        String localName,
        String name
) {}
