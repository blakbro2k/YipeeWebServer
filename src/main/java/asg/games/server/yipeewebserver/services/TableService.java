package asg.games.server.yipeewebserver.services;

import asg.games.server.yipeewebserver.data.YipeeTableOccupancyEntity;
import asg.games.server.yipeewebserver.persistence.YipeeTableOccupancyRepository;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.core.objects.YipeeSeat;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableService {

    private final YipeeGameJPAServiceImpl yipeeGameService;
    private final YipeeTableOccupancyRepository occupancyRepository;

    @Transactional
    public YipeeSeat sitDown(String tableId, String playerId, int seatNumber) {
        YipeeSeat seat = yipeeGameService.sitDown(playerId, tableId, seatNumber);

        // update occupancy index here if you want, trusting game logic
        YipeeTableOccupancyEntity occ = occupancyRepository.findById(tableId)
                .orElseGet(() -> new YipeeTableOccupancyEntity(tableId));
        occ.setSeatedCount(
                (int) seat.getParentTable().getSeats().stream()
                        .filter(YipeeSeat::isOccupied)
                        .count()
        );
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
}