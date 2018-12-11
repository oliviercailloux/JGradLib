package io.github.oliviercailloux.st_projects.servlets;

import java.util.Arrays;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.asciidoctor.Asciidoctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AsciidoctorCDIFactory {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(AsciidoctorCDIFactory.class);

	@Produces
	@ApplicationScoped
	public Asciidoctor getInstance() {
//		LOGGER.info("Loading short.");
//		RubyInstanceConfig config = new RubyInstanceConfig();
//		config.setLoader(this.getClass().getClassLoader());
//
//		JavaEmbedUtils.initialize(Arrays.asList("META-INF/jruby.home/lib/ruby", "gems/asciidoctor-1.5.6.1/lib"),
//				config);
//		return Asciidoctor.Factory.create(this.getClass().getClassLoader());

		LOGGER.info("Loading with path.");
		return Asciidoctor.Factory.create(Arrays.asList("META-INF/jruby.home/lib/ruby"),
				"gems/asciidoctor-1.5.6.1/lib");
	}
}
