package com.jashleen.taskmanagement.service;

import com.jashleen.taskmanagement.dto.TaskDtos.TaskRequest;
import com.jashleen.taskmanagement.exception.InvalidStatusTransitionException;
import com.jashleen.taskmanagement.exception.UnauthorizedActionException;
import com.jashleen.taskmanagement.model.*;
import com.jashleen.taskmanagement.repository.TaskRepository;
import com.jashleen.taskmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskServiceTest {

    private TaskRepository taskRepository;
    private UserRepository userRepository;
    private ProjectService projectService;
    private TaskService taskService;

    private User creator;
    private User assignee;
    private User otherUser;
    private User admin;
    private Project project;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        userRepository = mock(UserRepository.class);
        projectService = mock(ProjectService.class);
        taskService = new TaskService(taskRepository, userRepository, projectService);

        creator = userWithId(1L, Role.USER);
        assignee = userWithId(2L, Role.USER);
        otherUser = userWithId(3L, Role.USER);
        admin = userWithId(4L, Role.ADMIN);
        project = new Project("Website Revamp", "Q3 redesign", creator);

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User userWithId(long id, Role role) {
        User user = new User("user" + id, "hashed", "user" + id + "@example.com", role);
        setId(user, id);
        return user;
    }

    // Reflection is the pragmatic way to set the JPA-generated id on an entity built via `new` in a unit test.
    private void setId(User user, long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Task newTask(TaskStatus status) {
        Task task = new Task("Fix homepage bug", "desc", TaskPriority.HIGH,
                LocalDate.now().plusDays(3), project, assignee, creator);
        task.applyStatus(status); // TODO is the constructor default; move it if the test needs a different start state
        return task;
    }

    @Test
    void todoCanMoveToInProgress() {
        Task task = newTask(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        var response = taskService.updateStatus(1L, TaskStatus.IN_PROGRESS, creator);

        assertEquals(TaskStatus.IN_PROGRESS, response.status());
    }

    @Test
    void doneIsTerminalAndRejectsAnyTransition() {
        Task task = newTask(TaskStatus.DONE);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(InvalidStatusTransitionException.class,
                () -> taskService.updateStatus(1L, TaskStatus.IN_PROGRESS, creator));
    }

    @Test
    void cancelledIsTerminalAndRejectsAnyTransition() {
        Task task = newTask(TaskStatus.CANCELLED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(InvalidStatusTransitionException.class,
                () -> taskService.updateStatus(1L, TaskStatus.TODO, creator));
    }

    @Test
    void inProgressCanMoveBackToTodo() {
        Task task = newTask(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        var response = taskService.updateStatus(1L, TaskStatus.TODO, creator);

        assertEquals(TaskStatus.TODO, response.status());
    }

    @Test
    void assigneeCanUpdateStatus() {
        Task task = newTask(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        var response = taskService.updateStatus(1L, TaskStatus.IN_PROGRESS, assignee);

        assertEquals(TaskStatus.IN_PROGRESS, response.status());
    }

    @Test
    void unrelatedUserCannotUpdateStatus() {
        Task task = newTask(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(UnauthorizedActionException.class,
                () -> taskService.updateStatus(1L, TaskStatus.IN_PROGRESS, otherUser));
    }

    @Test
    void adminCanUpdateStatusOnAnyTask() {
        Task task = newTask(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        var response = taskService.updateStatus(1L, TaskStatus.IN_PROGRESS, admin);

        assertEquals(TaskStatus.IN_PROGRESS, response.status());
    }

    @Test
    void onlyCreatorOrAdminCanEditTaskDetails() {
        Task task = newTask(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        // Assignee is not the creator — should NOT be allowed to edit details (only status)
        var request = new TaskRequest("New title", "new desc", TaskPriority.LOW,
                LocalDate.now().plusDays(1), 1L, null);

        assertThrows(UnauthorizedActionException.class,
                () -> taskService.updateDetails(1L, request, assignee));
    }
}
