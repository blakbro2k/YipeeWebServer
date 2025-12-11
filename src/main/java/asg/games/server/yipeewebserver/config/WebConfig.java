package asg.games.server.yipeewebserver.config;

import asg.games.server.yipeewebserver.mvc.CurrentPlayerArgumentResolver;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.services.SessionService;
import asg.games.server.yipeewebserver.web.SessionConnectionArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final YipeeClientConnectionRepository clientConnectionRepository;
    private final SessionService sessionService;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentPlayerArgumentResolver(clientConnectionRepository));
        resolvers.add(new SessionConnectionArgumentResolver(sessionService));
    }
}