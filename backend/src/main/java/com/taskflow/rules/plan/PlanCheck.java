package com.taskflow.rules.plan;

import com.taskflow.dto.response.TaskResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A proposed day plan, as the plan rules see it. Blocks are parsed but not trusted: an unparsable value is null,
 * and the well-formedness rule reports it.
 *
 * @param date             the day the plan is for, or null when it could not be parsed
 * @param today            the owner's current date
 * @param availableMinutes the owner's daily available minutes
 * @param blocks           the proposed time blocks, in the order given
 * @param ownTasks         the owner's tasks referenced by the blocks, by id (another user's or unknown ids are absent)
 */
public record PlanCheck(LocalDate date, LocalDate today, int availableMinutes, List<Block> blocks,
                        Map<UUID, TaskResponse> ownTasks) {

    /** @param raw the block as the model sent it, for messages */
    public record Block(UUID taskId, LocalTime start, Integer minutes, String raw) {

        public LocalTime end() {
            return start == null || minutes == null ? null : start.plusMinutes(minutes);
        }
    }
}
