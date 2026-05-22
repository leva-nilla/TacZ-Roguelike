package com.levanilla.rogue.core.smoke;

import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class SmokeLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("TacRogueSmoke");
    private static final String PREFIX = "[TacRogue][SMOKE]";
    private static String activeRunId;

    private SmokeLogger() {}

    public static synchronized String startRun(String suite) {
        activeRunId = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            .replace(":", "")
            .replace(".", "_")
            + "-" + sanitize(suite);
        try {
            Files.createDirectories(logDir());
            Files.deleteIfExists(textLog());
            Files.deleteIfExists(jsonLog());
        } catch (IOException ex) {
            LOGGER.warn("{} failed to reset smoke logs: {}", PREFIX, ex.toString());
        }
        record(activeRunId, suite, "run.start", "PASS", "smoke run starts", "started", "", 0L);
        return activeRunId;
    }

    public static synchronized String activeRunId() {
        if (activeRunId == null || activeRunId.isBlank()) {
            return startRun("adhoc");
        }
        return activeRunId;
    }

    public static void pass(String suite, String caseId, String expected, String actual, String detail, long durationMs) {
        record(activeRunId(), suite, caseId, "PASS", expected, actual, detail, durationMs);
    }

    public static void fail(String suite, String caseId, String expected, String actual, String detail, long durationMs) {
        record(activeRunId(), suite, caseId, "FAIL", expected, actual, detail, durationMs);
    }

    public static void skip(String suite, String caseId, String expected, String actual, String detail, long durationMs) {
        record(activeRunId(), suite, caseId, "SKIP", expected, actual, detail, durationMs);
    }

    public static synchronized void record(String runId, String suite, String caseId, String status,
                                           String expected, String actual, String detail, long durationMs) {
        String safeRunId = runId == null || runId.isBlank() ? "adhoc" : runId;
        String line = String.format(Locale.ROOT, "%s [%s] %s/%s expected=\"%s\" actual=\"%s\" detail=\"%s\" durationMs=%d",
            PREFIX, status, suite, caseId, oneLine(expected), oneLine(actual), oneLine(detail), durationMs);
        LOGGER.info(line);
        try {
            Files.createDirectories(logDir());
            Files.writeString(textLog(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(jsonLog(), jsonLine(safeRunId, suite, caseId, status, expected, actual, detail, durationMs)
                + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            LOGGER.warn("{} failed to write smoke log: {}", PREFIX, ex.toString());
        }
    }

    public static Path logDir() {
        return FMLPaths.GAMEDIR.get().resolve("logs").resolve("tac_rogue_smoke");
    }

    public static Path textLog() {
        return logDir().resolve("smoke-latest.log");
    }

    public static Path jsonLog() {
        return logDir().resolve("smoke-latest.jsonl");
    }

    private static String jsonLine(String runId, String suite, String caseId, String status,
                                   String expected, String actual, String detail, long durationMs) {
        return "{"
            + "\"runId\":\"" + json(runId) + "\","
            + "\"suite\":\"" + json(suite) + "\","
            + "\"caseId\":\"" + json(caseId) + "\","
            + "\"status\":\"" + json(status) + "\","
            + "\"expected\":\"" + json(expected) + "\","
            + "\"actual\":\"" + json(actual) + "\","
            + "\"detail\":\"" + json(detail) + "\","
            + "\"durationMs\":" + Math.max(0L, durationMs)
            + "}";
    }

    private static String sanitize(String value) {
        return value == null ? "unknown" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String oneLine(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String json(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
