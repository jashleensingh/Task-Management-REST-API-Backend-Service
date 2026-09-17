package com.jashleen.taskmanagement.dto;

import com.jashleen.taskmanagement.model.TaskPriority;
import com.jashleen.taskmanagement.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public class TaskDtos {

    public record TaskRequest(
            @NotBlank String title,
            String description,
            @NotNull TaskPriority priority,
            LocalDate dueDate,
            @NotNull Long projectId,
            Long assigneeId) {
    }

    public record TaskStatusUpdateRequest(@NotNull TaskStatus status) {
    }

    public record TaskResponse(
            Long id, String title, String description, TaskStatus status, TaskPriority priority,
            LocalDate dueDate, Long projectId, String projectName,
            String assigneeUsername, String createdByUsername,
            Instant createdAt, Instant updatedAt) {
    }
}
