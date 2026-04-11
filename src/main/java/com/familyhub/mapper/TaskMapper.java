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
    @BeanMapping(ignoreUnmappedSourceProperties = {"assignedToUserId", "assignedToMemberId"})
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "status", expression = "java(com.familyhub.entity.enums.TaskStatus.TODO)")
    @Mapping(target = "assignedTo", ignore = true)         // service'as priskiria User
    @Mapping(target = "assignedToMember", ignore = true)   // service'as priskiria FamilyMember
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    TaskItem toEntity(CreateTaskRequest request);

    // UPDATE: Request → modifikuojamas esamas TaskItem
    @BeanMapping(
            ignoreUnmappedSourceProperties = {"assignedToUserId", "assignedToMemberId"},
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "assignedTo", ignore = true)
    @Mapping(target = "assignedToMember", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    void updateEntity(UpdateTaskRequest request, @MappingTarget TaskItem task);

    // Entity → Response DTO
    // assignedTo (User) → assignedToUserId + assignedToDisplayName
    // assignedToMember (FamilyMember) → assignedToMemberId + assignedToMemberName
    @Mapping(target = "assignedToUserId",
            expression = "java(task.getAssignedTo() == null ? null : task.getAssignedTo().getId())")
    @Mapping(target = "assignedToDisplayName",
            expression = "java(task.getAssignedTo() == null ? null : task.getAssignedTo().getDisplayName())")
    @Mapping(target = "assignedToMemberId",
            expression = "java(task.getAssignedToMember() == null ? null : task.getAssignedToMember().getId())")
    @Mapping(target = "assignedToMemberName",
            expression = "java(task.getAssignedToMember() == null ? null : task.getAssignedToMember().getName())")
    @Mapping(target = "createdByUserId",
            expression = "java(task.getCreatedBy() == null ? null : task.getCreatedBy().getId())")
    TaskResponse toResponse(TaskItem task);
}
