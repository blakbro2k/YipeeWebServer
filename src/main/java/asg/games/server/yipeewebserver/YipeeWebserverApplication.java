package asg.games.server.yipeewebserver;

import asg.games.server.yipeewebserver.headless.HeadlessLauncher;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

//@EnableTransactionManagement(proxyTargetClass = true)
@EnableJpaRepositories({"asg.games.server.yipeewebserver.persistence","asg.games.yipee.json"})
@ComponentScan(basePackages = {"asg.games.server","asg.games.yipee.json"})
@EntityScan({"asg.games.yipee.objects","asg.games.yipee.json","asg.games.server.yipeewebserver.data"})
@SpringBootApplication()
public class YipeeWebserverApplication extends ServletInitializer implements CommandLineRunner {
	private static final Logger logger = LoggerFactory.getLogger(YipeeWebserverApplication.class);

	@Autowired
	private YipeeGameJPAServiceImpl yipeeGameService;

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
		HeadlessLauncher launcher = new HeadlessLauncher();
		logger.info("Starting Web Server, launching {}", launcher.getClass().getSimpleName());
		launcher.launch(tcpPort, udpPort, tickRate, yipeeGameService);
	}
}
