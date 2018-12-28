package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.GradingContext;

public class PomContexter implements GradingContext {
	private final ContentSupplier supplier;
	private String groupId;
	/**
	 * empty iff groupId is empty.
	 */
	private ImmutableList<String> groupIdElements;

	public PomContexter(ContentSupplier supplier) {
		this.supplier = requireNonNull(supplier);
		clear();
	}

	@Override
	public void clear() {
		groupId = null;
		groupIdElements = null;
	}

	@Override
	public void init() throws GradingException {
		final Matcher matcher = Pattern.compile("<groupId>(([^\\.]\\.?)+)</groupId>").matcher(supplier.getContent());
		final boolean found = matcher.find();
		final MatchResult result = matcher.toMatchResult();
		final boolean foundTwice = matcher.find();
		if (found && !foundTwice) {
			groupId = result.group(1);
			assert groupId.length() >= 1;
			groupIdElements = ImmutableList.copyOf(groupId.split("\\."));
			assert groupIdElements.size() >= 1 : groupId;
			assert !groupIdElements.contains("");
		} else {
			groupId = "";
			groupIdElements = ImmutableList.of();
		}
	}

	public boolean isGroupIdValid() {
		final ImmutableList<String> validStart = ImmutableList.of("io", "github");
		return groupIdElements != null && groupIdElements.size() >= 3
				&& groupIdElements.subList(0, 2).equals(validStart);
	}

	public String getGroupId() {
		return groupId;
	}

	public List<String> getGroupIdElements() {
		return groupIdElements;
	}

}
