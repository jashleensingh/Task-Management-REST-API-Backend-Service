package com.jashleen.taskmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises the full stack (real Spring context, real H2 DB, real Spring
 * Security filter chain) rather than mocking layers out — this is what
 * catches wiring problems (security config, JSON serialization, JPA
 * mappings) that pure unit tests can't.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String ownerToken;
    private String otherUserToken;

    @BeforeEach
    void registerTwoUsers() throws Exception {
        ownerToken = registerAndLogin("owner_" + System.nanoTime(), "password123");
        otherUserToken = registerAndLogin("other_" + System.nanoTime(), "password123");
    }

    private String registerAndLogin(String username, String password) throws Exception {
        String registerBody = """
                {"username": "%s", "password": "%s", "email": "%s@example.com"}
                """.formatted(username, password, username);

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullTaskLifecycle() throws Exception {
        // Create a project
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Q3 Launch", "description": "Launch prep"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long projectId = objectMapper.readTree(projectResponse).get("id").asLong();

        // Create a task under it
        String taskResponse = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Write launch announcement", "priority": "HIGH", "projectId": %d}
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(taskResponse).get("id").asLong();

        // Valid transition: TODO -> IN_PROGRESS
        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Invalid transition: IN_PROGRESS -> ... -> DONE -> (anything) should be rejected once DONE
        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DONE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "TODO"}
                                """))
                .andExpect(status().isConflict()); // DONE is terminal

        // A user with no relationship to the task can't change its status
        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_PROGRESS"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonOwnerCannotCreateTaskUnderSomeoneElsesProject() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Private Project"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long projectId = objectMapper.readTree(projectResponse).get("id").asLong();

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Sneaky task", "priority": "LOW", "projectId": %d}
                                """.formatted(projectId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRequestBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "missing the required name field"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }
}
