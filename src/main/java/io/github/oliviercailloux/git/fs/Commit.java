package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Authoring comes logically before committing, but because the dates are set by
 * the client, manual tampering (or incorrect clock) may cause author time to be
 * after committer time. This is rare in practice, but happens. For example, see
 * <a href=
 * "https://api.github.com/repos/checkstyle/checkstyle/commits/812b3416dcda571de5e6f7abd9d4e4c68c4dcdcf">this
 * commit</a> in the checkstyle project, authored at "2017-07-09T05:01:28Z" but
 * committed (by someone else) at "2017-07-09T04:42:52Z", twenty minutes
 * earlier!
 */
public class Commit {
	private static ZonedDateTime getCreationTime(PersonIdent ident) {
		final Date creationInstant = ident.getWhen();
		final TimeZone creationZone = ident.getTimeZone();
		final ZonedDateTime creationTime = ZonedDateTime.ofInstant(creationInstant.toInstant(),
				creationZone.toZoneId());
		return creationTime;
	}

	static Commit create(RevCommit revCommit) {
		final PersonIdent authorIdent = revCommit.getAuthorIdent();
		final PersonIdent committerIdent = revCommit.getCommitterIdent();
		return Commit.create(revCommit, authorIdent.getName(), authorIdent.getEmailAddress(),
				getCreationTime(authorIdent), committerIdent.getName(), committerIdent.getEmailAddress(),
				getCreationTime(committerIdent), ImmutableList.copyOf(revCommit.getParents()));
	}

	static Commit create(ObjectId id, String authorName, String authorEmail, ZonedDateTime authorDate,
			String committerName, String committerEmail, ZonedDateTime committerDate, List<ObjectId> parents) {
		return new Commit(id, authorName, authorEmail, authorDate, committerName, committerEmail, committerDate,
				parents);
	}

	private final ObjectId id;
	private final String authorName;
	private final String authorEmail;
	private final ZonedDateTime authorDate;
	private final String committerName;
	private final String committerEmail;
	private final ZonedDateTime committerDate;
	/**
	 * https://stackoverflow.com/questions/18301284
	 */
	private final ImmutableList<ObjectId> parents;

	private Commit(ObjectId id, String authorName, String authorEmail, ZonedDateTime authorDate, String committerName,
			String committerEmail, ZonedDateTime committerDate, List<ObjectId> parents) {
		this.id = checkNotNull(id);
		this.authorName = checkNotNull(authorName);
		this.authorEmail = checkNotNull(authorEmail);
		this.authorDate = checkNotNull(authorDate);
		this.committerName = checkNotNull(committerName);
		this.committerEmail = checkNotNull(committerEmail);
		this.committerDate = checkNotNull(committerDate);
		this.parents = ImmutableList.copyOf(parents);
	}

	public ObjectId getId() {
		return id;
	}

	public String getAuthorName() {
		return authorName;
	}

	public String getAuthorEmail() {
		return authorEmail;
	}

	public ZonedDateTime getAuthorDate() {
		return authorDate;
	}

	public String getCommitterName() {
		return committerName;
	}

	public String getCommitterEmail() {
		return committerEmail;
	}

	public ZonedDateTime getCommitterDate() {
		return committerDate;
	}

	public ImmutableList<ObjectId> getParents() {
		return parents;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Commit)) {
			return false;
		}
		/**
		 * We could get happy with comparing only the ids, as a random collision is
		 * extremely unlikely. But non-random collisions appear to be not so unlikely
		 * (https://stackoverflow.com/q/10434326), so let’s compare everything just to
		 * be sure not to allow for exploits.
		 */
		final Commit c2 = (Commit) o2;
		return id.equals(c2.id) && authorName.equals(c2.authorName) && authorEmail.equals(c2.authorEmail)
				&& authorDate.equals(c2.authorDate) && committerName.equals(c2.committerName)
				&& committerEmail.equals(c2.committerEmail) && committerDate.equals(c2.committerDate)
				&& parents.equals(c2.parents);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, authorName, authorEmail, authorDate, committerName, committerEmail, committerDate,
				parents);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("id", id).add("authorName", authorName)
				.add("authorDate", authorDate).add("committerName", committerName).add("committerDate", committerDate)
				.add("parents", parents).toString();
	}
}
