package asg.games.server.yipeewebserver.config;

import asg.games.server.yipeewebserver.mvc.CurrentPlayerArgumentResolver;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.services.SessionService;
import asg.games.server.yipeewebserver.web.SessionConnectionArgumentResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final YipeeClientConnectionRepository clientConnectionRepository;
    private final YipeePlayerRepository yipeePlayerRepository;
    private final SessionService sessionService;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentPlayerArgumentResolver(clientConnectionRepository));
        resolvers.add(new SessionConnectionArgumentResolver(sessionService, yipeePlayerRepository));
    }
}