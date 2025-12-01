package asg.games.server.yipeewebserver.net.api;

import java.util.List;

public record TableDetailResponse(
        String roomId,
        String roomName,
        String tableId,
        int tableNumber,
        boolean rated,
        boolean soundOn,
        List<SeatDetailSummary> seats,
        List<PlayerSummary> watchers
) {}