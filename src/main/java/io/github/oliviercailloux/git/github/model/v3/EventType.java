package io.github.oliviercailloux.git.github.model.v3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum EventType {
  OTHER, ISSUES_EVENT, ISSUE_COMMENT_EVENT, CREATE_REPOSITORY_EVENT, CREATE_BRANCH_EVENT, CREATE_TAG_EVENT, MEMBER_EVENT, PUSH_EVENT;

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(EventType.class);
}
