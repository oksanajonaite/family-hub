package com.familyhub.dto.response;

import java.time.LocalDate;

// Represents a birthday entry for display in the calendar and Today+Tomorrow widget.
// Used for both registered users (User.dateOfBirth) and account-less family members (FamilyMember.dateOfBirth).
// dateOfBirth stores the original birth year, but only month+day are used for matching.
public record BirthdayEntry(String name, LocalDate dateOfBirth) {}
