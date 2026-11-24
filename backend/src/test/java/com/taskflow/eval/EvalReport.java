package com.taskflow.eval;

import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

/**
 * Turns run results into a reproducible report (JSON for tools, Markdown for people) under
 * {@code target/eval-reports/<suite>/}. Every pass rate is reported with its sample size, and scenarios that were
 * not executed are listed with the reason, never silently dropped or counted as passes.
 */
public final class EvalReport {

    public record Metadata(String suite, Instant generatedAt, String gitCommit, String model, String temperature,
                           int repetitions, String datasetSha256, Map<String, String> promptSha256, Instant clock) {
    }

    /**
     * @param status PASSED / FAILED (every run), FLAKY (some runs passed), NOT_APPLICABLE, NOT_RUN
     */
    public record ScenarioSummary(String id, String title, String category, String status, String reason,
                                  int runs, int passes, Double passRate, Double avgLlmCalls, Double avgTokens,
                                  Double avgDurationMs, List<String> agentsSeen, List<String> failures) {
    }

    public record CategorySummary(int scenarios, int executed, int runs, int passes, Double passRate) {
    }

    public record Report(Metadata metadata, int scenarios, int executed, int notApplicable, int notRun,
                         int runs, int passes, Double passRate, Map<String, CategorySummary> byCategory,
                         List<ScenarioSummary> results) {
    }

    private EvalReport() {
    }

    public static Report build(Metadata metadata, EvalDataset dataset, List<RunResult> results, String notRunReason) {
        List<ScenarioSummary> summaries = new ArrayList<>();
        Map<Scenario.Category, int[]> categories = new EnumMap<>(Scenario.Category.class); // scenarios, executed, runs, passes
        int runs = 0;
        int passes = 0;
        int notApplicable = 0;
        int notRun = 0;
        for (Scenario scenario : dataset.scenarios()) {
            int[] category = categories.computeIfAbsent(scenario.category(), key -> new int[4]);
            category[0]++;
            List<RunResult> own = results.stream().filter(result -> result.scenarioId().equals(scenario.id())).toList();
            if (!scenario.executable()) {
                notApplicable++;
                summaries.add(new ScenarioSummary(scenario.id(), scenario.title(), scenario.category().name(),
                        "NOT_APPLICABLE", "until " + scenario.notApplicable().until() + ": " + scenario.notApplicable().reason(),
                        0, 0, null, null, null, null, List.of(), List.of()));
                continue;
            }
            if (own.isEmpty()) {
                notRun++;
                summaries.add(new ScenarioSummary(scenario.id(), scenario.title(), scenario.category().name(),
                        "NOT_RUN", notRunReason, 0, 0, null, null, null, null, List.of(), List.of()));
                continue;
            }
            int ownPasses = (int) own.stream().filter(RunResult::passed).count();
            runs += own.size();
            passes += ownPasses;
            category[1]++;
            category[2] += own.size();
            category[3] += ownPasses;
            String status = ownPasses == own.size() ? "PASSED" : ownPasses == 0 ? "FAILED" : "FLAKY";
            LinkedHashSet<String> agents = new LinkedHashSet<>();
            LinkedHashSet<String> failures = new LinkedHashSet<>();
            own.forEach(result -> {
                agents.addAll(result.agents());
                result.failures().forEach(failure -> failures.add("run " + result.repetition() + ": " + failure));
            });
            summaries.add(new ScenarioSummary(scenario.id(), scenario.title(), scenario.category().name(), status, null,
                    own.size(), ownPasses, rate(ownPasses, own.size()),
                    avg(own.stream().mapToDouble(RunResult::llmCalls).average()),
                    avg(own.stream().filter(result -> result.tokens() != null).mapToDouble(RunResult::tokens).average()),
                    avg(own.stream().mapToDouble(RunResult::durationMs).average()),
                    List.copyOf(agents), List.copyOf(failures)));
        }
        Map<String, CategorySummary> byCategory = new LinkedHashMap<>();
        categories.forEach((category, counts) -> byCategory.put(category.name(),
                new CategorySummary(counts[0], counts[1], counts[2], counts[3], rate(counts[3], counts[2]))));
        return new Report(metadata, dataset.scenarios().size(), dataset.scenarios().size() - notApplicable - notRun,
                notApplicable, notRun, runs, passes, rate(passes, runs), byCategory, summaries);
    }

    /** Writes report.json and report.md and returns the directory. */
    public static Path write(Report report) {
        Path dir = Path.of("target", "eval-reports", report.metadata().suite());
        try {
            Files.createDirectories(dir);
            EvalDataset.JSON.copy().enable(SerializationFeature.INDENT_OUTPUT)
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValue(dir.resolve("report.json").toFile(), report);
            Files.writeString(dir.resolve("report.md"), markdown(report));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return dir;
    }

    static String markdown(Report report) {
        Metadata meta = report.metadata();
        StringBuilder md = new StringBuilder();
        md.append("# TaskFlow AI evaluation report: ").append(meta.suite()).append("\n\n");
        md.append("| | |\n|---|---|\n");
        md.append("| Generated | ").append(meta.generatedAt()).append(" |\n");
        md.append("| Git commit | `").append(meta.gitCommit()).append("` |\n");
        md.append("| Model | ").append(meta.model()).append(" (temperature ").append(meta.temperature()).append(") |\n");
        md.append("| Repetitions per scenario | ").append(meta.repetitions()).append(" |\n");
        md.append("| Default fixed clock | ").append(meta.clock()).append(" |\n");
        md.append("| Dataset SHA-256 | `").append(meta.datasetSha256()).append("` |\n\n");

        md.append("## Summary\n\n");
        md.append("Pass rate: **").append(percent(report.passRate())).append("** (").append(report.passes())
                .append(" of ").append(report.runs()).append(" runs, ").append(report.executed())
                .append(" scenarios executed; ").append(report.notApplicable()).append(" not applicable yet, ")
                .append(report.notRun()).append(" not run in this suite).\n\n");
        md.append("| Category | Scenarios | Executed | Runs | Passed | Pass rate |\n|---|---|---|---|---|---|\n");
        report.byCategory().forEach((category, summary) -> md.append("| ").append(category).append(" | ")
                .append(summary.scenarios()).append(" | ").append(summary.executed()).append(" | ")
                .append(summary.runs()).append(" | ").append(summary.passes()).append(" | ")
                .append(percent(summary.passRate())).append(" |\n"));

        md.append("\n## Scenarios\n\n");
        md.append("| Id | Scenario | Status | Passed | Avg LLM calls | Avg tokens | Avg ms | Agents |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (ScenarioSummary summary : report.results()) {
            md.append("| ").append(summary.id()).append(" | ").append(escape(summary.title())).append(" | ")
                    .append(summary.status()).append(" | ")
                    .append(summary.runs() == 0 ? "–" : summary.passes() + "/" + summary.runs()).append(" | ")
                    .append(number(summary.avgLlmCalls())).append(" | ").append(number(summary.avgTokens())).append(" | ")
                    .append(number(summary.avgDurationMs())).append(" | ")
                    .append(String.join(", ", summary.agentsSeen())).append(" |\n");
        }

        List<ScenarioSummary> problems = report.results().stream()
                .filter(summary -> !summary.failures().isEmpty()).toList();
        if (!problems.isEmpty()) {
            md.append("\n## Failures\n");
            for (ScenarioSummary summary : problems) {
                md.append("\n**").append(summary.id()).append(" — ").append(escape(summary.title())).append("**\n\n");
                summary.failures().forEach(failure -> md.append("- ").append(escape(failure)).append('\n'));
            }
        }

        List<ScenarioSummary> skipped = report.results().stream().filter(summary -> summary.reason() != null).toList();
        if (!skipped.isEmpty()) {
            md.append("\n## Not executed\n\n| Id | Scenario | Status | Reason |\n|---|---|---|---|\n");
            skipped.forEach(summary -> md.append("| ").append(summary.id()).append(" | ")
                    .append(escape(summary.title())).append(" | ").append(summary.status()).append(" | ")
                    .append(escape(summary.reason())).append(" |\n"));
        }

        md.append("\n## Prompt templates (SHA-256)\n\n");
        meta.promptSha256().forEach((file, hash) -> md.append("- `").append(file).append("`: `").append(hash).append("`\n"));
        return md.toString();
    }

    /** The commit the run was made from, marked "+dirty" when the working tree has uncommitted changes. */
    public static String gitCommit() {
        String commit = git("rev-parse", "--short", "HEAD");
        if (commit == null) {
            return "unknown";
        }
        String status = git("status", "--porcelain");
        return status != null && !status.isBlank() ? commit + "+dirty" : commit;
    }

    private static String git(String... args) {
        try {
            List<String> command = new ArrayList<>(List.of("git"));
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            return output;
        } catch (IOException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Double rate(int passes, int runs) {
        return runs == 0 ? null : (double) passes / runs;
    }

    private static Double avg(OptionalDouble value) {
        return value.isPresent() ? Math.round(value.getAsDouble() * 10) / 10.0 : null;
    }

    private static String percent(Double rate) {
        return rate == null ? "–" : Math.round(rate * 1000) / 10.0 + " %";
    }

    private static String number(Double value) {
        return value == null ? "–" : String.valueOf(value);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }
}
