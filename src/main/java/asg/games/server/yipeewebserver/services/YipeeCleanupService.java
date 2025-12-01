package asg.games.server.yipeewebserver.services;

import asg.games.server.yipeewebserver.data.PlayerConnectionDTO;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YipeeCleanupService {

    private final YipeeClientConnectionRepository connectionRepo;
    private final YipeeGameJPAServiceImpl yipeeGameJPAServiceImpl;

    // 10 minutes default (configurable)
    private static final long TIMEOUT_SECONDS = 600;

    @Transactional
    public int cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minusSeconds(TIMEOUT_SECONDS);

        int deleted = connectionRepo.deleteByLastActivityBefore(cutoff);
        List<PlayerConnectionDTO> expiredConnections = connectionRepo.findByLastActivityBefore(cutoff);
        for (PlayerConnectionDTO conn : expiredConnections) {
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
    public int cleanupEmptyTables() {
        Instant cutoff = Instant.now().minusSeconds(TIMEOUT_SECONDS);

        int deleted = connectionRepo.deleteByLastActivityBefore(cutoff);
        List<PlayerConnectionDTO> expiredConnections = connectionRepo.findByLastActivityBefore(cutoff);
        for (PlayerConnectionDTO conn : expiredConnections) {
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
}
