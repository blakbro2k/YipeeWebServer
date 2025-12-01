package asg.games.server.yipeewebserver;

import asg.games.server.yipeewebserver.core.GameContextFactory;
import asg.games.server.yipeewebserver.headless.HeadlessLauncher;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableScheduling
@RequiredArgsConstructor
@EnableJpaRepositories({"asg.games.server.yipeewebserver.persistence"})
@ComponentScan(basePackages = {"asg.games.server","asg.games.yipee"})
@EntityScan({"asg.games.yipee.core.objects","asg.games.server.yipeewebserver.data"})
@SpringBootApplication()
public class YipeeWebserverApplication extends ServletInitializer implements CommandLineRunner {
	private final YipeePacketHandler yipeePacketHandler;
	private final GameContextFactory gameContextFactory;
	private final ApplicationContext appContext;
	private final YipeeGameJPAServiceImpl yipeeGameJPAService;

	@Value("${gameserver.port}")
	private int tcpPort;

	@Value("${gameserver.udp.port}")
	private int udpPort;

	@Value("${gameserver.tickrate}")
	private float tickRate;

	public static void main(String[] args) {
		SpringApplication.run(YipeeWebserverApplication.class, args);
	}

	@Override
	public void run(String... args) {
		// Launch HeadlessLauncher and pass configuration
		HeadlessLauncher launcher = new HeadlessLauncher(yipeePacketHandler, gameContextFactory, appContext, yipeeGameJPAService);
		log.info("Starting Web Server, launching {}", launcher.getClass().getSimpleName());
		launcher.launch(tcpPort, udpPort, tickRate);
	}
}
