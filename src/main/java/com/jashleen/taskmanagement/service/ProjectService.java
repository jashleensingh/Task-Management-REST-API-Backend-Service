package com.jashleen.taskmanagement.service;

import com.jashleen.taskmanagement.dto.ProjectDtos.ProjectRequest;
import com.jashleen.taskmanagement.dto.ProjectDtos.ProjectResponse;
import com.jashleen.taskmanagement.exception.ResourceNotFoundException;
import com.jashleen.taskmanagement.exception.UnauthorizedActionException;
import com.jashleen.taskmanagement.model.Project;
import com.jashleen.taskmanagement.model.Role;
import com.jashleen.taskmanagement.model.User;
import com.jashleen.taskmanagement.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectResponse create(ProjectRequest request, User currentUser) {
        Project project = projectRepository.save(
                new Project(request.name(), request.description(), currentUser));
        return toResponse(project);
    }

    public List<ProjectResponse> listVisibleTo(User currentUser) {
        List<Project> projects = currentUser.getRole() == Role.ADMIN
                ? projectRepository.findAll()
                : projectRepository.findByOwnerId(currentUser.getId());
        return projects.stream().map(this::toResponse).toList();
    }

    public ProjectResponse getById(Long id, User currentUser) {
        Project project = findOwnedOrAdmin(id, currentUser);
        return toResponse(project);
    }

    public ProjectResponse update(Long id, ProjectRequest request, User currentUser) {
        Project project = findOwnedOrAdmin(id, currentUser);
        project.update(request.name(), request.description());
        return toResponse(projectRepository.save(project));
    }

    public void delete(Long id, User currentUser) {
        Project project = findOwnedOrAdmin(id, currentUser);
        projectRepository.delete(project);
    }

    /** Package-private so TaskService can reuse the same ownership check when creating tasks under a project. */
    Project findOwnedOrAdmin(Long id, User currentUser) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));

        boolean isOwner = project.getOwner().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new UnauthorizedActionException("You do not have access to project " + id);
        }
        return project;
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(), project.getName(), project.getDescription(),
                project.getOwner().getUsername(), project.getCreatedAt());
    }
}
