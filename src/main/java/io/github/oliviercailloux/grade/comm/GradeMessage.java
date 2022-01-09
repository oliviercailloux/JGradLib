package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.grade.IGrade;
import javax.mail.Message;

class GradeMessage {
	public static GradeMessage given(IGrade grade, Message message) {
		return new GradeMessage(grade, message);
	}

	private IGrade grade;
	private Message message;

	private GradeMessage(IGrade grade, Message message) {
		this.grade = checkNotNull(grade);
		this.message = checkNotNull(message);
	}

	public IGrade getGrade() {
		return grade;
	}

	public Message getMessage() {
		return message;
	}
}
