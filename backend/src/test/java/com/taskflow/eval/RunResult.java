package com.taskflow.eval;

import java.util.List;

/**
 * The outcome of one execution of one scenario.
 *
 * @param failures   every expectation that did not hold, in plain words (empty when passed)
 * @param agents     sub-agents invoked over all turns, in order
 * @param tools      tool calls over all turns, in order, as {@code name} or {@code name(failed)}
 * @param llmCalls   LLM calls over all turns
 * @param tokens     total tokens over all turns, or null when the model reports none
 * @param responses  the assistant's answer to each turn
 */
public record RunResult(String scenarioId, int repetition, Status status, List<String> failures,
                        List<String> agents, List<String> tools, int llmCalls, Integer tokens, long durationMs,
                        List<String> responses) {

    public enum Status { PASSED, FAILED, ERROR }

    public boolean passed() {
        return status == Status.PASSED;
    }
}
