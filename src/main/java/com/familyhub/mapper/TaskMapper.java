package com.familyhub.mapper;

import com.familyhub.dto.request.task.CreateTaskRequest;
import com.familyhub.dto.request.task.UpdateTaskRequest;
import com.familyhub.dto.response.task.TaskResponse;
import com.familyhub.entity.TaskItem;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    // CREATE: Request → naujas TaskItem entity
    // assignedToUserId yra Long (ID), bet entity lauke yra User objektas.
    // Todėl ignoruojame šį lauką mapper'yje — service'as pats suranda User pagal ID
    @BeanMapping(ignoreUnmappedSourceProperties = "assignedToUserId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    // expression — tiesioginis Java kodas mapper'yje.
    // Naujos užduoties statusas visada TODO, nepriklausomai nuo to ką siuntė request'as
    @Mapping(target = "status", expression = "java(com.familyhub.entity.enums.TaskStatus.TODO)")
    @Mapping(target = "assignedTo", ignore = true) // service'as priskiria vartotoją
    @Mapping(target = "createdBy", ignore = true)  // service'as priskiria kūrėją
    @Mapping(target = "createdAt", ignore = true)  // @CreationTimestamp užpildo Hibernate
    @Mapping(target = "completedAt", ignore = true) // null kol nebaigta
    TaskItem toEntity(CreateTaskRequest request);

    // UPDATE: Request → modifikuojamas esamas TaskItem
    // Skirtumas nuo CREATE: nekeičiame status, assignedTo, createdBy, completedAt —
    // juos valdo service'as atskirai (pvz. status keičiamas per atskirą endpoint'ą)
    @BeanMapping(
            ignoreUnmappedSourceProperties = "assignedToUserId",
            // IGNORE — null reikšmės iš request'o NERAŠOMOS į entity.
            // Leidžia siųsti tik norimus keisti laukus.
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "status", ignore = true)      // status keičiamas per atskirą endpoint'ą
    @Mapping(target = "assignedTo", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    // @MappingTarget — reiškia kad modifikuojame esamą objektą, ne kuriame naują.
    // void grąžinimo tipas — pakeitimai daromi tiesiogiai ant task objekto.
    void updateEntity(UpdateTaskRequest request, @MappingTarget TaskItem task);

    // Entity → Response DTO konvertavimas
    // Problema: TaskResponse turi assignedToUserId (Long) ir assignedToDisplayName (String),
    // bet TaskItem turi assignedTo (User objektas, gali būti null).
    // expression = "java(...)" — ternary operatorius: null check → paima id arba grąžina null
    @Mapping(target = "assignedToUserId",
            expression = "java(task.getAssignedTo() == null ? null : task.getAssignedTo().getId())")
    @Mapping(target = "assignedToDisplayName",
            expression = "java(task.getAssignedTo() == null ? null : task.getAssignedTo().getDisplayName())")
    @Mapping(target = "createdByUserId",
            expression = "java(task.getCreatedBy() == null ? null : task.getCreatedBy().getId())")
    TaskResponse toResponse(TaskItem task);
}
