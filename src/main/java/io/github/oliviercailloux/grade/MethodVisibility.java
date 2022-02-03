package io.github.oliviercailloux.grade;

import jakarta.json.bind.config.PropertyVisibilityStrategy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
