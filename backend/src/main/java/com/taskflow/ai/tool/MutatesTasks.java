package com.taskflow.ai.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Tool} method whose successful execution changes task data. The supervisor's
 * {@code AgentRunRecorder} reads this marker to decide whether a response must report
 * {@code dataChanged = true}, so a new write tool only needs the annotation, not a second list of names.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MutatesTasks {
}
