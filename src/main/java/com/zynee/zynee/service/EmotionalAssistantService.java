package com.zynee.zynee.service;

import java.io.IOException;
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

@Service
public class EmotionalAssistantService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String assistantUrl;

    public EmotionalAssistantService(
            ObjectMapper objectMapper,
            @Value("${assistant.python.url:http://127.0.0.1:8001/chat}") String assistantUrl,
            @Value("${assistant.python.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${assistant.python.read-timeout-ms:30000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.assistantUrl = assistantUrl == null ? "" : assistantUrl.trim();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(connectTimeoutMs, 1000));
        requestFactory.setReadTimeout(Math.max(readTimeoutMs, 1000));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public String getAssistantReply(
            String userMessage,
            String countryCode,
            List<Map<String, String>> history) throws IOException {
        if (assistantUrl.isBlank()) {
            throw new IllegalStateException("assistant.python.url is not configured.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", userMessage);
        payload.put("countryCode", countryCode == null ? "" : countryCode.trim());
        payload.put("history", history == null ? List.of() : history);

        String requestBody = objectMapper.writeValueAsString(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(assistantUrl, HttpMethod.POST, requestEntity, String.class);
        } catch (HttpStatusCodeException ex) {
            String errorMessage = extractServiceErrorMessage(ex.getResponseBodyAsString());
            throw new IOException(
                    "Local assistant service error (" + ex.getStatusCode().value() + "): " + errorMessage,
                    ex);
        } catch (ResourceAccessException ex) {
            throw new IOException(
                    "Could not reach local assistant service at " + assistantUrl
                            + ". Start the Python microservice and retry.",
                    ex);
        } catch (RestClientException ex) {
            throw new IOException("Failed to call local assistant service: " + ex.getMessage(), ex);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            String errorMessage = extractServiceErrorMessage(response.getBody());
            throw new IOException("Local assistant service error (" + response.getStatusCode().value()
                    + "): " + errorMessage);
        }

        String responseBody = response.getBody() == null ? "" : response.getBody();
        JsonNode root = objectMapper.readTree(responseBody);
        String reply = root.path("reply").asText("").trim();
        if (reply.isEmpty()) {
            throw new IOException("Local assistant service returned an empty response.");
        }
        return reply;
    }

    private String extractServiceErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String detail = root.path("detail").asText("").trim();
            if (!detail.isEmpty()) {
                return detail;
            }

            String error = root.path("error").asText("").trim();
            if (!error.isEmpty()) {
                return error;
            }
        } catch (IOException | RuntimeException ignored) {
            // Fall back to generic parsing below.
        }

        String body = responseBody == null ? "" : responseBody.trim();
        return body.isEmpty() ? "Unknown local assistant error." : body;
    }
}
