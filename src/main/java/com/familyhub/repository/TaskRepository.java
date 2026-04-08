package com.familyhub.repository;

import com.familyhub.entity.TaskItem;
import com.familyhub.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<TaskItem, Long> {

    List<TaskItem> findAllByFamilyIdOrderByCreatedAtDesc(Long familyId); //visi taskai seimai, naujaisi virsuje
    List<TaskItem> findAllByFamilyIdAndStatusOrderByCreatedAtDesc(Long familyId, TaskStatus status); //filtras pagal busena
    List<TaskItem> findAllByAssignedToIdOrderByCreatedAtDesc(Long userId); //vartotojui priskirti taskai
}
