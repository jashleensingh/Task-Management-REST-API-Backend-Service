package com.jashleen.taskmanagement.repository;

import com.jashleen.taskmanagement.model.Task;
import com.jashleen.taskmanagement.model.TaskPriority;
import com.jashleen.taskmanagement.model.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Each filter is optional — null means "don't filter on this field" — so
 * callers can combine any subset of status/priority/project/assignee
 * without the repository needing a method per combination.
 */
public class TaskSpecifications {

    public static Specification<Task> withFilters(TaskStatus status, TaskPriority priority,
                                                    Long projectId, Long assigneeId) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicate = cb.and(predicate, cb.equal(root.get("priority"), priority));
            }
            if (projectId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("project").get("id"), projectId));
            }
            if (assigneeId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("assignee").get("id"), assigneeId));
            }
            return predicate;
        };
    }
}
