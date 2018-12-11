package io.github.oliviercailloux.st_projects.ex1;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public enum Ex1Criterion {
	SUBMITTED_GITHUB_USER_NAME("GitHub username submitted on MyCourse", 0), REPO_EXISTS("Repository exists", 2),
	ON_TIME("Delivered on time", 0), USER_NAME("Commit is associated to the right user name", 2),
	SINGLE_ROOT_COMMIT("Only one root commit", 0), CONTAINS_START("First commit contains start.txt", 1),
	HELLO2("First commit contains a blob “hello2” in start.txt", 1), DEV_EXISTS("Branch dev exists", 1),
	DEV_CONTAINS_BOLD("Branch dev contains bold.txt", 0.5), TRY_1("Branch dev contains “try 1” in bold.txt", 0.5),
	DEV2_EXISTS("Branch dev2 exists", 1),
	ALTERNATIVE_APPROACH("Branch dev2 contains “alternative approach” in bold.txt", 1),
	MERGE1_COMMIT(
			"A single commit exists with exactly two parents, both being parent of the first commit or being the branch dev and dev2",
			2),
	MERGE1_CONTAINS_BOLD("Merged commit contains bold.txt", 1),
	MERGED1("Merged commit contains neither “<<<” nor “===” nor “>>>”", 1),
	AFTER_MERGE1("Commit exists after merged one", 0.5),
	AFTER_MERGE1_BOLD("Commit after merged one contains a blob in bold.txt strict superset of the one merged", 1.5),
	MERGE2_COMMIT("Some commit is parent of two commits each having at least four ascendents", 4),
	CURL("A branch curl exists", 0.5), CURL_LINE("A file curl.txt exists in that branch with a single line", 0.5),
	CURL_CMD("The line contains “curl ”", 1), CURL_START("The line contains “https://en.wikipedia.org/w/api.php?”", 1),
	CURL_BASICS(
			"The line contains the basics: “https://en.wikipedia.org/w/api.php?”, “action=query”, “&titles=Bertrand”, and “&prop=revisions”",
			1),
	CURL_PROPS("The line contains, in supplement to the basics: “&rvprop=ids|timestamp|user”", 2),
	CURL_DATE("The line contains, in supplement to the basics: “&rvstart=01082017”", 2),
	CURL_FORMAT("The line contains, in supplement to the basics: “&format=json”", 2);
	private String expl;
	private double points;

	private Ex1Criterion(String expl, double points) {
		this.expl = requireNonNull(expl);
		checkArgument(Double.isFinite(points));
		checkArgument(points >= 0);
		this.points = points;
	}

	public String getExpl() {
		return expl;
	}

	public double getPoints() {
		return points;
	}
}
