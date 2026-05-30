package com.myworld.modules.tasks.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.modules.tasks.application.DailyTaskService;
import com.myworld.modules.tasks.domain.TaskType;
import com.myworld.modules.tasks.dto.TaskDTO;
import com.myworld.modules.tasks.dto.TaskLeadSubmitDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class DailyTaskController {

    private final DailyTaskService taskService;

    /**
     * GET /api/tasks/today
     * Returns all tasks with completion & lead status for current user.
     * Response: List<TaskDTO>
     */
    @GetMapping("/today")
    public ApiResponse<List<TaskDTO>> getTodayTasks(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        List<TaskDTO> tasks = taskService.getTodayTasks(currentUser.getUser().getId());
        return ApiResponse.success(tasks, "Tasks fetched");
    }

    /**
     * POST /api/tasks/submit-lead
     * Body: { taskType, email, mobile }
     * 
     * Saves lead and returns the unlocked partnerUrl.
     * Frontend then shows "Go to Site" button with this URL.
     * Credits are NOT awarded yet — user must call /complete after visiting.
     */
    @PostMapping("/submit-lead")
    public ResponseEntity<ApiResponse<Map<String, String>>> submitLead(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody TaskLeadSubmitDTO dto) {

        try {
            String url = taskService.submitLead(currentUser.getUser().getId(), dto);
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("partnerUrl", url),
                    "Details saved! Your link is now unlocked."));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * POST /api/tasks/complete
     * Body: { taskType }
     *
     * Awards credits for the task.
     * For LINK tasks: lead must have been submitted first.
     */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> completeTask(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestBody Map<String, String> body) {

        String taskTypeStr = body.get("taskType");
        if (taskTypeStr == null || taskTypeStr.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("taskType is required"));
        }

        TaskType taskType;
        try {
            taskType = TaskType.valueOf(taskTypeStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid taskType: " + taskTypeStr));
        }

        try {
            boolean completed = taskService.completeTask(currentUser.getUser().getId(), taskType);
            String msg = completed ? "Task completed! Credits added." : "Already completed today.";
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("completed", completed, "alreadyDone", !completed), msg));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }
}