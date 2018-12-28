package io.github.oliviercailloux.st_projects.guice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;

public class TestModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(String.class).toProvider(TestProvider.class);
	}

	public static void main(String[] args) {
		Injector injector = Guice.createInjector(new TestModule());

		LOGGER.info("Getting instance s1.");
		String s1 = injector.getInstance(String.class);
		LOGGER.info("Getting instance s2.");
		String s2 = injector.getInstance(String.class);
		LOGGER.info("Getting instance p1.");
		Provider<String> p1 = injector.getProvider(String.class);
		LOGGER.info("Getting instance p1s.");
		p1.get();
		LOGGER.info("Getting instance p2.");
		Provider<String> p2 = injector.getProvider(String.class);
		LOGGER.info("Getting instance sn.");
		String sn = injector.getInstance(String.class);

		LOGGER.info("Getting instance.");
		final Provider<String> p3 = new TestProvider();
		final String s3 = p3.get();
		final String s4 = p3.get();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestModule.class);
}