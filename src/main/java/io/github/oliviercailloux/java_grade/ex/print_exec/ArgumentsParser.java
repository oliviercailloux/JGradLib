package io.github.oliviercailloux.java_grade.ex.print_exec;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.regex.Matcher;
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
	private String effectiveCommand;

	ArgumentsParser() {
		iterator = null;
		effectiveCommand = null;
	}

	/**
	 * @param command may not start or end with spaces
	 */
	public ImmutableList<String> parseCommand(String command) {
		checkArgument(!command.startsWith(" "));
		checkArgument(!command.endsWith(" "));

		final String commandSpaced;
		if (command.startsWith("javac\"")) {
			commandSpaced = "javac " + command.substring(5);
		} else {
			commandSpaced = command;
		}

		final Matcher matcher = Pattern.compile(" [^\" ][^ ]+\" ").matcher(commandSpaced);
		if (matcher.find() || commandSpaced.codePoints().filter(i -> i == '\"').count() == 1) {
			effectiveCommand = commandSpaced.replace("\"", "");
			LOGGER.warn("Illegal quoting: {}, using {} instead.", commandSpaced, effectiveCommand);
		} else {
			effectiveCommand = commandSpaced;
		}
		iterator = Iterators.peekingIterator(effectiveCommand.chars().iterator());

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
		if (first == '"') {
			inQuote = true;
			iterator.next();
		} else {
			inQuote = false;
		}

		final StringBuilder builder = new StringBuilder();
		while (iterator.hasNext()) {
			final int current = iterator.peek();
			if (current == '"') {
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
		checkArgument(!inQuote, "Incorrect quoting in '" + effectiveCommand + "'.");

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
