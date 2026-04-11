package com.familyhub.dto.request.pet;

import com.familyhub.entity.enums.PetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdatePetRequest(

        @NotBlank(message = "Pet name is required")
        @Size(max = 80, message = "Pet name must be at most 80 characters")
        String name,

        @NotNull(message = "Pet type is required")
        PetType type,

        LocalDate dateOfBirth
) {}
