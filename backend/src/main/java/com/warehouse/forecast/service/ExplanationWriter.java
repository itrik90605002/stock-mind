package com.warehouse.forecast.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.ai.AiUnavailableException;
import com.warehouse.ai.ChatClient;
import com.warehouse.ai.JsonReplies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the one-line "why" behind each reorder suggestion.
 *
 * <p>A deterministic template explanation is always produced from the numbers. When AI is
 * available, the most urgent suggestions are re-phrased by the model in one batched call; the
 * model is given only the computed facts and its output is length-checked. The quantities never
 * come from the model.
 */
@Component
public class ExplanationWriter {

    private static final Logger log = LoggerFactory.getLogger(ExplanationWriter.class);
    private static final int MAX_AI_TEXT = 220;

    private static final String SYSTEM_PROMPT = """
            You explain inventory reorder suggestions to warehouse managers.
            For each item, write ONE plain sentence (max 160 characters) saying why it should be reordered now.
            Use only the facts provided; never invent numbers, suppliers, events or causes.
            Mention the most decision-relevant facts: days of stock left versus lead time, usage trend, stock already on order.
            Reply with JSON only: {"explanations":[{"id":<id>,"text":"<sentence>"}]}
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ExplanationWriter(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /** Facts about one suggestion, as sent to the model. */
    public record Facts(int id, String sku, String product, String warehouse, String unit, double avgDailyUsage,
                        Double trendPct, Double daysOfCover, int leadTimeDays, int available, int incoming,
                        int safetyStock, int suggestedQty) {
    }

    public static String template(Facts f, int reviewPeriodDays) {
        StringBuilder sb = new StringBuilder();
        if (f.available() <= 0) {
            sb.append("Out of stock");
            sb.append(String.format(Locale.ROOT, " (recent usage ~%s %s/day)", number(f.avgDailyUsage()), f.unit()));
            if (f.incoming() > 0) {
                sb.append(String.format(Locale.ROOT, "; %d already on order", f.incoming()));
            }
            sb.append(String.format(Locale.ROOT, ". Order %d to cover %d days plus %d safety stock.",
                    f.suggestedQty(), f.leadTimeDays() + reviewPeriodDays, f.safetyStock()));
            return sb.toString();
        }
        sb.append(String.format(Locale.ROOT, "Uses ~%s %s/day", number(f.avgDailyUsage()), f.unit()));
        if (f.trendPct() != null && Math.abs(f.trendPct()) >= 10) {
            sb.append(String.format(Locale.ROOT, " (%s %.0f%% vs prior 2 weeks)", f.trendPct() > 0 ? "up" : "down",
                    Math.abs(f.trendPct())));
        }
        if (f.daysOfCover() != null) {
            sb.append(String.format(Locale.ROOT, "; %s days of stock left vs %d-day lead time",
                    number(f.daysOfCover()), f.leadTimeDays()));
        } else {
            sb.append(String.format(Locale.ROOT, "; %d available, at or below reorder point", f.available()));
        }
        if (f.incoming() > 0) {
            sb.append(String.format(Locale.ROOT, "; %d already on order", f.incoming()));
        }
        sb.append(String.format(Locale.ROOT, ". Order %d to cover %d days plus %d safety stock.",
                f.suggestedQty(), f.leadTimeDays() + reviewPeriodDays, f.safetyStock()));
        return sb.toString();
    }

    /**
     * Returns AI-written explanations keyed by fact id, or an empty map when AI is unavailable
     * or the reply is unusable. Never throws.
     */
    public Map<Integer, String> aiExplanations(List<Facts> facts) {
        if (facts.isEmpty() || !chatClient.isAvailable()) {
            return Map.of();
        }
        try {
            String reply = chatClient.completeJson(SYSTEM_PROMPT, objectMapper.writeValueAsString(
                    Map.of("items", facts.stream().map(ExplanationWriter::payload).toList())));
            JsonNode items = objectMapper.readTree(JsonReplies.extractObject(reply)).path("explanations");
            Map<Integer, String> valid = new HashMap<>();
            for (JsonNode item : items) {
                int id = item.path("id").asInt(-1);
                String text = item.path("text").asText("").replaceAll("\\s+", " ").trim();
                boolean known = facts.stream().anyMatch(f -> f.id() == id);
                if (known && !text.isEmpty() && text.length() <= MAX_AI_TEXT) {
                    valid.put(id, text);
                }
            }
            return valid;
        } catch (AiUnavailableException e) {
            return Map.of();
        } catch (Exception e) {
            log.warn("Discarding unusable AI explanations: {}", e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, Object> payload(Facts f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.id());
        m.put("product", f.sku() + " " + f.product());
        m.put("warehouse", f.warehouse());
        m.put("unit", f.unit());
        m.put("avgDailyUsage", round(f.avgDailyUsage()));
        m.put("outOfStock", f.available() <= 0);
        // A falling trend on an out-of-stock item only reflects that nothing could be issued; don't show it.
        m.put("usageTrendPctVsPrior2Weeks", f.trendPct() == null || f.available() <= 0 ? null : Math.round(f.trendPct()));
        m.put("daysOfStockLeft", f.daysOfCover() == null ? null : round(f.daysOfCover()));
        m.put("leadTimeDays", f.leadTimeDays());
        m.put("available", f.available());
        m.put("alreadyOnOrder", f.incoming());
        m.put("suggestedOrderQty", f.suggestedQty());
        return m;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static String number(double value) {
        return value >= 10 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
