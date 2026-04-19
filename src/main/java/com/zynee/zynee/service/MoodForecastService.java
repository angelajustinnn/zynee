package com.zynee.zynee.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zynee.zynee.model.MoodLog;

@Service
public class MoodForecastService {

    public record MoodForecast(
            String todayMood,
            int todayConfidence,
            String tomorrowMood,
            int tomorrowConfidence,
            String source,
            int logsUsed) {
    }

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String forecastUrl;

    public MoodForecastService(
            ObjectMapper objectMapper,
            @Value("${assistant.python.mood-url:http://127.0.0.1:8001/mood-forecast}") String forecastUrl,
            @Value("${assistant.python.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${assistant.python.read-timeout-ms:30000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.forecastUrl = forecastUrl == null ? "" : forecastUrl.trim();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(connectTimeoutMs, 1000));
        requestFactory.setReadTimeout(Math.max(readTimeoutMs, 1000));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public MoodForecast predictFromMicroservice(List<MoodLog> logs) throws IOException {
        if (forecastUrl.isBlank()) {
            throw new IOException("assistant.python.mood-url is not configured.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("logs", toMicroserviceLogs(logs));

        String requestBody = objectMapper.writeValueAsString(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(forecastUrl, HttpMethod.POST, requestEntity, String.class);
        } catch (HttpStatusCodeException ex) {
            throw new IOException("Mood forecast microservice error (" + ex.getStatusCode().value() + "): "
                    + safeErrorMessage(ex.getResponseBodyAsString()), ex);
        } catch (ResourceAccessException ex) {
            throw new IOException("Could not reach local mood forecast microservice at " + forecastUrl, ex);
        } catch (RestClientException ex) {
            throw new IOException("Failed to call local mood forecast microservice: " + ex.getMessage(), ex);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IOException("Mood forecast microservice error (" + response.getStatusCode().value() + "): "
                    + safeErrorMessage(response.getBody()));
        }

        String body = response.getBody() == null ? "" : response.getBody();
        JsonNode root = objectMapper.readTree(body);

        String todayMood = normalizeMood(root.path("todayMood").asText("Calm"));
        int todayConfidence = clampConfidence(root.path("todayConfidence").asInt(40));
        String tomorrowMood = normalizeMood(root.path("tomorrowMood").asText(todayMood));
        int tomorrowConfidence = clampConfidence(root.path("tomorrowConfidence").asInt(Math.max(30, todayConfidence - 4)));
        int logsUsed = Math.max(0, root.path("logsUsed").asInt(logs == null ? 0 : logs.size()));

        return new MoodForecast(todayMood, todayConfidence, tomorrowMood, tomorrowConfidence, "microservice", logsUsed);
    }

    public MoodForecast predictFallback(List<MoodLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return new MoodForecast("Calm", 35, "Calm", 33, "fallback", 0);
        }

        List<MoodLog> ordered = logs.stream()
                .filter(log -> log != null && log.getTimestamp() != null)
                .sorted(Comparator.comparing(MoodLog::getTimestamp))
                .toList();

        if (ordered.isEmpty()) {
            return new MoodForecast("Calm", 35, "Calm", 33, "fallback", 0);
        }

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        LocalDateTime now = LocalDateTime.now();

        for (MoodLog log : ordered) {
            double level = clampScore(log.getMoodLevel());
            long ageHours = Math.max(0L, java.time.Duration.between(log.getTimestamp(), now).toHours());
            double ageDays = ageHours / 24.0;
            double weight = Math.exp(-ageDays / 10.0);
            weightedSum += level * weight;
            totalWeight += weight;
        }

        double base = totalWeight > 0.0 ? weightedSum / totalWeight : ordered.get(ordered.size() - 1).getMoodLevel();
        double trend = estimateTrend(ordered);

        double todayScore = clampScore(base + (trend * 0.25));
        double tomorrowScore = clampScore(todayScore + (trend * 0.35));

        int confidence = estimateConfidence(ordered.size(), ordered);
        int tomorrowConfidence = Math.max(30, confidence - 4);

        return new MoodForecast(
                scoreToMood(todayScore),
                confidence,
                scoreToMood(tomorrowScore),
                tomorrowConfidence,
                "fallback",
                ordered.size());
    }

    private List<Map<String, Object>> toMicroserviceLogs(List<MoodLog> logs) {
        List<MoodLog> safeLogs = logs == null ? List.of() : logs;

        return safeLogs.stream()
                .filter(log -> log != null && log.getTimestamp() != null)
                .sorted(Comparator.comparing(MoodLog::getTimestamp))
                .skip(Math.max(0, safeLogs.size() - 180))
                .map(log -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("moodLevel", Math.max(1, Math.min(5, log.getMoodLevel())));
                    item.put("timestamp", log.getTimestamp().toString());
                    item.put("feelings", log.getFeelings() == null ? List.of() : new ArrayList<>(log.getFeelings()));
                    return item;
                })
                .toList();
    }

    private int estimateConfidence(int count, List<MoodLog> ordered) {
        if (count <= 1) {
            return 35;
        }

        double mean = ordered.stream()
                .mapToDouble(log -> clampScore(log.getMoodLevel()))
                .average()
                .orElse(3.0);
        double variance = ordered.stream()
                .mapToDouble(log -> Math.pow(clampScore(log.getMoodLevel()) - mean, 2.0))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);
        double countFactor = Math.min(1.0, count / 35.0);
        double stability = 1.0 - Math.min(1.0, stdDev / 2.0);

        double confidence = (0.65 * countFactor) + (0.35 * stability);
        return clampConfidence((int) Math.round(confidence * 100.0));
    }

    private double estimateTrend(List<MoodLog> ordered) {
        if (ordered.size() < 4) {
            return 0.0;
        }

        int start = Math.max(0, ordered.size() - 8);
        List<MoodLog> tail = ordered.subList(start, ordered.size());
        int half = tail.size() / 2;
        if (half == 0) {
            return 0.0;
        }

        double firstHalfAvg = tail.subList(0, half).stream()
                .mapToDouble(log -> clampScore(log.getMoodLevel()))
                .average()
                .orElse(3.0);
        double secondHalfAvg = tail.subList(half, tail.size()).stream()
                .mapToDouble(log -> clampScore(log.getMoodLevel()))
                .average()
                .orElse(3.0);

        double trend = secondHalfAvg - firstHalfAvg;
        return Math.max(-1.2, Math.min(1.2, trend));
    }

    private String normalizeMood(String raw) {
        String cleaned = (raw == null ? "" : raw.trim()).replaceAll("\\s+", " ");
        if (cleaned.isBlank()) {
            return "Calm";
        }

        String[] parts = cleaned.split(" ");
        if (parts.length > 2) {
            cleaned = parts[0] + " " + parts[1];
        }

        return capitalizeWords(cleaned);
    }

    private String capitalizeWords(String value) {
        String[] words = value.split(" ");
        List<String> output = new ArrayList<>();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            String first = word.substring(0, 1).toUpperCase();
            String rest = word.length() > 1 ? word.substring(1).toLowerCase() : "";
            output.add(first + rest);
            if (output.size() == 2) {
                break;
            }
        }
        return output.isEmpty() ? "Calm" : String.join(" ", output);
    }

    private String scoreToMood(double score) {
        if (score <= 1.6) {
            return "Very Sad";
        }
        if (score <= 2.4) {
            return "Low";
        }
        if (score <= 3.3) {
            return "Calm";
        }
        if (score <= 4.1) {
            return "Good";
        }
        return "Happy";
    }

    private double clampScore(double score) {
        return Math.max(1.0, Math.min(5.0, score));
    }

    private int clampConfidence(int value) {
        return Math.max(30, Math.min(96, value));
    }

    private String safeErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Unknown local forecast service error.";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String detail = root.path("detail").asText("").trim();
            if (!detail.isEmpty()) {
                return detail;
            }
            String error = root.path("error").asText("").trim();
            if (!error.isEmpty()) {
                return error;
            }
        } catch (IOException ignored) {
            // ignore parse errors and fall back to raw body
        }

        return body.trim();
    }
}
