package io.github.oliviercailloux.git.github.model.graphql;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import jakarta.json.JsonObject;
import java.util.stream.Stream;

public class AssignedEvent extends IssueEvent {

  protected AssignedEvent(JsonObject json) {
    super(json);
  }

  @Override
  public IssueSnapshot applyTo(IssueSnapshot snap) {
    final User user = getUser();
    checkArgument(!snap.getAssignees().contains(user));
    return IssueSnapshot.of(getCreatedAt(), snap.getName(), snap.isOpen(),
        Streams.concat(snap.getAssignees().stream(), Stream.of(user))
            .collect(ImmutableSet.toImmutableSet()));
  }

  public User getUser() {
    return User.from(getJson().getJsonObject("user"));
  }
}
