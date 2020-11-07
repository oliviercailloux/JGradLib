package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Comparator;
import java.util.Objects;

import org.eclipse.jgit.lib.ObjectId;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

public class RootComponent {
	public static final RootComponent DEFAULT = givenRef("refs/heads/main");
	/**
	 * Compares without access to the file system and including those roots that do
	 * not exist an underlying file system. This object does not implement
	 * Comparable because is not the only natural implementation of a comparison.
	 * For example, with access to a given file system, one could want to order
	 * roots by commit date.
	 */
	static final Comparator<RootComponent> SYNTAXIC_COMPARATOR = Comparator
			.comparingInt((RootComponent r) -> r.isRef() ? 0 : 1).thenComparing(RootComponent::toStringForm);

	public static RootComponent givenStringForm(String stringForm) {
		checkArgument(stringForm.startsWith("/"));
		checkArgument(stringForm.endsWith("/"));
		return given(stringForm.substring(1, stringForm.length() - 1));
	}

	public static RootComponent given(String refOrObjectId) {
		if (refOrObjectId.startsWith("refs/") || refOrObjectId.startsWith("heads/")
				|| refOrObjectId.startsWith("tags/")) {
			return givenRef(refOrObjectId);
		}
		return givenObjectId(ObjectId.fromString(refOrObjectId));
	}

	public static RootComponent givenRef(String ref) {
		final String extendedRef;
		if (ref.startsWith("refs/")) {
			extendedRef = ref;
		} else if (ref.startsWith("heads/") || ref.startsWith("tags/")) {
			extendedRef = "refs/" + ref;
		} else {
			throw new IllegalArgumentException("The given ref must start with refs/, heads/, or tags/.");
		}
		return new RootComponent(extendedRef, null);
	}

	public static RootComponent givenObjectId(ObjectId objectId) {
		return new RootComponent(null, objectId);
	}

	private String gitRef;
	private ObjectId objectId;

	private RootComponent(String gitRef, ObjectId objectId) {
		final boolean hasObjectId = objectId != null;
		final boolean hasRef = gitRef != null;
		checkArgument(hasRef != hasObjectId);
		if (hasRef) {
			assert gitRef != null;
			checkArgument(gitRef.startsWith("refs/"));
			checkArgument(!gitRef.endsWith("/"));
			checkArgument(!gitRef.contains("//"));
			checkArgument(!gitRef.contains("\\"));
		}
		this.gitRef = gitRef;
		this.objectId = objectId;
	}

	public boolean isRef() {
		return gitRef != null;
	}

	public boolean isObjectId() {
		return objectId != null;
	}

	public String getGitRef() {
		checkState(gitRef != null);
		return gitRef;
	}

	public ObjectId getObjectId() {
		checkState(objectId != null);
		return objectId;
	}

	public String toStringForm() {
		return "/" + (gitRef == null ? objectId.getName() : gitRef) + "/";
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof RootComponent)) {
			return false;
		}
		final RootComponent r2 = (RootComponent) o2;
		return Objects.equals(gitRef, r2.gitRef) && Objects.equals(objectId, r2.objectId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gitRef, objectId);
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		if (isRef()) {
			helper.add("gitRef", gitRef);
		} else {
			helper.add("objectId", objectId);
		}
		return helper.toString();
	}
}
