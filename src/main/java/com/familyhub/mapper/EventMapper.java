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

    // CREATE: maps request fields to a new Event entity.
    // startDate/startTime/endDate/endTime and participantIds are ignored here —
    // startsAt/endsAt are combined and set manually in EventService.createEvent()
    @BeanMapping(ignoreUnmappedSourceProperties = {"participantIds", "startDate", "startTime", "endDate", "endTime"})
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "startsAt", ignore = true)
    @Mapping(target = "endsAt", ignore = true)
    Event toEntity(CreateEventRequest request);

    // UPDATE: applies changed fields onto an existing Event (null values are ignored)
    // Same reason: startsAt/endsAt are set manually in EventService.updateEvent()
    @BeanMapping(
            ignoreUnmappedSourceProperties = {"participantIds", "startDate", "startTime", "endDate", "endTime"},
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "startsAt", ignore = true)
    @Mapping(target = "endsAt", ignore = true)
    void updateEntity(UpdateEventRequest request, @MappingTarget Event event);

    // RESPONSE: participant ID lists are passed as extra parameters because
    // EventParticipant is a separate table, not a field on the Event entity
    @Mapping(target = "createdByUserId",
            expression = "java(event.getCreatedBy() == null ? null : event.getCreatedBy().getId())")
    @Mapping(target = "participantUserIds", source = "participantUserIds")
    @Mapping(target = "participantPetIds", source = "participantPetIds")
    @Mapping(target = "participantFamilyMemberIds", source = "participantFamilyMemberIds")
    @Mapping(target = "participantNames", source = "participantNames")
    EventResponse toResponse(
            Event event,
            List<Long> participantUserIds,
            List<Long> participantPetIds,
            List<Long> participantFamilyMemberIds,
            List<String> participantNames
    );
}
