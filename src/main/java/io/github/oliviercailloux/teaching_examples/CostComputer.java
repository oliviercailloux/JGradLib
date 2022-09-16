package io.github.oliviercailloux.teaching_examples;

import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CostComputer {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CostComputer.class);

	public static void main(String[] args) throws Exception {
		LOGGER.info("Starting.");

		Set<Heater> heaters = new LinkedHashSet<>();

		LOGGER.info("Adding heaters.");
		heaters.add(Heater.standard(1, "id1"));
		heaters.add(Heater.standard(2, "id2"));
		heaters.add(Heater.standard(3, "id3"));
		// …
		heaters.add(Heater.standard(1, "id1"));
		LOGGER.info("Added heaters.");

		// heaters.add(Heater.LG());
//		heaters.add(Heater.LG());

		LOGGER.info("Nb of heaters: {}.", heaters.size());
	}

}
