package com.finance.config;

import com.finance.service.RecurringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.recurring.enabled", havingValue = "true")
public class RecurringJob {
    private final RecurringService recurring;
    public RecurringJob(RecurringService recurring) { this.recurring = recurring; }
    @Scheduled(fixedDelayString = "${app.recurring.poll-ms:60000}", initialDelayString = "${app.recurring.initial-delay-ms:1000}")
    public void generateDueBills() { recurring.runDue(null, RecurringService.today()); }
}
