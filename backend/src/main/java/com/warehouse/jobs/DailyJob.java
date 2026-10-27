package com.warehouse.jobs;

import com.warehouse.common.idempotency.IdempotencyRecordRepository;
import com.warehouse.config.AppProperties;
import com.warehouse.forecast.dto.ForecastRunResult;
import com.warehouse.forecast.service.ForecastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Nightly maintenance: refresh usage statistics and reorder suggestions, and purge expired
 * idempotency records. Also runs once at startup, because free-tier hosts sleep and may miss the
 * scheduled time. Safe to run repeatedly.
 */
@Component
public class DailyJob {

    private static final Logger log = LoggerFactory.getLogger(DailyJob.class);

    private final ForecastService forecastService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AppProperties properties;
    private final boolean runOnStartup;
    private final Clock clock;

    public DailyJob(ForecastService forecastService, IdempotencyRecordRepository idempotencyRepository,
                    AppProperties properties, @Value("${app.jobs.run-on-startup:true}") boolean runOnStartup,
                    Clock clock) {
        this.forecastService = forecastService;
        this.idempotencyRepository = idempotencyRepository;
        this.properties = properties;
        this.runOnStartup = runOnStartup;
        this.clock = clock;
    }

    public record Result(ForecastRunResult forecast, int idempotencyRecordsPurged) {
    }

    @Scheduled(cron = "${app.jobs.daily-cron}", zone = "UTC")
    public void scheduled() {
        runSafely("scheduled");
    }

    /** Runs in the background so startup (and the health check) is not delayed by the forecast run. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (runOnStartup) {
            Thread.ofVirtual().name("daily-job-startup").start(() -> runSafely("startup"));
        }
    }

    public Result run() {
        ForecastRunResult forecast = forecastService.recompute();
        int purged = purgeIdempotencyRecords();
        log.info("Daily job finished: {} reorder suggestions, {} idempotency records purged",
                forecast.suggestions(), purged);
        return new Result(forecast, purged);
    }

    private int purgeIdempotencyRecords() {
        var cutoff = clock.instant().atZone(ZoneOffset.UTC).minus(properties.stock().idempotencyRetention()).toInstant();
        return idempotencyRepository.deleteOlderThan(cutoff);
    }

    private void runSafely(String trigger) {
        try {
            run();
        } catch (RuntimeException e) {
            log.error("Daily job ({}) failed", trigger, e);
        }
    }
}
