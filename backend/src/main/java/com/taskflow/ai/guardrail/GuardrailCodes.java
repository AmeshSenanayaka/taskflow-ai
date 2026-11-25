package com.taskflow.ai.guardrail;

/** Stable codes reported in responses, logs and evaluations when a guardrail acts. */
public final class GuardrailCodes {

    /** The message is not about task management; answered without running any agent. */
    public static final String INPUT_OUT_OF_SCOPE = "INPUT_OUT_OF_SCOPE";
    /** The message tries to override the assistant's instructions; answered without running any agent. */
    public static final String INPUT_PROMPT_INJECTION = "INPUT_PROMPT_INJECTION";
    /** A tool returned task text that looks like instructions; the model was told to treat it as data. */
    public static final String TOOL_UNTRUSTED_CONTENT = "TOOL_UNTRUSTED_CONTENT";
    /** The message reached its limit of task changes; further writes were refused. */
    public static final String TOOL_WRITE_LIMIT = "TOOL_WRITE_LIMIT";
    /** The answer mentioned a task id or title the user does not have. */
    public static final String OUTPUT_UNKNOWN_TASK = "OUTPUT_UNKNOWN_TASK";
    /** The answer's time blocks differ from the checked plan. */
    public static final String OUTPUT_PLAN_MISMATCH = "OUTPUT_PLAN_MISMATCH";
    /** A failed answer was rewritten and passed the checks. */
    public static final String OUTPUT_REWRITTEN = "OUTPUT_REWRITTEN";
    /** A failed answer could not be fixed; a safe fallback answer was returned instead. */
    public static final String OUTPUT_FALLBACK = "OUTPUT_FALLBACK";

    private GuardrailCodes() {
    }
}
