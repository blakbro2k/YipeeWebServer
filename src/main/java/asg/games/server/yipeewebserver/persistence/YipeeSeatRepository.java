package asg.games.server.yipeewebserver.persistence;

import asg.games.yipee.core.objects.YipeeSeat;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface YipeeSeatRepository extends YipeeRepository<YipeeSeat, String> {

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