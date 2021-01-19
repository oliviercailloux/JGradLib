package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Objects;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class Patch {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Patch.class);

	private final ImmutableList<Criterion> path;
	private final IGrade grade;

	@JsonbCreator
	public static Patch create(@JsonbProperty("path") List<Criterion> path, @JsonbProperty("grade") IGrade grade) {
		return new Patch(path, grade);
	}

	private Patch(List<Criterion> path, IGrade grade) {
		this.path = ImmutableList.copyOf(path);
		this.grade = checkNotNull(grade);
	}

	public ImmutableList<Criterion> getPath() {
		return path;
	}

	public IGrade getGrade() {
		return grade;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Patch)) {
			return false;
		}
		final Patch t2 = (Patch) o2;
		return path.equals(t2.path) && grade.equals(t2.grade);
	}

	@Override
	public int hashCode() {
		return Objects.hash(path, grade);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("path", path).add("grade", grade).toString();
	}

}
