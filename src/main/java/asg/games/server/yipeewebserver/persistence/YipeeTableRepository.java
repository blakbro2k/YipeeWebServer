package asg.games.server.yipeewebserver.persistence;

// This will be AUTO IMPLEMENTED by Spring into a Bean called userRepository
// CRUD refers Create, Read, Update, Delete

import asg.games.yipee.core.objects.YipeeTable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YipeeTableRepository extends YipeeRepository<YipeeTable, String> {
    Optional<YipeeTable> findByRoomIdAndTableNumber(String roomId, int tableNumber);
    // Tables where the player is a watcher
    List<YipeeTable> findByWatchers_Id(String playerId);
}