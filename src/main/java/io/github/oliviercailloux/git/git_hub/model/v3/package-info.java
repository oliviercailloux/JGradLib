/**
 *
 * This alternative to coordinates objects fails: having nakedIssues with fetcher functionalities
 * (similar to JCabi). Fails because also some objects that are more complete are needed, such as:
 * IssueBasic, the simplest one with few details coming from a partial json; IssueMoreComplete, with
 * an event list but events having low details, …
 *
 * Having no coordinates: fails because if I have IssueBasic with low details, and want
 * IssueMoreComplete, I need a way to refer to the issue when passing it to the fetcher.
 *
 * Having an interface IssueCoordinates could work. Implemented by IssueBasic and IssueMoreComplete.
 * Then I can give them to the fetcher, or I can ask (when knowing the issue coordinates in static):
 * Fetcher.getRepo(…).getIssue(…). But, still, a basic implementation of IssueCoordinates with the
 * coordinates only is useful, e.g., to store a list of issue coordinates.
 *
 * The "Bare" objects hand out informations coming from the json, with possibly slight
 * transformations for easier use. For example, html_url is given with a trailing slash for easier
 * combination of urls, if suitable; dates and times are parsed as Instant s.
 *
 * @author Olivier Cailloux
 *
 */
package io.github.oliviercailloux.git.git_hub.model.v3;
