package com.familyhub.mapper;

import com.familyhub.dto.request.pet.CreatePetRequest;
import com.familyhub.dto.request.pet.UpdatePetRequest;
import com.familyhub.entity.Pet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PetMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "photoUrl", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Pet toEntity(CreatePetRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "photoUrl", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdatePetRequest request, @MappingTarget Pet pet);
}
