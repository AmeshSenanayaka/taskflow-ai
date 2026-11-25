package com.taskflow.ai.guardrail;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic detection of text that tries to give the assistant instructions: "ignore previous instructions",
 * "reveal your system prompt", "you are now in developer mode", lines starting with "SYSTEM:". Used on the user's
 * message (direct injection) and on task titles and descriptions returned by tools (indirect injection).
 * <p>
 * It catches the common phrasings, not every possible attack: it is one layer. Owner scoping (3.0), the business
 * rules (3.2) and the write limit bound the damage when something gets through.
 */
public final class InjectionDetector {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("\\b(ignore|disregard|forget|override)\\s+(all\\s+|any\\s+|the\\s+|your\\s+|of\\s+)*"
                    + "(previous|prior|above|earlier|system|original)\\s+(instructions?|rules|prompts?|messages?|guidelines)", FLAGS),
            Pattern.compile("\\b(reveal|show|print|repeat|output|tell\\s+me)\\s+(me\\s+)?(your\\s+|the\\s+)*"
                    + "(system|hidden|developer|initial|original)\\s+(prompt|instructions?|message)", FLAGS),
            Pattern.compile("\\byou\\s+are\\s+now\\s+(in\\s+)?(a\\s+|an\\s+)?(developer|maintenance|admin|jailbreak|dan|unrestricted)\\b", FLAGS),
            Pattern.compile("\\b(developer|maintenance|god|jailbreak)\\s+mode\\b", FLAGS),
            Pattern.compile("(^|\\n)\\s*(system|assistant|developer)\\s*:", FLAGS)
    );

    private InjectionDetector() {
    }

    public static boolean looksLikeInstructions(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}
