package com.jashleen.taskmanagement.service;

import com.jashleen.taskmanagement.dto.TaskDtos.TaskRequest;
import com.jashleen.taskmanagement.dto.TaskDtos.TaskResponse;
import com.jashleen.taskmanagement.exception.InvalidStatusTransitionException;
import com.jashleen.taskmanagement.exception.ResourceNotFoundException;
import com.jashleen.taskmanagement.exception.UnauthorizedActionException;
import com.jashleen.taskmanagement.model.*;
import com.jashleen.taskmanagement.repository.TaskRepository;
import com.jashleen.taskmanagement.repository.TaskSpecifications;
import com.jashleen.taskmanagement.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class TaskService {

    /**
     * Allowed forward/lateral transitions per current status. DONE and
     * CANCELLED have no outgoing edges — they're terminal states; reopening
     * a task means creating a new one, which keeps the status history honest.
     */
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            TaskStatus.TODO, Set.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.IN_PROGRESS, Set.of(TaskStatus.DONE, TaskStatus.CANCELLED, TaskStatus.TODO),
            TaskStatus.DONE, Set.of(),
            TaskStatus.CANCELLED, Set.of()
    );

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository,
                        ProjectService projectService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.projectService = projectService;
    }

    public TaskResponse create(TaskRequest request, User currentUser) {
        // Reuses ProjectService's ownership check: only the project's owner (or an admin) can add tasks to it.
        Project project = projectService.findOwnedOrAdmin(request.projectId(), currentUser);
        User assignee = resolveAssignee(request.assigneeId());

        Task task = new Task(request.title(), request.description(), request.priority(),
                request.dueDate(), project, assignee, currentUser);
        return toResponse(taskRepository.save(task));
    }

    public Page<TaskResponse> search(TaskStatus status, TaskPriority priority,
                                      Long projectId, Long assigneeId, Pageable pageable) {
        var spec = TaskSpecifications.withFilters(status, priority, projectId, assigneeId);
        return taskRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public TaskResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public TaskResponse updateDetails(Long id, TaskRequest request, User currentUser) {
        Task task = findOrThrow(id);
        requireCreatorOrAdmin(task, currentUser);

        Project project = projectService.findOwnedOrAdmin(request.projectId(), currentUser);
        User assignee = resolveAssignee(request.assigneeId());

        task.updateDetails(request.title(), request.description(), request.priority(),
                request.dueDate(), assignee);
        return toResponse(taskRepository.save(task));
    }

    public TaskResponse updateStatus(Long id, TaskStatus newStatus, User currentUser) {
        Task task = findOrThrow(id);
        requireCreatorAssigneeOrAdmin(task, currentUser);

        Set<TaskStatus> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(task.getStatus(), Set.of());
        if (!allowedNext.contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Cannot move task " + id + " from " + task.getStatus() + " to " + newStatus +
                    ". Allowed next states: " + allowedNext);
        }

        task.applyStatus(newStatus);
        return toResponse(taskRepository.save(task));
    }

    public void delete(Long id, User currentUser) {
        Task task = findOrThrow(id);
        requireCreatorOrAdmin(task, currentUser);
        taskRepository.delete(task);
    }

    private Task findOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
    }

    private User resolveAssignee(Long assigneeId) {
        if (assigneeId == null) return null;
        return userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + assigneeId));
    }

    private void requireCreatorOrAdmin(Task task, User currentUser) {
        boolean isCreator = task.getCreatedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isCreator && !isAdmin) {
            throw new UnauthorizedActionException("Only the task's creator or an admin can do that");
        }
    }

    private void requireCreatorAssigneeOrAdmin(Task task, User currentUser) {
        boolean isCreator = task.getCreatedBy().getId().equals(currentUser.getId());
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isCreator && !isAssignee && !isAdmin) {
            throw new UnauthorizedActionException(
                    "Only the task's creator, assignee, or an admin can change its status");
        }
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(), task.getTitle(), task.getDescription(), task.getStatus(), task.getPriority(),
                task.getDueDate(), task.getProject().getId(), task.getProject().getName(),
                task.getAssignee() != null ? task.getAssignee().getUsername() : null,
                task.getCreatedBy().getUsername(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
