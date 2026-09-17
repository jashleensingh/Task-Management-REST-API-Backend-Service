package com.jashleen.taskmanagement.controller;

import com.jashleen.taskmanagement.dto.ProjectDtos.ProjectRequest;
import com.jashleen.taskmanagement.dto.ProjectDtos.ProjectResponse;
import com.jashleen.taskmanagement.model.User;
import com.jashleen.taskmanagement.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request,
                                   @AuthenticationPrincipal User currentUser) {
        return projectService.create(request, currentUser);
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal User currentUser) {
        return projectService.listVisibleTo(currentUser);
    }

    @GetMapping("/{id}")
    public ProjectResponse getById(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        return projectService.getById(id, currentUser);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request,
                                   @AuthenticationPrincipal User currentUser) {
        return projectService.update(id, request, currentUser);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        projectService.delete(id, currentUser);
    }
}
