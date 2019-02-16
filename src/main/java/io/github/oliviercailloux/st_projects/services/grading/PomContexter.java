package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.PomContext;

public class PomContexter implements PomContext {
	private final ContentSupplier supplier;
	private String groupId;
	/**
	 * empty iff groupId is empty.
	 */
	private ImmutableList<String> groupIdElements;

	public PomContexter(ContentSupplier supplier) {
		this.supplier = requireNonNull(supplier);
		groupId = null;
		groupIdElements = null;
	}

	public void init() throws GradingException {
		final String content = supplier.getContent();
		final Matcher matcher = Pattern.compile(
				"<project[^>]*>" + "[^<]*" + "(?:<[^>]*>[^<]*</[^>]*>[^<]*)*" + "<groupId>(([^\\.<]\\.?)+)</groupId>")
				.matcher(content);
		LOGGER.debug("Matching for group id against {}.", content);
		final boolean found = matcher.find();
		final MatchResult result = matcher.toMatchResult();
		final boolean foundTwice = matcher.find();
		if (found && !foundTwice) {
			groupId = result.group(1);
			assert groupId.length() >= 1;
			groupIdElements = ImmutableList.copyOf(groupId.split("\\."));
			LOGGER.debug("Found group id {}; elements are {}.", groupId, groupIdElements);
			assert groupIdElements.size() >= 1 : groupId;
			assert !groupIdElements.contains("");
		} else {
			LOGGER.debug("Found once: {}, result: {}, twice: {}.", found, result, foundTwice);
			groupId = "";
			groupIdElements = ImmutableList.of();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PomContexter.class);

	@Override
	public boolean isGroupIdValid() {
		final ImmutableList<String> validStart = ImmutableList.of("io", "github");
		return groupIdElements != null && groupIdElements.size() >= 3
				&& groupIdElements.subList(0, 2).equals(validStart);
	}

	@Override
	public String getGroupId() {
		return groupId;
	}

	@Override
	public List<String> getGroupIdElements() {
		assert groupIdElements != null;
		return groupIdElements;
	}

}
