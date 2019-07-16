package io.github.oliviercailloux.grade;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.json.bind.config.PropertyVisibilityStrategy;

public class MethodVisibility implements PropertyVisibilityStrategy {

	@Override
	public boolean isVisible(Field field) {
		return false;
	}

	@Override
	public boolean isVisible(Method method) {
		return true;
	}

}
