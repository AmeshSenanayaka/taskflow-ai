package com.taskflow.controller;

import com.taskflow.config.OpenApiConfig;
import com.taskflow.dto.response.DashboardStatsResponse;
import com.taskflow.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = OpenApiConfig.TAG_DASHBOARD)
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Dashboard statistics",
            description = "Counts by status plus overdue and due-today counts, computed in the user's configured timezone.")
    @ApiResponse(responseCode = "200", description = "Statistics")
    public DashboardStatsResponse getStats() {
        return dashboardService.getStats();
    }
}
