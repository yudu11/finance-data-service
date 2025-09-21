package com.example.financedataservice.bootstrap;

import com.example.financedataservice.service.FinanceDataService;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DailySnapshotInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DailySnapshotInitializer.class);

    private final FinanceDataService financeDataService;

    public DailySnapshotInitializer(FinanceDataService financeDataService) {
        this.financeDataService = financeDataService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Path snapshotPath = financeDataService.refreshDailyData();
            log.info("Daily finance snapshot ready at {}", snapshotPath);
        } catch (Exception ex) {
            log.error("Failed to initialize daily finance snapshot", ex);
        }
    }
}
