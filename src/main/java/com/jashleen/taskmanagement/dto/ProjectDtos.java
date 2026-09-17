package com.jashleen.taskmanagement.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class ProjectDtos {

    public record ProjectRequest(
            @NotBlank String name,
            String description) {
    }

    public record ProjectResponse(
            Long id, String name, String description, String ownerUsername, Instant createdAt) {
    }
}
