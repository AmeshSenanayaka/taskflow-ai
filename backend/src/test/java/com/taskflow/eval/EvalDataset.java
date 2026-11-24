package com.taskflow.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The version-controlled scenario dataset ({@code classpath:eval/scenarios/*.json}, each file a JSON array) and the
 * fingerprints a report needs to be reproducible: a hash of the dataset and of every prompt template.
 */
public final class EvalDataset {

    static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules()
            // A typo in a scenario must fail loudly, not silently drop an expectation.
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final List<Scenario> scenarios;
    private final String sha256;

    private EvalDataset(List<Scenario> scenarios, String sha256) {
        this.scenarios = scenarios;
        this.sha256 = sha256;
    }

    public static EvalDataset load() {
        Resource[] files = resources("classpath:eval/scenarios/*.json");
        List<Scenario> scenarios = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        for (Resource file : files) {
            String content = read(file);
            all.append(file.getFilename()).append('\n').append(content).append('\n');
            try {
                scenarios.addAll(JSON.readValue(content, new TypeReference<List<Scenario>>() { }));
            } catch (IOException ex) {
                throw new IllegalStateException("Invalid scenario file " + file.getFilename() + ": " + ex.getMessage(), ex);
            }
        }
        validate(scenarios);
        scenarios.sort(Comparator.comparing(Scenario::id));
        return new EvalDataset(List.copyOf(scenarios), sha256(all.toString()));
    }

    public List<Scenario> scenarios() {
        return scenarios;
    }

    public String sha256() {
        return sha256;
    }

    /** SHA-256 of each prompt template, so a report says exactly which prompts produced it. */
    public static Map<String, String> promptHashes() {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Resource prompt : resources("classpath:prompts/*.txt")) {
            hashes.put(prompt.getFilename(), sha256(read(prompt)));
        }
        return hashes;
    }

    private static void validate(List<Scenario> scenarios) {
        Set<String> ids = new HashSet<>();
        for (Scenario scenario : scenarios) {
            if (scenario.id() == null || !ids.add(scenario.id())) {
                throw new IllegalStateException("Missing or duplicate scenario id: " + scenario.id());
            }
            if (scenario.category() == null || scenario.title() == null) {
                throw new IllegalStateException(scenario.id() + ": category and title are required");
            }
            if (!List.of("alice", "bob").contains(scenario.userOrDefault())) {
                throw new IllegalStateException(scenario.id() + ": user must be alice or bob");
            }
            if (scenario.executable() && (scenario.turns() == null || scenario.turns().isEmpty())) {
                throw new IllegalStateException(scenario.id() + ": an executable scenario needs at least one turn");
            }
            for (Scenario.ScriptStep step : scenario.scriptOrEmpty()) {
                long kinds = Arrays.asList(step.invokeAgent(), step.toolCall(), step.text(), step.done()).stream()
                        .filter(value -> value != null).count();
                if (kinds != 1) {
                    throw new IllegalStateException(scenario.id() + ": each script step needs exactly one of "
                            + "invokeAgent, toolCall, text or done");
                }
            }
        }
    }

    private static Resource[] resources(String pattern) {
        try {
            Resource[] found = new PathMatchingResourcePatternResolver().getResources(pattern);
            Arrays.sort(found, Comparator.comparing(Resource::getFilename));
            return found;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Line endings are normalised so a checkout on Windows and on Linux produce the same hash. */
    private static String read(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
