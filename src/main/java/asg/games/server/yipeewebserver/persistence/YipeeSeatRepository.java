package asg.games.server.yipeewebserver.persistence;

import asg.games.yipee.core.objects.YipeeSeat;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YipeeSeatRepository extends YipeeRepository<YipeeSeat, String> {
    // player is seated somewhere at this table
    boolean existsByParentTable_IdAndSeatedPlayer_Id(String tableId, String playerId);

    Optional<YipeeSeat> findFirstByParentTable_IdAndSeatedPlayer_Id(String tableId, String playerId);

    // if you want to validate the exact seat number too
    boolean existsByParentTable_IdAndSeatNumberAndSeatedPlayer_Id(String tableId, int seatNumber, String playerId);

    // Seats currently occupied by this player
    List<YipeeSeat> findBySeatedPlayer_Id(String playerId);

    interface TableSeatCount {
        String getTableId();
        long getOccupiedCount();
    }

    @Query("""
      select s.parentTable.id as tableId, count(s) as occupiedCount
      from YipeeSeat s
      where s.seatedPlayer is not null
      group by s.parentTable.id
    """)
    List<TableSeatCount> countOccupiedSeatsByTable();
}