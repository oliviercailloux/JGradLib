package io.github.oliviercailloux.st_projects.services.read;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Objects;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.AbstractBlock;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.ListNode;
import org.asciidoctor.ast.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MoreCollectors;
import com.google.common.io.CharStreams;

public class ProjectReader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectReader.class);

	private final Asciidoctor asciidoctor;

	private Document doc;

	public ProjectReader() {
		asciidoctor = Asciidoctor.Factory.create();
		doc = null;
	}

	public void read(Reader source) throws IOException {
		doc = asciidoctor.load(CharStreams.toString(source), ImmutableMap.of());
		final List<AbstractBlock> blocks = doc.blocks();
		for (AbstractBlock block : blocks) {
			LOGGER.debug("Title: {}.", block.getTitle());
		}

		final Section section;
		{
			final AbstractBlock matchingBlock = blocks.stream()
					.filter(b -> Objects.equals(b.getTitle(), "Fonctions demandées"))
					.collect(MoreCollectors.onlyElement());
			section = (Section) matchingBlock;
		}
		LOGGER.info("Found section: {}.", section.getTitle());

		final ListNode fcts;
		{
			final AbstractBlock matchingBlock = section.getBlocks().stream().filter(b -> b instanceof ListNode)
					.collect(MoreCollectors.onlyElement());
			fcts = (ListNode) matchingBlock;
		}
		LOGGER.info("Found functionalities list.");

		final List<AbstractBlock> items = fcts.getBlocks();
		for (AbstractBlock item : items) {
			LOGGER.info("Item: {}.", item);
		}
	}
}
