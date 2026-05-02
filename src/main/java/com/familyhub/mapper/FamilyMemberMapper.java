package com.familyhub.mapper;

import com.familyhub.dto.request.member.CreateFamilyMemberRequest;
import com.familyhub.dto.request.member.UpdateFamilyMemberRequest;
import com.familyhub.entity.FamilyMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FamilyMemberMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "photoUrl", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    FamilyMember toEntity(CreateFamilyMemberRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "photoUrl", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdateFamilyMemberRequest request, @MappingTarget FamilyMember member);
}
