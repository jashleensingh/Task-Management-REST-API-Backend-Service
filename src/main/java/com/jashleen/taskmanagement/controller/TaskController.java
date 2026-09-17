package com.jashleen.taskmanagement.controller;

import com.jashleen.taskmanagement.dto.TaskDtos.TaskRequest;
import com.jashleen.taskmanagement.dto.TaskDtos.TaskResponse;
import com.jashleen.taskmanagement.dto.TaskDtos.TaskStatusUpdateRequest;
import com.jashleen.taskmanagement.model.TaskPriority;
import com.jashleen.taskmanagement.model.TaskStatus;
import com.jashleen.taskmanagement.model.User;
import com.jashleen.taskmanagement.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody TaskRequest request,
                                @AuthenticationPrincipal User currentUser) {
        return taskService.create(request, currentUser);
    }

    /**
     * Filters are all optional and combine with AND. Example:
     * GET /api/tasks?status=IN_PROGRESS&priority=HIGH&projectId=3&page=0&size=20&sort=dueDate,asc
     */
    @GetMapping
    public Page<TaskResponse> search(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long assigneeId,
            Pageable pageable) {
        return taskService.search(status, priority, projectId, assigneeId, pageable);
    }

    @GetMapping("/{id}")
    public TaskResponse getById(@PathVariable Long id) {
        return taskService.getById(id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request,
                                @AuthenticationPrincipal User currentUser) {
        return taskService.updateDetails(id, request, currentUser);
    }

    @PatchMapping("/{id}/status")
    public TaskResponse updateStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusUpdateRequest request,
                                      @AuthenticationPrincipal User currentUser) {
        return taskService.updateStatus(id, request.status(), currentUser);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        taskService.delete(id, currentUser);
    }
}
