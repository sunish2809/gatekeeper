package com.project.gatekeeper.analytics.service;

import com.project.gatekeeper.analytics.domain.AccessEventDocument;
import com.project.gatekeeper.analytics.repo.AccessEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsQueryService {

    private final AccessEventRepository events;

    public AnalyticsQueryService(AccessEventRepository events) {
        this.events = events;
    }

    public List<AccessEventDocument> recent(String clientId, int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        PageRequest page = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        if (clientId == null || clientId.isBlank()) {
            return events.findAll(page).getContent();
        }
        return events.findByClientId(clientId, page);
    }

    public Summary summarize(String clientId) {
        List<AccessEventDocument> rows = recent(clientId, 200);
        long errors = rows.stream().filter(row -> row.getStatus() >= 400).count();
        double avg = rows.stream().mapToLong(AccessEventDocument::getLatencyMs).average().orElse(0);
        Map<String, long[]> byRoute = new LinkedHashMap<>();
        for (AccessEventDocument row : rows) {
            String routeId = row.getRouteId() == null ? "unmatched" : row.getRouteId();
            long[] stats = byRoute.computeIfAbsent(routeId, key -> new long[2]);
            stats[0]++;
            stats[1] += row.getLatencyMs();
        }
        List<RouteSummary> routes = new ArrayList<>();
        byRoute.forEach((routeId, stats) -> routes.add(new RouteSummary(routeId, stats[0], stats[0] == 0 ? 0 : (double) stats[1] / stats[0])));
        return new Summary(rows.size(), errors, avg, routes);
    }

    public Overview overview(String clientId) {
        List<AccessEventDocument> newestFirst = recent(clientId, 500);
        List<AccessEventDocument> rows = new ArrayList<>(newestFirst);
        rows.sort(Comparator.comparing(row -> row.getTimestamp() == null ? Instant.EPOCH : row.getTimestamp()));
        long success = rows.stream().filter(row -> row.getStatus() >= 200 && row.getStatus() < 400).count();
        long clientErrors = rows.stream().filter(row -> row.getStatus() >= 400 && row.getStatus() < 500).count();
        long serverErrors = rows.stream().filter(row -> row.getStatus() >= 500).count();
        long rateLimited = rows.stream().filter(row -> row.getStatus() == 429 || "rate_limited".equals(row.getError())).count();
        long[] latency = rows.stream().mapToLong(AccessEventDocument::getLatencyMs).sorted().toArray();
        double avg = latency.length == 0 ? 0 : java.util.Arrays.stream(latency).average().orElse(0);
        long p95 = latency.length == 0 ? 0 : latency[Math.min(latency.length - 1, (int) Math.ceil(latency.length * 0.95) - 1)];
        return new Overview(rows.size(), success, clientErrors, serverErrors, rateLimited, round(avg), p95,
                series(rows), statuses(rows), paths(rows), recentRows(newestFirst));
    }

    private List<Bucket> series(List<AccessEventDocument> rows) {
        List<AccessEventDocument> timed = rows.stream().filter(row -> row.getTimestamp() != null).toList();
        if (timed.isEmpty()) {
            return List.of();
        }
        Instant start = timed.get(0).getTimestamp();
        Instant end = timed.get(timed.size() - 1).getTimestamp();
        long span = Math.max(1, end.toEpochMilli() - start.toEpochMilli());
        long bucketMs = span <= 3_600_000 ? 300_000 : 3_600_000;
        Map<Long, long[]> buckets = new LinkedHashMap<>();
        for (AccessEventDocument row : timed) {
            long index = (row.getTimestamp().toEpochMilli() - start.toEpochMilli()) / bucketMs;
            long[] stats = buckets.computeIfAbsent(index, key -> new long[3]);
            stats[0]++;
            stats[1] += row.getLatencyMs();
            if (row.getStatus() >= 400) {
                stats[2]++;
            }
        }
        List<Bucket> points = new ArrayList<>();
        buckets.forEach((index, stats) -> points.add(new Bucket(
                start.plusMillis(index * bucketMs).toString(),
                stats[0],
                stats[2],
                stats[0] == 0 ? 0 : round((double) stats[1] / stats[0]))));
        return points;
    }

    private List<StatusCount> statuses(List<AccessEventDocument> rows) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (AccessEventDocument row : rows) {
            counts.merge(row.getStatus(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(6)
                .map(entry -> new StatusCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<PathStat> paths(List<AccessEventDocument> rows) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (AccessEventDocument row : rows) {
            String path = row.getPath() == null ? "/" : row.getPath();
            long[] stats = counts.computeIfAbsent(path, key -> new long[3]);
            stats[0]++;
            stats[1] += row.getLatencyMs();
            if (row.getStatus() >= 400) {
                stats[2]++;
            }
        }
        return counts.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue()[0], left.getValue()[0]))
                .limit(6)
                .map(entry -> new PathStat(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[0] == 0 ? 0 : round((double) entry.getValue()[1] / entry.getValue()[0]),
                        entry.getValue()[2]))
                .toList();
    }

    private List<RecentCall> recentRows(List<AccessEventDocument> newestFirst) {
        List<RecentCall> recent = new ArrayList<>();
        for (AccessEventDocument row : newestFirst) {
            if (recent.size() == 8) {
                break;
            }
            recent.add(new RecentCall(row.getMethod(), row.getPath(), row.getStatus(), row.getLatencyMs(),
                    row.getTimestamp() == null ? null : row.getTimestamp().toString(), row.getError(), row.getCorrelationId()));
        }
        return recent;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record Summary(long total, long errors, double avgLatencyMs, List<RouteSummary> routes) {
    }

    public record Overview(long total, long success, long clientErrors, long serverErrors, long rateLimited,
                           double avgLatencyMs, long p95LatencyMs, List<Bucket> series, List<StatusCount> statuses,
                           List<PathStat> paths, List<RecentCall> recent) {
    }

    public record Bucket(String time, long count, long errors, double avgLatencyMs) {
    }

    public record StatusCount(int status, long count) {
    }

    public record PathStat(String path, long count, double avgLatencyMs, long errors) {
    }

    public record RecentCall(String method, String path, int status, long latencyMs, String timestamp, String error, String correlationId) {
    }

    public record RouteSummary(String routeId, long total, double avgLatencyMs) {
    }
}
