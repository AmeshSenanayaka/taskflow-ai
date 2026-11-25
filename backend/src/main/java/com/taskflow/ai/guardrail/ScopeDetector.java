package com.taskflow.ai.guardrail;

import java.util.regex.Pattern;

/**
 * Deterministic scope check: is a message clearly about something other than task management? A message is out of
 * scope only when it has an off-topic signal (a poem, the weather, a recipe, code...) <em>and</em> no task word, so
 * "add a task to write a poem for mum's birthday" stays in scope. Short follow-ups ("yes", "do it", "thanks") have
 * no off-topic signal and always pass.
 * <p>
 * Precision over recall: it refuses only obvious cases. Misses are measured by the evaluation scenarios; a
 * classifier can replace it later without changing callers.
 */
public final class ScopeDetector {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern TASK_WORDS = Pattern.compile("\\b(tasks?|to-?dos?|plan|planning|plans|schedule|"
            + "priorit\\w*|due|deadlines?|overdue|remind\\w*|complet\\w*|done|finish\\w*|progress|focus|today|tomorrow|"
            + "week|calendar|work|agenda|estimate\\w*)\\b", FLAGS);

    private static final Pattern OFF_TOPIC = Pattern.compile("\\b(poems?|poetry|songs?|lyrics|jokes?|recipes?|weather|"
            + "translat\\w*|essays?|stock\\s+prices?|bitcoin|crypto\\w*|capital\\s+of|movies?|football|cricket|"
            + "horoscope|write\\s+(me\\s+)?(some\\s+)?code|write\\s+a\\s+program)\\b", FLAGS);

    private ScopeDetector() {
    }

    public static boolean outOfScope(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return OFF_TOPIC.matcher(message).find() && !TASK_WORDS.matcher(message).find();
    }
}
