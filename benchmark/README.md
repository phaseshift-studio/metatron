# Benchmark Results

This folder contains benchmark results for performance tracking.

## Files

- `*_baseline.json` - The baseline to compare against (committed to git)
- `*_YYYY-MM-DD_HH-mm-ss.json` - Historical run data (can be git-ignored or kept for history)

## Running Benchmarks

### Quick summary (no tracking):
```bash
mvn test -Dtest=InstResolutionBenchmarkTest#benchmarkSummary
```

### Tracked benchmark (compares to baseline, fails on regression):
```bash
mvn test -Dtest=InstResolutionBenchmarkTest#benchmarkTracked
```

### Update baseline (after intentional performance changes):
```bash
mvn test -Dtest=InstResolutionBenchmarkTest#benchmarkUpdateBaseline -DskipTests=false
```

## Regression Threshold

The default regression threshold is **20%**. If any benchmark is more than 20% slower than the baseline, the test will fail.

## Adding New Benchmarks

1. Add the benchmark operation in `InstResolutionBenchmarkTest.java`
2. Record it in both `benchmarkTracked()` and `benchmarkUpdateBaseline()`
3. Run `benchmarkUpdateBaseline` to create a new baseline with the new metric
