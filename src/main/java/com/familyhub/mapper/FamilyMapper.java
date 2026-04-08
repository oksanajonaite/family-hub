package com.familyhub.mapper;

import com.familyhub.dto.response.family.FamilyResponse;
import com.familyhub.entity.Family;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FamilyMapper {

    @Mapping(target = "id", source = "family.id")
    @Mapping(target = "name", source = "family.name")
    @Mapping(target = "memberCount", source = "memberCount")
    FamilyResponse toResponse(Family family, Integer memberCount);
}
