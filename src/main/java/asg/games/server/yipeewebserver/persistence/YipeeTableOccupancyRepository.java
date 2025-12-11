package asg.games.server.yipeewebserver.persistence;

import asg.games.server.yipeewebserver.data.YipeeTableOccupancyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface YipeeTableOccupancyRepository extends JpaRepository<YipeeTableOccupancyEntity, String> {

    // "select o from YipeeTableOccupancy o
    //  where o.seatedCount = ?1 and o.lastOccupancyChange < ?2"
    List<YipeeTableOccupancyEntity> findBySeatedCountAndLastOccupancyChangeBefore(
            int seatedCount,
            Instant cutoff
    );
}
