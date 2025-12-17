package asg.games.server.yipeewebserver.persistence;

import asg.games.yipee.core.objects.YipeeTable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YipeeTableRepository extends YipeeRepository<YipeeTable, String> {

    Optional<YipeeTable> findByRoomIdAndTableNumber(String roomId, int tableNumber);

    // watcher exists in YT_TABLE_PLAYERS for this table
    boolean existsByIdAndWatchers_Id(String tableId, String playerId);

    // Tables where the player is a watcher
    List<YipeeTable> findByWatchers_Id(String playerId);

    interface TableWatcherCount {
        String getTableId();
        long getWatcherCount();
    }

    @Query("""
      select t.id as tableId, count(p) as watcherCount
      from YipeeTable t
      left join t.watchers p
      group by t.id
    """)
    List<TableWatcherCount> countWatchersByTable();
}