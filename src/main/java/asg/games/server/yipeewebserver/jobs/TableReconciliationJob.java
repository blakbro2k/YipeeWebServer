package asg.games.server.yipeewebserver.jobs;

import asg.games.server.yipeewebserver.services.YipeeCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TableReconciliationJob {

    private final YipeeCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${yipee.tables.reconcileMs:60000}")
    public void runTableReconciliations() {
        cleanupService.reconcileTableActivity();
    }
}