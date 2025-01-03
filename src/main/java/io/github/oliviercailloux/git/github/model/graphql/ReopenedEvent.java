package io.github.oliviercailloux.git.github.model.graphql;

import static com.google.common.base.Preconditions.checkArgument;

import jakarta.json.JsonObject;

public class ReopenedEvent extends IssueEvent {

  protected ReopenedEvent(JsonObject json) {
    super(json);
  }

  @Override
  public IssueSnapshot applyTo(IssueSnapshot snap) {
    checkArgument(!snap.isOpen());
    return IssueSnapshot.of(getCreatedAt(), snap.getName(), true, snap.getAssignees());
  }
}
