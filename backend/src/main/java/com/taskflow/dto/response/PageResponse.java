package com.taskflow.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A stable, minimal page envelope. We deliberately do not expose Spring's {@link Page}
 * directly because its JSON shape is not a public contract and changes between versions.
 */
@Schema(description = "A page of results")
public record PageResponse<T>(
        List<T> content,
        @Schema(description = "Zero-based page index", example = "0") int page,
        @Schema(description = "Page size", example = "10") int size,
        @Schema(description = "Total number of matching items", example = "42") long totalElements,
        @Schema(description = "Total number of pages", example = "5") int totalPages,
        @Schema(description = "True when this is the first page") boolean first,
        @Schema(description = "True when this is the last page") boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
