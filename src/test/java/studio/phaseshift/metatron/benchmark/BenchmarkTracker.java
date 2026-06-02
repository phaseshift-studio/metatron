/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks benchmark results over time and detects performance regressions.
 * <p>
 * Results are saved to the benchmark/ folder in the project root.
 * Each benchmark suite has a baseline file and historical run files.
 */
public class BenchmarkTracker {

    private static final Path BENCHMARK_DIR = Paths.get("benchmark");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    /**
     * Maximum allowed regression percentage before failing tests.
     * A value of 0.20 means 20% slower than baseline will fail.
     */
    private static final double DEFAULT_REGRESSION_THRESHOLD = 0.20;

    /**
     * Represents a single benchmark measurement
     */
    public record Measurement(
            String name,
            double avgNanos,
            double minNanos,
            double maxNanos,
            double opsPerSecond,
            int iterations
    ) {
        public double avgMicros() {
            return avgNanos / 1000.0;
        }
    }

    /**
     * Represents a complete benchmark run
     */
    public record BenchmarkRun(
            String suiteName,
            String timestamp,
            String gitCommit,
            String javaVersion,
            Map<String, Measurement> measurements
    ) {
        public static BenchmarkRun create(String suiteName, Map<String, Measurement> measurements) {
            return new BenchmarkRun(
                    suiteName,
                    Instant.now().toString(),
                    getGitCommit(),
                    System.getProperty("java.version"),
                    measurements
            );
        }

        private static String getGitCommit() {
            try {
                ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--short", "HEAD");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String commit = new String(p.getInputStream().readAllBytes()).trim();
                p.waitFor();
                return commit;
            } catch (Exception e) {
                return "unknown";
            }
        }
    }

    /**
     * Comparison result for a single measurement
     */
    public record ComparisonResult(
            String name,
            double baselineAvgMicros,
            double currentAvgMicros,
            double changePercent,
            boolean isRegression,
            boolean failsThreshold
    ) {
        @Override
        public String toString() {
            String arrow = changePercent > 0 ? "↑" : (changePercent < 0 ? "↓" : "→");
            String status = failsThreshold ? "FAIL" : (isRegression ? "WARN" : "OK");
            return String.format("[%s] %s: %.2fμs → %.2fμs (%s%.1f%%) %s",
                    status, name, baselineAvgMicros, currentAvgMicros,
                    changePercent > 0 ? "+" : "", changePercent * 100, arrow);
        }
    }

    private final String suiteName;
    private final double regressionThreshold;
    private final Map<String, Measurement> measurements = new LinkedHashMap<>();

    public BenchmarkTracker(String suiteName) {
        this(suiteName, DEFAULT_REGRESSION_THRESHOLD);
    }

    public BenchmarkTracker(String suiteName, double regressionThreshold) {
        this.suiteName = suiteName;
        this.regressionThreshold = regressionThreshold;
    }

    /**
     * Record a benchmark measurement
     */
    public void record(String name, double avgNanos, double minNanos, double maxNanos,
                       double opsPerSecond, int iterations) {
        measurements.put(name, new Measurement(name, avgNanos, minNanos, maxNanos, opsPerSecond, iterations));
    }

    /**
     * Save current run and compare against baseline.
     *
     * @param updateBaseline if true, update the baseline with current results
     * @return list of comparison results
     * @throws BenchmarkRegressionException if any measurement exceeds regression threshold
     */
    public List<ComparisonResult> saveAndCompare(boolean updateBaseline) throws IOException {
        ensureBenchmarkDir();

        BenchmarkRun currentRun = BenchmarkRun.create(suiteName, measurements);

        // Save current run with timestamp
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        Path runFile = BENCHMARK_DIR.resolve(suiteName + "_" + timestamp + ".json");
        Files.writeString(runFile, GSON.toJson(currentRun));

        // Load baseline if exists
        Path baselineFile = getBaselinePath();
        BenchmarkRun baseline = loadBaseline();

        List<ComparisonResult> results = new ArrayList<>();
        List<ComparisonResult> failures = new ArrayList<>();

        if (baseline != null) {
            for (Map.Entry<String, Measurement> entry : measurements.entrySet()) {
                String name = entry.getKey();
                Measurement current = entry.getValue();
                Measurement base = baseline.measurements().get(name);

                if (base != null) {
                    double changePercent = (current.avgNanos() - base.avgNanos()) / base.avgNanos();
                    boolean isRegression = changePercent > 0.05; // 5% slower
                    boolean failsThreshold = changePercent > regressionThreshold;

                    ComparisonResult result = new ComparisonResult(
                            name, base.avgMicros(), current.avgMicros(),
                            changePercent, isRegression, failsThreshold
                    );
                    results.add(result);

                    if (failsThreshold) {
                        failures.add(result);
                    }
                }
            }
        }

        // Update baseline if requested or if no baseline exists
        if (updateBaseline || baseline == null) {
            Files.writeString(baselineFile, GSON.toJson(currentRun));
            System.out.println("Updated baseline: " + baselineFile);
        }

        // Print comparison results
        if (!results.isEmpty()) {
            System.out.println("\n=== Benchmark Comparison vs Baseline ===");
            for (ComparisonResult r : results) {
                System.out.println(r);
            }
            System.out.println("=========================================\n");
        }

        // Throw exception if any failures
        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder("Benchmark regression detected!\n");
            for (ComparisonResult f : failures) {
                sb.append("  ").append(f).append("\n");
            }
            throw new BenchmarkRegressionException(sb.toString(), failures);
        }

        return results;
    }

    /**
     * Load the baseline benchmark run if it exists
     */
    public BenchmarkRun loadBaseline() throws IOException {
        Path baselineFile = getBaselinePath();
        if (Files.exists(baselineFile)) {
            String json = Files.readString(baselineFile);
            return GSON.fromJson(json, BenchmarkRun.class);
        }
        return null;
    }

    /**
     * Get list of all historical runs
     */
    public List<BenchmarkRun> loadHistory() throws IOException {
        ensureBenchmarkDir();
        List<BenchmarkRun> history = new ArrayList<>();

        try (var stream = Files.list(BENCHMARK_DIR)) {
            stream.filter(p -> p.getFileName().toString().startsWith(suiteName + "_"))
                  .filter(p -> p.toString().endsWith(".json"))
                  .sorted()
                  .forEach(p -> {
                      try {
                          String json = Files.readString(p);
                          history.add(GSON.fromJson(json, BenchmarkRun.class));
                      } catch (IOException e) {
                          // Skip invalid files
                      }
                  });
        }

        return history;
    }

    private Path getBaselinePath() {
        return BENCHMARK_DIR.resolve(suiteName + "_baseline.json");
    }

    private void ensureBenchmarkDir() throws IOException {
        if (!Files.exists(BENCHMARK_DIR)) {
            Files.createDirectories(BENCHMARK_DIR);
        }
    }

    /**
     * Exception thrown when benchmark regression exceeds threshold
     */
    public static class BenchmarkRegressionException extends RuntimeException {
        private final List<ComparisonResult> failures;

        public BenchmarkRegressionException(String message, List<ComparisonResult> failures) {
            super(message);
            this.failures = failures;
        }

        public List<ComparisonResult> getFailures() {
            return failures;
        }
    }
}
