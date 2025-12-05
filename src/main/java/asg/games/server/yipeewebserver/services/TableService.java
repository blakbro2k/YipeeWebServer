package asg.games.server.yipeewebserver.services;

import asg.games.server.yipeewebserver.data.YipeeTableOccupancyEntity;
import asg.games.server.yipeewebserver.persistence.YipeeTableOccupancyRepository;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.core.objects.YipeeSeat;
import asg.games.yipee.core.objects.YipeeTable;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TableService {

    private final YipeeGameJPAServiceImpl yipeeGameService;
    private final YipeeTableOccupancyRepository occupancyRepository;

    @Transactional
    public YipeeSeat sitDown(String tableId, String playerId, int seatNumber) {
        // 1) Do the real game logic
        YipeeSeat seat = yipeeGameService.sitDown(playerId, tableId, seatNumber);

        // 2) Update occupancy index
        YipeeTableOccupancyEntity occ = occupancyRepository.findById(tableId)
                .orElseGet(() -> new YipeeTableOccupancyEntity(tableId));
        occ.incrementSeated();
        occupancyRepository.save(occ);

        return seat;
    }

    @Transactional
    public YipeeSeat standUp(String tableId, String playerId) {
        // 1) Domain logic
        YipeeSeat seat = yipeeGameService.standUp(playerId, tableId);

        // 2) Occupancy index
        occupancyRepository.findById(tableId).ifPresent(occ -> {
            occ.decrementSeated();
            occupancyRepository.save(occ);
        });

        return seat;
    }

    @Transactional
    public void onTableCreated(YipeeTable table) {
        if (!occupancyRepository.existsById(table.getId())) {
            occupancyRepository.save(new YipeeTableOccupancyEntity(table.getId()));
        }
    }

    @Transactional
    public void onTableDeleted(String tableId) {
        occupancyRepository.deleteById(tableId);
    }
}