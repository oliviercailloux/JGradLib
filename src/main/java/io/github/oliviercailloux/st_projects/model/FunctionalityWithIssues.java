package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.odftoolkit.simple.text.Paragraph;
import org.odftoolkit.simple.text.ParagraphContainer;

import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueWithHistory;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class FunctionalityWithIssues {
	public static FunctionalityWithIssues from(Functionality functionality, Set<IssueWithHistory> issues) {
		return new FunctionalityWithIssues(functionality, issues);
	}

	private Functionality functionality;

	private Set<IssueWithHistory> issues;

	private FunctionalityWithIssues(Functionality functionality, Set<IssueWithHistory> issues) {
		this.functionality = requireNonNull(functionality);
		this.issues = requireNonNull(issues);
	}

	public Stream<Consumer<ParagraphContainer>> getWriters() {
		final Iterator<IssueWithHistory> it = issues.iterator();
		final Consumer<ParagraphContainer> cons = ((c) -> {
			final Paragraph p = c.addParagraph("");
			if (it.hasNext()) {
				final IssueWithHistory issue = it.next();
				checkArgument(issue.getOriginalName().equals(functionality.getName()));
				p.appendHyperlink(issue.getOriginalName(), Utils.toURI(issue.getBare().getHtmlURL()));
			} else {
				p.appendTextContent(functionality.getName(), false);
			}
		});
		final Stream<IssueWithHistory> s = StreamSupport
				.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false);
		final Stream<Consumer<ParagraphContainer>> s2 = s.map((i) -> ((c) -> c.addParagraph("")
				.appendHyperlink(i.getOriginalName(), Utils.toURI(i.getBare().getHtmlURL()))));
		return Stream.concat(Stream.of(cons), s2);
	}
}
