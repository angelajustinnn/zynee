package com.zynee.zynee.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class LocalAssistantProcessManager {

    private static final Logger log = LoggerFactory.getLogger(LocalAssistantProcessManager.class);

    private final boolean autoStartEnabled;
    private final String healthUrl;
    private final String workingDir;
    private final String pythonBin;
    private final long startTimeoutMs;
    private final boolean ollamaAutoStartEnabled;
    private final String ollamaHealthUrl;
    private final String ollamaCommand;
    private final long ollamaStartTimeoutMs;

    private volatile Process startedProcess;
    private volatile Process startedOllamaProcess;
    private volatile long lastOnDemandEnsureAttemptMs = 0L;

    public LocalAssistantProcessManager(
            @Value("${assistant.python.auto-start:true}") boolean autoStartEnabled,
            @Value("${assistant.python.health-url:http://127.0.0.1:8001/health}") String healthUrl,
            @Value("${assistant.python.working-dir:assistant-microservice}") String workingDir,
            @Value("${assistant.python.python-bin:.venv/bin/python3}") String pythonBin,
            @Value("${assistant.python.start-timeout-ms:15000}") long startTimeoutMs,
            @Value("${assistant.ollama.auto-start:true}") boolean ollamaAutoStartEnabled,
            @Value("${assistant.ollama.health-url:http://127.0.0.1:11434/api/tags}") String ollamaHealthUrl,
            @Value("${assistant.ollama.command:ollama}") String ollamaCommand,
            @Value("${assistant.ollama.start-timeout-ms:10000}") long ollamaStartTimeoutMs) {
        this.autoStartEnabled = autoStartEnabled;
        this.healthUrl = healthUrl == null ? "" : healthUrl.trim();
        this.workingDir = workingDir == null ? "assistant-microservice" : workingDir.trim();
        this.pythonBin = pythonBin == null ? ".venv/bin/python3" : pythonBin.trim();
        this.startTimeoutMs = Math.max(startTimeoutMs, 3000L);
        this.ollamaAutoStartEnabled = ollamaAutoStartEnabled;
        this.ollamaHealthUrl = ollamaHealthUrl == null ? "" : ollamaHealthUrl.trim();
        this.ollamaCommand = ollamaCommand == null ? "ollama" : ollamaCommand.trim();
        this.ollamaStartTimeoutMs = Math.max(ollamaStartTimeoutMs, 3000L);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureAssistantRunning() {
        ensureAssistantRunningInternal(true);
    }

    public void ensureAssistantRunningOnDemand() {
        ensureAssistantRunningInternal(false);
    }

    private void ensureAssistantRunningInternal(boolean startupPhase) {
        if (!autoStartEnabled) {
            if (startupPhase) {
                log.info("Local assistant auto-start is disabled.");
            }
            return;
        }
        if (healthUrl.isBlank()) {
            if (startupPhase) {
                log.warn("assistant.python.health-url is blank; skipping assistant auto-start.");
            }
            return;
        }

        if (!startupPhase && isHealthy(healthUrl)) {
            return;
        }

        synchronized (this) {
            if (isHealthy(healthUrl)) {
                if (startupPhase) {
                    log.info("Local assistant is already running.");
                }
                return;
            }

            if (!startupPhase) {
                long now = System.currentTimeMillis();
                if (now - lastOnDemandEnsureAttemptMs < 2000L) {
                    return;
                }
                lastOnDemandEnsureAttemptMs = now;
            }

            ensureOllamaRunning();
            if (isHealthy(healthUrl)) {
                return;
            }

            File assistantDir = resolveAssistantDir();
            if (!assistantDir.isDirectory()) {
                log.warn("Assistant directory not found at {}. Cannot auto-start local assistant.", assistantDir.getAbsolutePath());
                return;
            }

            List<String> command = buildStartCommand(assistantDir);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(assistantDir);
            pb.redirectErrorStream(true);

            try {
                startedProcess = pb.start();
                streamProcessLogs(startedProcess);
                if (waitUntilHealthy(healthUrl, Duration.ofMillis(startTimeoutMs))) {
                    log.info("Local assistant auto-started successfully at {}", healthUrl);
                } else {
                    log.warn("Started local assistant process, but health check did not pass in {} ms.", startTimeoutMs);
                }
            } catch (IOException ex) {
                log.warn("Failed to auto-start local assistant process: {}", ex.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdownStartedProcess() {
        Process process = startedProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        Process ollamaProcess = startedOllamaProcess;
        if (ollamaProcess != null && ollamaProcess.isAlive()) {
            ollamaProcess.destroy();
        }
    }

    private File resolveAssistantDir() {
        File configured = new File(workingDir);
        if (configured.isAbsolute()) {
            return configured;
        }
        File projectDir = new File(System.getProperty("user.dir", "."));
        return new File(projectDir, workingDir);
    }

    private List<String> buildStartCommand(File assistantDir) {
        List<String> command = new ArrayList<>();

        File configuredBin = resolvePythonBin(assistantDir);
        if (configuredBin.exists() && configuredBin.canExecute()) {
            command.add(configuredBin.getAbsolutePath());
        } else {
            command.add("python3");
        }

        command.add("-m");
        command.add("uvicorn");
        command.add("app:app");
        command.add("--host");
        command.add("127.0.0.1");
        command.add("--port");
        command.add("8001");
        return command;
    }

    private File resolvePythonBin(File assistantDir) {
        File configured = new File(pythonBin);
        if (configured.isAbsolute()) {
            return configured;
        }
        return new File(assistantDir, pythonBin);
    }

    private boolean waitUntilHealthy(String targetUrl, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (isHealthy(targetUrl)) {
                return true;
            }
            LockSupport.parkNanos(Duration.ofMillis(750L).toNanos());
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isHealthy(targetUrl);
    }

    private boolean isHealthy(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            int status = connection.getResponseCode();
            return status >= 200 && status < 300;
        } catch (IOException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private void ensureOllamaRunning() {
        if (!ollamaAutoStartEnabled) {
            return;
        }
        if (ollamaHealthUrl.isBlank()) {
            log.warn("assistant.ollama.health-url is blank; skipping Ollama auto-start.");
            return;
        }
        if (isHealthy(ollamaHealthUrl)) {
            return;
        }

        List<String> command = List.of(ollamaCommand, "serve");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            startedOllamaProcess = pb.start();
            streamProcessLogs(startedOllamaProcess);
            if (waitUntilHealthy(ollamaHealthUrl, Duration.ofMillis(ollamaStartTimeoutMs))) {
                log.info("Ollama auto-started successfully.");
            } else {
                log.warn("Started Ollama process, but health check did not pass in {} ms.", ollamaStartTimeoutMs);
            }
        } catch (IOException ex) {
            log.warn("Could not auto-start Ollama using command '{} serve': {}", ollamaCommand, ex.getMessage());
        }
    }

    private void streamProcessLogs(Process process) {
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[local-assistant] {}", line);
                }
            } catch (IOException ignored) {
                // Ignore log stream termination errors.
            }
        }, "local-assistant-log-stream");
        logThread.setDaemon(true);
        logThread.start();
    }
}
