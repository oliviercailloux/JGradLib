package io.github.oliviercailloux.st_projects.services.read;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.DescriptionList;
import org.asciidoctor.ast.DescriptionListEntry;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.StructuralNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;

import io.github.oliviercailloux.st_projects.model.Functionality;

public class FunctionalitiesReader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(FunctionalitiesReader.class);

	public static Logger getLogger() {
		return LOGGER;
	}

	private final Asciidoctor asciidoctor;

	private final Optional<BigDecimal> defaultDifficulty;

	private final Pattern difficultyRegExp;

	private Document doc;

	/**
	 * Not <code>null</code>.
	 */
	private List<Functionality> functionalities;

	private final DecimalFormat numberFormatter;

	public FunctionalitiesReader() {
		this(Optional.empty());
	}

	public FunctionalitiesReader(Optional<BigDecimal> defaultDifficulty) {
		LOGGER.info("Loading.");
		asciidoctor = Asciidoctor.Factory.create();
		LOGGER.info("Loaded.");
		functionalities = Lists.newLinkedList();
		/**
		 * Note that the sentence preceding the "difficulty" does not necessarily end
		 * with a dot. (As in this example.) (Or in this example!)
		 */
		difficultyRegExp = Pattern.compile("^(?<Description>.*) \\((?<Difficulty>[0-9,]+)\\)$");
		numberFormatter = (DecimalFormat) NumberFormat.getInstance(Locale.FRENCH);
		numberFormatter.setParseBigDecimal(true);
		this.defaultDifficulty = defaultDifficulty;
	}

	public Functionality asFunctionality(DescriptionListEntry item) throws IllegalFormat {
		final List<ListItem> terms = item.getTerms();
		checkFormat(terms.size() == 1);
		final ListItem term = Iterables.getOnlyElement(terms);
		final String functionalityName = term.getText();
		checkFormat(!Strings.isNullOrEmpty(functionalityName));
		final ListItem descriptionItem = item.getDescription();
		checkFormat(descriptionItem != null);
		assert descriptionItem != null;
		final String descriptionFull = descriptionItem.getSource();
		final String description;
		final BigDecimal difficulty;
		final Matcher matcher = difficultyRegExp.matcher(descriptionFull);
		if (matcher.matches()) {
			description = matcher.group("Description");
			final String difficultyString = matcher.group("Difficulty");
			/** This conversion is safe, as the pattern has matched the group. */
			try {
				difficulty = (BigDecimal) numberFormatter.parse(difficultyString);
			} catch (ParseException e) {
				throw new IllegalStateException(e);
			}
		} else {
			checkFormat(defaultDifficulty.isPresent(),
					"Difficulty not found (and default not provided) while matching '" + descriptionFull + "'.");
			description = descriptionFull;
			difficulty = defaultDifficulty.get();
		}
		return new Functionality(functionalityName, description, difficulty);
	}

	public Asciidoctor getAsciidoctor() {
		return asciidoctor;
	}

	public Document getDoc() {
		return requireNonNull(doc);
	}

	public List<Functionality> getFunctionalities() {
		return functionalities;
	}

	/**
	 * Sets the document and functionalities (at least one).
	 *
	 * @param source
	 *            not <code>null</code>.
	 * @throws IOException
	 * @throws IllegalFormat
	 */
	public void read(Reader source) throws IOException, IllegalFormat {
		functionalities = Lists.newLinkedList();

		doc = asciidoctor.load(CharStreams.toString(requireNonNull(source)), ImmutableMap.of());
		LOGGER.debug("Doc title: {}.", doc.getAttribute("doctitle"));

		final List<StructuralNode> blocks = doc.blocks();
		logTitles(blocks);

		final Section section;
		{
			final List<StructuralNode> matchingBlocks = blocks.stream()
					.filter(b -> Objects.equals(b.getTitle(), "Fonctions demandées")).collect(Collectors.toList());
			checkFormat(matchingBlocks.size() == 1,
					blocks.stream().map(StructuralNode::getTitle).collect(Collectors.toList()).toString() + "; "
							+ matchingBlocks.toString());
			final StructuralNode matchingBlock = Iterables.getOnlyElement(matchingBlocks);
			section = (Section) matchingBlock;
		}
		LOGGER.debug("Found section: {}.", section.getTitle());

		final List<DescriptionList> fctsSources;
		{
			final List<DescriptionList> matchingBlocks = section.getBlocks().stream()
					.filter(b -> b instanceof DescriptionList).map(b -> (DescriptionList) b)
					.collect(Collectors.toList());
			checkFormat(matchingBlocks.size() >= 1 && matchingBlocks.size() <= 2);
			fctsSources = matchingBlocks;
		}
		LOGGER.debug("Found functionalities lists.");

		final Stream<DescriptionListEntry> itemsStream = fctsSources.stream().flatMap(d -> d.getItems().stream());
		final List<DescriptionListEntry> items = itemsStream.collect(Collectors.toList());
		for (DescriptionListEntry item : items) {
			final Functionality functionality = asFunctionality(item);
			functionalities.add(functionality);
			LOGGER.debug("Found: {}.", functionality);
		}
	}

	private void checkFormat(boolean assertion) throws IllegalFormat {
		checkFormat(assertion, "");
	}

	private void checkFormat(boolean assertion, String error) throws IllegalFormat {
		if (!assertion) {
			throw new IllegalFormat(error);
		}
	}

	private void logTitles(final List<StructuralNode> blocks) {
		for (StructuralNode block : blocks) {
			LOGGER.debug("Title: {}.", block.getTitle());
		}
	}
}
