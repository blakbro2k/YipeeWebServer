package asg.games.server.yipeewebserver.persistence;

// This will be AUTO IMPLEMENTED by Spring into a Bean called userRepository
// CRUD refers Create, Read, Update, Delete

import asg.games.yipee.core.objects.YipeeRoom;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface YipeeRoomRepository extends YipeeRepository<YipeeRoom, String> {
    // All rooms that currently contain this player
    List<YipeeRoom> findByPlayers_Id(String playerId);
}