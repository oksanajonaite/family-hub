package com.familyhub.repository;

import com.familyhub.entity.TaskItem;
import com.familyhub.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<TaskItem, Long> {

    List<TaskItem> findAllByFamilyIdOrderByCreatedAtDesc(Long familyId);

    List<TaskItem> findAllByFamilyIdAndStatusOrderByCreatedAtDesc(Long familyId, TaskStatus status);

    // Used by the calendar to find tasks with a due date within the visible date range
    List<TaskItem> findAllByFamilyIdAndDueDateBetween(Long familyId, LocalDate from, LocalDate to);

    // Used by the overdue task reminder scheduler — finds all non-done tasks whose due date has passed
    List<TaskItem> findAllByDueDateBeforeAndStatusNot(LocalDate date, TaskStatus status);
}
