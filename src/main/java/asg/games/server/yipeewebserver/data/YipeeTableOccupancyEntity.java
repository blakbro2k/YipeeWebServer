package asg.games.server.yipeewebserver.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
@Entity
@Table(
        name = "YT_TABLE_ACTIVITY",
        indexes = @Index(name = "idx_last_occupancy", columnList = "lastOccupancyChange")
)
@Getter
@Setter
public class YipeeTableOccupancyEntity {

    @Id
    private String tableId; // same id as YipeeTable

    @Column(nullable = false)
    private int seatedCount;

    @Column(nullable = false)
    private Instant lastOccupancyChange;

    @Column(nullable=false)
    private int watcherCount;

    @Column
    private Instant lastEmptyAt;

    private String roomId;

    private String gameId;

    // ---------------------------------------------------
    // Constructors
    // ---------------------------------------------------

    protected YipeeTableOccupancyEntity() {
        // JPA requires no-args constructor
    }

    public YipeeTableOccupancyEntity(String tableId) {
        this.tableId = tableId;
        this.seatedCount = 0;
        this.lastOccupancyChange = Instant.now();
    }

    public YipeeTableOccupancyEntity(String tableId, String roomId, String gameId) {
        this.tableId = tableId;
        this.roomId = roomId;
        this.gameId = gameId;
        this.seatedCount = 0;
        this.lastOccupancyChange = Instant.now();
    }

    // ---------------------------------------------------
    // Business logic
    // ---------------------------------------------------

    public void incrementSeated() {
        seatedCount++;
        lastOccupancyChange = Instant.now();
    }

    public void decrementSeated() {
        seatedCount = Math.max(0, seatedCount - 1);
        lastOccupancyChange = Instant.now();
    }

    public boolean isEmpty() {
        return seatedCount == 0;
    }
}