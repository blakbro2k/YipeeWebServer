package asg.games.server.yipeewebserver.services;

import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.data.YipeeTableOccupancyEntity;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.persistence.YipeeSeatRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableOccupancyRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableRepository;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class YipeeCleanupService {
    private static final Duration EMPTY_TABLE_TIMEOUT = Duration.ofMinutes(10);

    private final YipeeClientConnectionRepository connectionRepo;
    private final YipeeGameJPAServiceImpl yipeeGameJPAServiceImpl;
    private final YipeeTableRepository yipeeTableRepository;
    private final YipeeSeatRepository yipeeSeatRepository;
    private final YipeeTableOccupancyRepository yipeeTableOccupancyRepository;

    // 10 minutes default (configurable)
    private static final long TIMEOUT_SECONDS = 600;

    @Transactional
    public int cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minusSeconds(TIMEOUT_SECONDS);

        int deleted = 0;
        List<PlayerConnectionEntity> expiredConnections = connectionRepo.findByLastActivityBefore(cutoff);
        for (PlayerConnectionEntity conn : expiredConnections) {
            deleted++;
            String playerId = conn.getPlayer().getId();
            // delete the connection first (or inside removePlayerCompletely)
            connectionRepo.delete(conn);
            yipeeGameJPAServiceImpl.removePlayerCompletely(playerId);
        }

        if (deleted > 0) {
            log.info("Cleaned up {} expired player connections", deleted);
        }

        return deleted;
    }

    @Transactional
    public void deleteAllConnections() {
        log.warn("Deleting ALL player connections from YT_PLAYER_CONNECTION");
        connectionRepo.deleteAllInBatch();
    }

    @Transactional
    public void forceDeleteAllTables() {
        log.warn("FORCE PURGE: deleting all Yipee tables (cascade seats + join rows), then activity rows");

        // Delete tables as entities so cascade/orphanRemoval runs for seats,
        // and Hibernate cleans up the ManyToMany join table rows.
        yipeeTableRepository.deleteAll();

        // Activity rows are separate and must be cleared explicitly
        yipeeTableOccupancyRepository.deleteAll();

        log.warn("FORCE PURGE: done.");
    }

    @Transactional
    public void cleanupEmptyTables() {
        log.debug("Enter cleanupEmptyTables()");
        Instant cutoff = Instant.now().minusSeconds(TIMEOUT_SECONDS);

        var stale = yipeeTableOccupancyRepository.findBySeatedCountAndLastOccupancyChangeBefore(0, cutoff);
        log.debug("stale=" + stale);

        int deleted = 0;
        for (YipeeTableOccupancyEntity occ : stale) {
            log.debug("occEntity={}",occ);

            String tableId = occ.getTableId();
            log.debug("tableId={}",tableId);
            if (yipeeTableRepository.existsById(tableId)) {
                yipeeTableRepository.deleteById(tableId);
            }
            yipeeTableOccupancyRepository.delete(occ);
            deleted++;
        }
        log.debug("Enter cleanupEmptyTables()={}", deleted);
    }

    @Transactional
    public void reconcileTableActivity() {
        Instant now = Instant.now();

        Map<String, Integer> seatTruth = new HashMap<>();
        for (var r : yipeeSeatRepository.countOccupiedSeatsByTable()) {
            seatTruth.put(r.getTableId(), (int) r.getOccupiedCount());
        }

        Map<String, Integer> watcherTruth = new HashMap<>();
        for (var r : yipeeTableRepository.countWatchersByTable()) {
            watcherTruth.put(r.getTableId(), (int) r.getWatcherCount());
        }

        // iterate all activity rows (or all tables; pick one as the "source list")
        for (YipeeTableOccupancyEntity occ : yipeeTableOccupancyRepository.findAll()) {
            int occupiedSeats = seatTruth.getOrDefault(occ.getTableId(), 0);
            int watchers = watcherTruth.getOrDefault(occ.getTableId(), 0);

            boolean changed =
                    occ.getSeatedCount() != occupiedSeats ||
                            occ.getWatcherCount() != watchers;

            if (changed) {
                occ.setSeatedCount(occupiedSeats);
                occ.setWatcherCount(watchers);
                occ.setLastOccupancyChange(now);
            }

            // If you add lastEmptyAt:
            boolean empty = occupiedSeats == 0 && watchers == 0; // or only seats, your choice
            if (empty) {
                if (occ.getLastEmptyAt() == null) occ.setLastEmptyAt(now);
            } else {
                occ.setLastEmptyAt(null);
            }
        }
    }
}
