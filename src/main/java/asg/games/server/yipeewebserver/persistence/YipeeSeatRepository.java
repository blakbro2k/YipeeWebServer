package asg.games.server.yipeewebserver.persistence;

// This will be AUTO IMPLEMENTED by Spring into a Bean called userRepository
// CRUD refers Create, Read, Update, Delete

import asg.games.yipee.core.objects.YipeeSeat;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface YipeeSeatRepository extends YipeeRepository<YipeeSeat, String> {
    // Seats currently occupied by this player
    List<YipeeSeat> findBySeatedPlayer_Id(String playerId);
}