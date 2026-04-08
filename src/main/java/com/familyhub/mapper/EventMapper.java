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

    @BeanMapping(ignoreUnmappedSourceProperties = {"participantUserIds", "participantPetIds"})
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Event toEntity(CreateEventRequest request);

    @BeanMapping(
            ignoreUnmappedSourceProperties = {"participantUserIds", "participantPetIds"},
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdateEventRequest request, @MappingTarget Event event);

    @Mapping(target = "createdByUserId", expression = "java(event.getCreatedBy() == null ? null : event.getCreatedBy().getId())")
    @Mapping(target = "participantUserIds", source = "participantUserIds")
    @Mapping(target = "participantPetIds", source = "participantPetIds")
    EventResponse toResponse(Event event, List<Long> participantUserIds, List<Long> participantPetIds);
}
