package asg.games.server.yipeewebserver.services;

import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.data.YipeeTableOccupancyEntity;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableOccupancyRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableRepository;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YipeeCleanupService {
    private static final Duration EMPTY_TABLE_TIMEOUT = Duration.ofMinutes(10);

    private final YipeeClientConnectionRepository connectionRepo;
    private final YipeeGameJPAServiceImpl yipeeGameJPAServiceImpl;
    private final YipeeTableRepository yipeeTableRepository;
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
    public void cleanupEmptyTables() {
        Instant cutoff = Instant.now().minusSeconds(TIMEOUT_SECONDS);

        var stale = yipeeTableOccupancyRepository.findBySeatedCountAndLastOccupancyChangeBefore(0, cutoff);

        int deleted = 0;
        for (YipeeTableOccupancyEntity occ : stale) {
            String tableId = occ.getTableId();
            if (yipeeTableRepository.existsById(tableId)) {
                yipeeTableRepository.deleteById(tableId);
            }
            yipeeTableOccupancyRepository.delete(occ);
            deleted++;
        }

    }

}
