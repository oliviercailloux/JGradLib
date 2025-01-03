package io.github.oliviercailloux.javagrade;

import io.github.oliviercailloux.grade.Criterion;

public enum JavaCriterion {

  POM,
  /**
   * A commit has been done, not through GitHub, but not necessarily with the right identity.
   */
  COMMIT,
  /**
   * Commit exists that uses the right identity.
   */
  ID, COMPILE, NO_WARNINGS, NO_DERIVED_FILES;

  public Criterion asCriterion() {
    return Criterion.given(toString());
  }
}
