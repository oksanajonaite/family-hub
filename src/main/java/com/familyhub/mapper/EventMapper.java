package com.familyhub.mapper;

import com.familyhub.dto.request.event.CreateEventRequest;
import com.familyhub.dto.request.event.UpdateEventRequest;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.Event;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EventMapper {

    // Request → Entity (CREATE)
    // participantUserIds, participantPetIds, participantFamilyMemberIds —
    // tai ID sąrašai, ne entity laukai. Service'as juos tvarko atskirai (EventParticipant lentelė).
    @BeanMapping(ignoreUnmappedSourceProperties = {
            "participantUserIds", "participantPetIds", "participantFamilyMemberIds"
    })
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Event toEntity(CreateEventRequest request);

    // Request → Entity (UPDATE) — modifikuoja esamą objektą
    @BeanMapping(
            ignoreUnmappedSourceProperties = {
                    "participantUserIds", "participantPetIds", "participantFamilyMemberIds"
            },
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdateEventRequest request, @MappingTarget Event event);

    // Entity → Response — priima dalyvių ID sąrašus kaip papildomus parametrus,
    // nes EventParticipant yra atskira lentelė, ne Event entity laukas
    @Mapping(target = "createdByUserId",
            expression = "java(event.getCreatedBy() == null ? null : event.getCreatedBy().getId())")
    @Mapping(target = "participantUserIds", source = "participantUserIds")
    @Mapping(target = "participantPetIds", source = "participantPetIds")
    @Mapping(target = "participantFamilyMemberIds", source = "participantFamilyMemberIds")
    EventResponse toResponse(
            Event event,
            List<Long> participantUserIds,
            List<Long> participantPetIds,
            List<Long> participantFamilyMemberIds
    );
}
