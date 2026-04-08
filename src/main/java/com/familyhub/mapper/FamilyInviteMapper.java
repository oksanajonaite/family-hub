package com.familyhub.mapper;

import com.familyhub.dto.response.family.FamilyInviteResponse;
import com.familyhub.entity.FamilyInvite;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FamilyInviteMapper {

    FamilyInviteResponse toResponse(FamilyInvite invite);
}
