package com.warehouse.jobs;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets an admin run the daily job on demand. */
@RestController
@RequestMapping("/api/jobs")
@PreAuthorize("hasRole('ADMIN')")
public class JobController {

    private final DailyJob dailyJob;

    public JobController(DailyJob dailyJob) {
        this.dailyJob = dailyJob;
    }

    @PostMapping("/daily")
    public DailyJob.Result runDaily() {
        return dailyJob.run();
    }
}
