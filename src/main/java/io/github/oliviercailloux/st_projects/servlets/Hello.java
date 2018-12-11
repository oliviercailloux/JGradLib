package io.github.oliviercailloux.st_projects.servlets;

import javax.json.JsonWriter;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Table;

@Path("hello")
public class Hello {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Hello.class);

	@GET
	@Produces(MediaType.TEXT_PLAIN + "; charset=UTF-8")
	public String sayHello() {
		LOGGER.info("Greeting.");
		LOGGER.info("Path t: {}.", Table.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		LOGGER.info("Path c: {}.", Client.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		LOGGER.info("Path h: {}.", JsonWriter.class.getProtectionDomain().getCodeSource().getLocation().getPath());
//		LOGGER.info("Path j: {}.", JerseyClient.class.getProtectionDomain().getCodeSource().getLocation().getPath());
//		LOGGER.info("Path l: {}.", org.glassfish.jersey.logging.LoggingFeatureAutoDiscoverable.class
//				.getProtectionDomain().getCodeSource().getLocation().getPath());
//		LOGGER.info("Path a: {}.", org.glassfish.jersey.internal.spi.AutoDiscoverable.class.getProtectionDomain()
//				.getCodeSource().getLocation().getPath());
		return "Hello, world.";
	}
}
