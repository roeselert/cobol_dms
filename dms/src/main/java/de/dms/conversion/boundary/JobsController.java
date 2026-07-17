package de.dms.conversion.boundary;

import de.dms.conversion.control.JobQueue;
import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.platform.control.Paging;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Read-only view of the durable conversion queue. Visibility follows the
 * documents the jobs belong to: the org-unit predicate is pushed into the
 * query, so a caller never learns about jobs (or document names) outside
 * their units — bootstrap admins see every unit and therefore every job.
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobsController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final JobQueue jobQueue;

    public JobsController(CurrentUser currentUser, Authorization authorization, JobQueue jobQueue) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.jobQueue = jobQueue;
    }

    public record JobDto(String id, String documentId, String documentName, String status,
                         int attempts, String lastError, String createdAt) {
    }

    @GetMapping
    public List<JobDto> list(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size) {
        UserRef user = currentUser.require();
        List<String> visible = authorization.visibleOrgUnitIds(user);
        if (visible.isEmpty()) {
            return List.of();
        }
        return jobQueue.list(visible, Paging.page(page), Paging.size(size)).stream()
                .map(job -> new JobDto(job.id(), job.documentId(), job.documentName(),
                        job.status().name(), job.attempts(), job.lastError(),
                        Instant.ofEpochMilli(job.createdAt()).toString()))
                .toList();
    }
}
