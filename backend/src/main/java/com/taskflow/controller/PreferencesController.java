package com.taskflow.controller;

import com.taskflow.config.OpenApiConfig;
import com.taskflow.dto.request.UpdatePreferencesRequest;
import com.taskflow.dto.response.PreferencesResponse;
import com.taskflow.security.CurrentUserProvider;
import com.taskflow.service.PreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences")
@Tag(name = OpenApiConfig.TAG_PREFERENCES)
public class PreferencesController {

    private final PreferencesService preferencesService;
    private final CurrentUserProvider currentUser;

    public PreferencesController(PreferencesService preferencesService, CurrentUserProvider currentUser) {
        this.preferencesService = preferencesService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Get preferences")
    @ApiResponse(responseCode = "200", description = "Current preferences")
    public PreferencesResponse get() {
        return preferencesService.get(currentUser.userId());
    }

    @PutMapping
    @Operation(summary = "Update preferences", description = "Replaces timezone and daily available minutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences updated"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public PreferencesResponse update(@Valid @RequestBody UpdatePreferencesRequest request) {
        return preferencesService.update(currentUser.userId(), request);
    }
}
