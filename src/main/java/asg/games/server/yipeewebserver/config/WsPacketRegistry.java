package asg.games.server.yipeewebserver.config;

import asg.games.yipee.net.packets.AbstractClientRequest;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class WsPacketRegistry {
    /**
     * Can be classpath:config/ws-packets.xml OR file:/opt/yipee/config/ws-packets.xml
     */
    private final ResourceLoader resourceLoader;
    private final String configLocation;

    private volatile long lastModified = -1L;

    private final AtomicReference<Map<String, Class<? extends AbstractClientRequest>>> registry =
            new AtomicReference<>(Collections.emptyMap());

    public WsPacketRegistry(
            ResourceLoader resourceLoader,
            @Value("${yipee.ws.packetConfigPath:classpath:config/ws-packets.xml}") String configLocation
    ) {
        this.resourceLoader = resourceLoader;
        this.configLocation = configLocation;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public Class<? extends AbstractClientRequest> resolve(String packetType) {
        Class<? extends AbstractClientRequest> clazz = registry.get().get(packetType);
        if (clazz == null) {
            throw new IllegalArgumentException("Unsupported WS packetType: " + packetType);
        }
        return clazz;
    }

    public synchronized void reload() {
        Resource resource = resourceLoader.getResource(configLocation);
        log.info("Reloading WS packet registry from {}", configLocation);

        if (!resource.exists()) {
            log.error("WS packet config resource not found: {}", configLocation);
            return;
        }

        try (InputStream in = resource.getInputStream()) {
            Map<String, Class<? extends AbstractClientRequest>> next = loadFromXml(in);

            if (next.isEmpty()) {
                log.warn("WS packet registry loaded empty — ignoring reload.");
                return;
            }

            registry.set(Collections.unmodifiableMap(next));
            log.info("WS packet registry loaded ({} packet types)", next.size());

        } catch (Exception e) {
            log.error("Failed to reload WS packet registry — keeping previous config", e);
        }
    }

    private Map<String, Class<? extends AbstractClientRequest>> loadFromXml(InputStream in) throws Exception {
        Document doc = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(in);

        NodeList packets = doc.getElementsByTagName("packet");
        Map<String, Class<? extends AbstractClientRequest>> map = new HashMap<>();

        for (int i = 0; i < packets.getLength(); i++) {
            Element el = (Element) packets.item(i);

            String type = el.getAttribute("type");
            String className = el.getAttribute("class");

            if (type.isBlank() || className.isBlank()) {
                throw new IllegalStateException("packet entry missing type or class");
            }

            Class<?> raw = Class.forName(className);

            if (!AbstractClientRequest.class.isAssignableFrom(raw)) {
                throw new IllegalStateException(className + " does not extend AbstractClientRequest");
            }

            @SuppressWarnings("unchecked")
            Class<? extends AbstractClientRequest> clazz =
                    (Class<? extends AbstractClientRequest>) raw;

            if (map.put(type, clazz) != null) {
                throw new IllegalStateException("Duplicate packet type: " + type);
            }
        }

        return map;
    }

    //@Scheduled(fixedDelay = 3000)
    public void poll() {
        try {
            Resource resource = resourceLoader.getResource(configLocation);

            if (!resource.exists()) {
                log.warn("WS packet registry poll: resource not found: {}", configLocation);
                return;
            }

            long modified = resource.lastModified(); // <-- works for file: resources
            // For classpath resources inside a jar, this may throw or be non-useful (see note below)

            if (modified != lastModified) {
                lastModified = modified;
                reload();
            }
        } catch (Exception e) {
            // This is expected for classpath resources inside a jar: lastModified isn't reliable.
            log.debug("WS packet registry poll skipped (no lastModified available) for {}: {}",
                    configLocation, e.getMessage());
        }
    }
}
