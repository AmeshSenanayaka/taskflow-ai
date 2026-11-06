package com.taskflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String TAG_TASKS = "Tasks";
    public static final String TAG_DASHBOARD = "Dashboard";
    public static final String TAG_PREFERENCES = "Preferences";
    public static final String TAG_AI = "AI Assistant";

    @Bean
    public OpenAPI taskFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TaskFlow AI API")
                        .version("0.2.0 (Phase 1)")
                        .description("""
                                REST API for TaskFlow AI, a personal task manager.

                                Phase 0 exposes plain task management: CRUD, filtering, sorting, pagination, \
                                dashboard statistics and single-user preferences. \
                                Phase 1 adds the AI assistant (/api/ai/**): a LangChain4j TaskAgent backed by \n                                Azure OpenAI that manages tasks through tool calling, with conversation memory \n                                and backend-enforced confirmation for deletions. \n                                Errors use RFC 9457 Problem Details (application/problem+json).
                                """)
                        .license(new License().name("MIT")))
                .tags(List.of(
                        new Tag().name(TAG_TASKS).description("Create, read, update, delete and list tasks"),
                        new Tag().name(TAG_DASHBOARD).description("Aggregated statistics for the dashboard"),
                        new Tag().name(TAG_PREFERENCES).description("Single-user preferences such as timezone"),
                        new Tag().name(TAG_AI).description("Natural-language task management through the TaskAgent")
                ));
    }
}
