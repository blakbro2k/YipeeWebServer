package asg.games.server.yipeewebserver.net.api;

import java.util.List;

public record TableDetailsSummary(
        TableSummary table,          // existing lightweight summary
        List<SeatDetailSummary> seats,     // existing seat summaries
        List<PlayerSummary> watchers    // just the names of watchers
) {}