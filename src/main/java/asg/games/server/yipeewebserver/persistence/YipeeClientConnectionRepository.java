package asg.games.server.yipeewebserver.persistence;

// This will be AUTO IMPLEMENTED by Spring into a Bean called userRepository
// CRUD refers Create, Read, Update, Delete

import asg.games.server.yipeewebserver.data.PlayerConnectionDTO;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface YipeeClientConnectionRepository extends YipeeRepository<PlayerConnectionDTO, String> {
    PlayerConnectionDTO findByClientId(String clientId);

    List<PlayerConnectionDTO> findByLastActivityBefore(Instant cutoff);

    PlayerConnectionDTO findByPlayer_Id(String playerId); // Spring Data derives this

    PlayerConnectionDTO findByName(String name);

    Optional<PlayerConnectionDTO> findOptionalByName(String name);

    Optional<PlayerConnectionDTO> findBySessionId(String sessionId);

    Optional<PlayerConnectionDTO> findByProviderAndExternalUserId(String provider, String externalUserId);

    void deleteBySessionId(String sessionId);

    int deleteByLastActivityBefore(Instant cutoff);

    boolean existsByProviderAndExternalUserId(String provider, String externalUserId);

    void deleteAllByPlayerId(String playerId);
}