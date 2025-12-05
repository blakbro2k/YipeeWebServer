package asg.games.server.yipeewebserver.persistence;

// This will be AUTO IMPLEMENTED by Spring into a Bean called userRepository
// CRUD refers Create, Read, Update, Delete

import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface YipeeClientConnectionRepository extends YipeeRepository<PlayerConnectionEntity, String> {
    PlayerConnectionEntity findByClientId(String clientId);

    List<PlayerConnectionEntity> findByLastActivityBefore(Instant cutoff);

    PlayerConnectionEntity findByPlayer_Id(String playerId); // Spring Data derives this

    PlayerConnectionEntity findByName(String name);

    Optional<PlayerConnectionEntity> findOptionalByName(String name);

    Optional<PlayerConnectionEntity> findBySessionId(String sessionId);

    Optional<PlayerConnectionEntity> findByProviderAndExternalUserId(String provider, String externalUserId);

    void deleteBySessionId(String sessionId);

    int deleteByLastActivityBefore(Instant cutoff);

    boolean existsByProviderAndExternalUserId(String provider, String externalUserId);

    void deleteAllByPlayerId(String playerId);
}