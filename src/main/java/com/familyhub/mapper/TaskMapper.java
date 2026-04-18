package com.familyhub.mapper;

import com.familyhub.dto.request.task.CreateTaskRequest;
import com.familyhub.dto.request.task.UpdateTaskRequest;
import com.familyhub.entity.TaskItem;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TaskMapper {

    // CREATE: maps request fields to a new TaskItem entity.
    // assigneeIds is a prefixed string list — ignored here, parsed in TaskService.applyAssignees()
    @BeanMapping(ignoreUnmappedSourceProperties = {"assigneeIds"})
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "status", expression = "java(com.familyhub.entity.enums.TaskStatus.TODO)")
    @Mapping(target = "assignedUsers", ignore = true)
    @Mapping(target = "assignedMembers", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    TaskItem toEntity(CreateTaskRequest request);

    // UPDATE: applies changed fields onto an existing TaskItem (null values are ignored)
    @BeanMapping(
            ignoreUnmappedSourceProperties = {"assigneeIds"},
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "family", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "assignedUsers", ignore = true)
    @Mapping(target = "assignedMembers", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    void updateEntity(UpdateTaskRequest request, @MappingTarget TaskItem task);
}
