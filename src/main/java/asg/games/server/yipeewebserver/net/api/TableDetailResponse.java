package asg.games.server.yipeewebserver.net.api;

import java.util.List;

public record TableDetailResponse(
        String roomId,
        String roomName,
        TableDetailsSummary tableDetailsSummary
) {}