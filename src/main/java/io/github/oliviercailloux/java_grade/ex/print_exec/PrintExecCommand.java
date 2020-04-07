package io.github.oliviercailloux.java_grade.ex.print_exec;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class PrintExecCommand {
	public static PrintExecCommand parse(List<String> command) {
		checkArgument(!command.stream().anyMatch(s -> s == null));
		checkArgument(!command.stream().anyMatch(s -> s.startsWith(" ")));
		checkArgument(!command.stream().anyMatch(s -> s.endsWith(" ")));

		boolean classpathUsed = false;
		String classpathArgument = null;
		boolean dUsed = false;
		String dArgument = null;
		final ImmutableList.Builder<String> otherArguments = ImmutableList.builder();

		final Iterator<String> iterator = command.iterator();
		final String commandItself = iterator.hasNext() ? iterator.next() : "";

		while (iterator.hasNext()) {
			final String current = iterator.next();
			switch (current) {
			case "-cp":
			case "-classpath":
			case "--class-path":
				verify(!classpathUsed);
				classpathUsed = true;
				classpathArgument = iterator.hasNext() ? iterator.next() : "";
				verify(!classpathArgument.startsWith("-"));
				break;
			case "-d":
				dUsed = true;
				verify(dArgument == null);
				dArgument = iterator.hasNext() ? iterator.next() : "";
				verify(!dArgument.startsWith("-"));
				break;
			case "":
				break;
			default:
				otherArguments.add(current);
				break;
			}
		}
		if (!classpathUsed) {
			verify(classpathArgument == null);
			classpathArgument = "";
		}
		assert classpathArgument != null;
		if (!dUsed) {
			Verify.verify(dArgument == null);
			dArgument = "";
		}
		assert dArgument != null;

		verify(!(classpathArgument.contains(":") && classpathArgument.contains(";")));
		final String splitOn;
		if (classpathArgument.contains(";")) {
			splitOn = ";";
		} else {
			splitOn = ":";
		}
		final ImmutableList<String> classpathEntries = Stream.of(classpathArgument.split(splitOn))
				.filter(s -> !s.isEmpty()).collect(ImmutableList.toImmutableList());
		verify(!classpathEntries.stream().anyMatch(String::isEmpty), "Arg: " + classpathArgument);

		return new PrintExecCommand(commandItself, classpathUsed, classpathArgument, classpathEntries, dUsed, dArgument,
				otherArguments.build());
	}

	private String command;
	private boolean classpathUsed;
	private String classpathArgument;
	private ImmutableSet<String> classpathEntries;
	private boolean dUsed;
	private String dArgument;
	private ImmutableSet<String> otherArguments;
	private String lastArgument;

	private PrintExecCommand(String command, boolean classpathUsed, String classpathArgument,
			List<String> classpathEntries, boolean dUsed, String dArgument, List<String> otherArguments) {
		this.command = checkNotNull(command);
		this.classpathUsed = classpathUsed;
		this.classpathArgument = checkNotNull(classpathArgument);
		this.classpathEntries = ImmutableSet.copyOf(classpathEntries);
		checkArgument(!classpathEntries.stream().anyMatch(String::isEmpty));
		this.dUsed = dUsed;
		this.dArgument = checkNotNull(dArgument);
		this.otherArguments = ImmutableSet.copyOf(otherArguments);
		checkArgument(!otherArguments.stream().anyMatch(String::isEmpty));
		this.lastArgument = otherArguments.isEmpty() ? "" : otherArguments.get(otherArguments.size() - 1);
	}

	public String getCommand() {
		return command;
	}

	public boolean isClasspathUsed() {
		return classpathUsed;
	}

	public String getClasspathArgument() {
		return classpathArgument;
	}

	public boolean hasClasspath() {
		return isClasspathUsed() && !getClasspathArgument().isEmpty();
	}

	public ImmutableSet<String> getClasspathEntries() {
		return classpathEntries;
	}

	public boolean isDUsed() {
		return dUsed;
	}

	public String getDArgument() {
		return dArgument;
	}

	public boolean hasD() {
		return isDUsed() && !getDArgument().isEmpty();
	}

	public ImmutableSet<String> getOtherArguments() {
		return otherArguments;
	}

	public String getLastArgument() {
		return lastArgument;
	}
}
