package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.grade.Grade;
import jakarta.mail.Message;

class GradeMessage {
  public static GradeMessage given(Grade grade, Message message) {
    return new GradeMessage(grade, message);
  }

  private Grade grade;
  private Message message;

  private GradeMessage(Grade grade, Message message) {
    this.grade = checkNotNull(grade);
    this.message = checkNotNull(message);
  }

  public Grade getGrade() {
    return grade;
  }

  public Message getMessage() {
    return message;
  }
}
