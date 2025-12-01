package asg.games.server.yipeewebserver.net.api;

import asg.games.server.yipeewebserver.net.api.SeatSummary;
import asg.games.server.yipeewebserver.net.api.TableSummary;

import java.util.List;

public record TableDetailsSummary(
        TableSummary table,          // existing lightweight summary
        List<SeatSummary> seats,     // existing seat summaries
        List<String> watcherNames    // just the names of watchers
) {}