package com.familyhub.dto.request.pet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdatePetRequest(

        @NotBlank(message = "Pet name is required")
        @Size(max = 80, message = "Pet name must be at most 80 characters")
        String name,

        // Optional free-text type — user can enter any animal (e.g. "Chinchilla", "Parrot")
        @Size(max = 50, message = "Type must be at most 50 characters")
        String type,

        LocalDate dateOfBirth
) {}
