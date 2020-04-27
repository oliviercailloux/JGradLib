package io.github.oliviercailloux.java_grade.ex.print_exec;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

public class ArgumentsParser {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ArgumentsParser.class);

	/**
	 * @param command may not start or end with spaces
	 * @return
	 */
	public static ImmutableList<String> parse(String command) {
		return new ArgumentsParser().parseCommand(command);
	}

	private PeekingIterator<Integer> iterator;
	private boolean legalQuoting;

	ArgumentsParser() {
		iterator = null;
		legalQuoting = false;
	}

	/**
	 * @param command may not start or end with spaces
	 */
	public ImmutableList<String> parseCommand(String command) {
		checkArgument(!command.startsWith(" "));
		checkArgument(!command.endsWith(" "));
		checkArgument(!(command.contains("\"") && command.contains("'")));

		LOGGER.debug("Parsing {}.", command);

		final boolean pblHouston = Pattern.compile(" [^\" ][^ ]+\" ").matcher(command).find();
		final boolean quoteNotSpaced = Pattern.compile("[^ ]'[^ ]").matcher(command).find();
		final long countQuotes = command.codePoints().filter(i -> i == '\"').count();
		final boolean quotesEven = countQuotes % 2 == 0;
		legalQuoting = !pblHouston && !quoteNotSpaced && quotesEven;
		if (!legalQuoting) {
			return ImmutableList.of(command);
		}

		iterator = Iterators.peekingIterator(command.chars().iterator());

		final ImmutableList.Builder<String> builder = ImmutableList.builder();
		while (iterator.hasNext()) {
			Verify.verify(iterator.peek() != ' ');
			builder.add(getArg());
			checkArgument(!iterator.hasNext() || iterator.peek() == ' ');
			skipSpaces();
		}
		return builder.build();
	}

	private String getArg() {
		assert iterator.hasNext();

		boolean inQuote;

		final int first = iterator.peek();
		if (first == '"' || first == '\'') {
			inQuote = true;
			iterator.next();
		} else {
			inQuote = false;
		}

		final StringBuilder builder = new StringBuilder();
		while (iterator.hasNext()) {
			final int current = iterator.peek();
			if (current == '"' || current == '\'') {
				inQuote = !inQuote;
				iterator.next();
				break;
			}
			if ((!inQuote && current == ' ')) {
				break;
			}
			builder.append(Character.toChars(current));
			iterator.next();
		}
		checkArgument(!inQuote, "Incorrect quoting.");

		return builder.toString();
	}

	private void skipSpaces() {
		while (iterator.hasNext()) {
			final int current = iterator.peek();
			if (current != ' ') {
				break;
			}
			iterator.next();
		}
	}

}
