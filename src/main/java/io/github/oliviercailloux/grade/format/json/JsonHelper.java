package io.github.oliviercailloux.grade.format.json;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class JsonHelper {

	private static final class FieldAccessStrategy implements PropertyVisibilityStrategy {
		@Override
		public boolean isVisible(Field field) {
			return true;
		}

		@Override
		public boolean isVisible(Method method) {
			return false;
		}
	}

	public static Jsonb getJsonb(@SuppressWarnings("rawtypes") JsonbAdapter... adapters) {
		final PropertyVisibilityStrategy propertyVisibilityStrategy = new FieldAccessStrategy();

		final Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true)
				.withPropertyVisibilityStrategy(propertyVisibilityStrategy).withAdapters(adapters));
		return jsonb;
	}

}
