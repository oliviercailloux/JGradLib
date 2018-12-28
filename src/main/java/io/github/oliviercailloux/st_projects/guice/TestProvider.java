package io.github.oliviercailloux.st_projects.guice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Provider;

public class TestProvider implements Provider<String> {
	public TestProvider() {
		LOGGER.info("New!");
	}

	@Override
	public String get() {
		LOGGER.info("Creating.");
		return "hey";
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestProvider.class);
}
