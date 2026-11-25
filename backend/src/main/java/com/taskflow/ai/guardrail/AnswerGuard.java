package com.taskflow.ai.guardrail;

import com.taskflow.ai.dto.DailyPlan;
import com.taskflow.dto.response.TaskResponse;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Output checks on an answer before the user sees it (Phase 3.3). Deterministic and grounded in data, not in
 * another model's opinion:
 * <ul>
 *   <li>every task id in the answer belongs to the user;</li>
 *   <li>every quoted task title in the answer is one of the user's task titles (or a phrase from their message);</li>
 *   <li>when a checked plan exists, every time block in the answer is one of the plan's blocks.</li>
 * </ul>
 * Agents write task titles in double quotes (rule in each agent prompt), which is what makes the title check possible;
 * an unquoted invented title is not detected. Quoted status and priority values ("COMPLETED", "high") are not titles.
 */
public final class AnswerGuard {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern QUOTED = Pattern.compile("[\"“]([^\"“”\\n]{3,150})[\"”]");
    private static final Pattern TIME_RANGE = Pattern.compile(
            "\\b([01]?\\d|2[0-3]):([0-5]\\d)\\s*(?:-|–|—|to)\\s*([01]?\\d|2[0-3]):([0-5]\\d)\\b");

    /** Status and priority values an agent may quote, e.g. "COMPLETED" or "in progress". */
    private static final Set<String> ENUM_VALUES = enumValues();

    private static Set<String> enumValues() {
        Set<String> values = new HashSet<>();
        for (Enum<?> value : TaskStatus.values()) {
            values.add(value.name().toLowerCase(Locale.ROOT));
            values.add(value.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        for (Enum<?> value : TaskPriority.values()) {
            values.add(value.name().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(values);
    }

    private AnswerGuard() {
    }

    /**
     * @param tasks       every task of the user (after the request ran)
     * @param userMessage what the user wrote; quoting the user back is not a hallucination
     * @param plan        the checked plan of this request, or null
     */
    public static List<GuardrailViolation> check(String answer, List<TaskResponse> tasks, String userMessage,
                                                 DailyPlan plan) {
        List<GuardrailViolation> violations = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return violations;
        }
        Set<String> ids = new HashSet<>();
        Set<String> titles = new HashSet<>();
        for (TaskResponse task : tasks) {
            ids.add(task.id().toString().toLowerCase(Locale.ROOT));
            titles.add(normalize(task.title()));
        }
        String message = normalize(userMessage);

        Set<String> unknownIds = new LinkedHashSet<>();
        Matcher idMatcher = UUID_PATTERN.matcher(answer);
        while (idMatcher.find()) {
            if (!ids.contains(idMatcher.group().toLowerCase(Locale.ROOT))) {
                unknownIds.add(idMatcher.group());
            }
        }
        if (!unknownIds.isEmpty()) {
            violations.add(new GuardrailViolation(GuardrailCodes.OUTPUT_UNKNOWN_TASK,
                    "The answer mentions task id(s) " + String.join(", ", unknownIds) + " that the user does not have."));
        }

        Set<String> unknownTitles = new LinkedHashSet<>();
        Matcher quoted = QUOTED.matcher(answer);
        while (quoted.find()) {
            String text = quoted.group(1).trim();
            String normalized = normalize(text);
            if (!titles.contains(normalized) && !message.contains(normalized) && !ENUM_VALUES.contains(normalized)) {
                unknownTitles.add(text);
            }
        }
        if (!unknownTitles.isEmpty()) {
            violations.add(new GuardrailViolation(GuardrailCodes.OUTPUT_UNKNOWN_TASK,
                    "The answer quotes \"" + String.join("\", \"", unknownTitles)
                            + "\", which is not the title of any of the user's tasks."));
        }

        if (plan != null) {
            Set<String> blocks = new HashSet<>();
            plan.entries().forEach(entry -> blocks.add(entry.start() + "-" + entry.end()));
            Set<String> wrong = new LinkedHashSet<>();
            Matcher range = TIME_RANGE.matcher(answer);
            while (range.find()) {
                String block = time(range.group(1), range.group(2)) + "-" + time(range.group(3), range.group(4));
                if (!blocks.contains(block)) {
                    wrong.add(block);
                }
            }
            if (!wrong.isEmpty()) {
                violations.add(new GuardrailViolation(GuardrailCodes.OUTPUT_PLAN_MISMATCH,
                        "The answer shows time block(s) " + String.join(", ", wrong)
                                + " that are not in the checked plan " + blocks + "."));
            }
        }
        return violations;
    }

    private static String time(String hours, String minutes) {
        return String.format("%02d:%s", Integer.parseInt(hours), minutes);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
