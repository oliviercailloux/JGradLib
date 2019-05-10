package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

public class GitUtils {

	public static ZonedDateTime getCreationTime(RevCommit commit) {
		/** https://stackoverflow.com/questions/11856983 */
		final PersonIdent authorIdent = commit.getAuthorIdent();
		final ZonedDateTime authorCreationTime = getCreationTime(authorIdent);
		final PersonIdent committerIdent = commit.getCommitterIdent();
		final ZonedDateTime committerCreationTime = getCreationTime(committerIdent);
		checkArgument(!authorCreationTime.isAfter(committerCreationTime),
				String.format("Author %s, creation time: %s, committer %s, creation time: %s", authorIdent.getName(),
						authorCreationTime, committerIdent.getName(), committerCreationTime));
		return committerCreationTime;
	}

	public static ZonedDateTime getCreationTime(PersonIdent ident) {
		Date creationInstant = ident.getWhen();
		TimeZone creationZone = ident.getTimeZone();
		final ZonedDateTime creationTime = ZonedDateTime.ofInstant(creationInstant.toInstant(),
				creationZone.toZoneId());
		return creationTime;
	}

}
