package asg.games.server.yipeewebserver.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Getter
@Component
public class ServerIdentity {

    @Value("${gameserver.server.id}")
    private String serverId;

    @Value("${gameserver.server.serviceName}")
    private String serviceName;

    private String instanceId;

    private String fullId;

    @PostConstruct
    void init() {
        // If you later run in Kubernetes/whatever, you can inject POD_NAME instead:
        // instanceId = System.getenv().getOrDefault("POD_NAME", UUID.randomUUID().toString().substring(0, 8));
        instanceId = UUID.randomUUID().toString().substring(0, 8);
        fullId = serverId + "::" + instanceId;
    }
}
