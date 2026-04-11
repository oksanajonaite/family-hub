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

// MapStruct — kodo generatorius. Compile metu sugeneruoja EventMapperImpl klasę
// su visais konvertavimo metodais. Nereikia rašyti rankiniu būdu.
// componentModel = "spring" — sugeneruota klasė bus Spring @Component (injectable)
// unmappedTargetPolicy = ERROR — kompiliacija nepavyks jei mapper'yje yra
// laukas kuris nesuporinti. Apsauga nuo "pamirštų" laukų.
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EventMapper {

    // Request → Entity konvertavimas (CREATE)
    // @BeanMapping(ignoreUnmappedSourceProperties) — request'e yra laukai
    // kurių nėra entity (participantUserIds, participantPetIds) — jie tvarkomis atskirai service'e
    @BeanMapping(ignoreUnmappedSourceProperties = {"participantUserIds", "participantPetIds"})
    // @Mapping(target = "id", ignore = true) — id generuoja DB, ne mes
    @Mapping(target = "id", ignore = true)
    // family, createdBy, createdAt — užpildo service'as rankiniu būdu po mapper'io
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Event toEntity(CreateEventRequest request);

    // Request → Entity konvertavimas (UPDATE) — modifikuoja esamą objektą
    // @MappingTarget — nurodo kad updateriname esamą objektą, ne kuriame naują
    @BeanMapping(
            ignoreUnmappedSourceProperties = {"participantUserIds", "participantPetIds"},
            // IGNORE — jei request lauke yra null, NERAŠOME null į esamą entity.
            // Tai leidžia daryti "partial update" — keisti tik siųstus laukus.
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdateEventRequest request, @MappingTarget Event event);

    // Entity → Response konvertavimas
    // Šis mapper'is nestandartinis — priima papildomus parametrus (dalyvių sąrašai),
    // nes EventParticipant yra atskira lentelė, ne Event laukas.
    // expression = "java(...)" — MapStruct leidžia rašyti Java kodą kai automatas nepakanka.
    // Čia: jei createdBy null (teoriškai negalimas) — grąžiname null, kitaip id.
    @Mapping(target = "createdByUserId", expression = "java(event.getCreatedBy() == null ? null : event.getCreatedBy().getId())")
    @Mapping(target = "participantUserIds", source = "participantUserIds")
    @Mapping(target = "participantPetIds", source = "participantPetIds")
    EventResponse toResponse(Event event, List<Long> participantUserIds, List<Long> participantPetIds);
}
