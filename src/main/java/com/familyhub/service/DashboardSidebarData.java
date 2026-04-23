package com.familyhub.service;

import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.TaskItem;

import java.util.List;

public record DashboardSidebarData(
        List<EventResponse> upcomingEvents,
        long todayEventsCount,
        List<TaskItem> dueSoonTasks,
        long attentionTasksCount,
        int doneCount,
        int totalCount
) {}
