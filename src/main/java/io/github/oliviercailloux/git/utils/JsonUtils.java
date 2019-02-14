package io.github.oliviercailloux.git.utils;

import java.lang.reflect.Type;
import java.util.function.Function;

import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.adapter.JsonbAdapter;

import io.github.oliviercailloux.json.JsonStringToObjectWrapper;
import io.github.oliviercailloux.json.PrintableJsonObject;

public class JsonUtils {
	private static JsonbBuilder jsonbDefaultBuilder = null;

	public static Jsonb getDefaultJsonb() {
		if (jsonbDefaultBuilder == null) {
			jsonbDefaultBuilder = JsonbBuilder.newBuilder().withConfig(new JsonbConfig().withFormatting(true));
		}
		return jsonbDefaultBuilder.build();
	}

	public static <T> T deserializeWithJsonB(String source, Type type,
			@SuppressWarnings("rawtypes") JsonbAdapter... adapters) {
		final T asObj;
		try (Jsonb jsonb = getInstance(adapters)) {
			asObj = jsonb.fromJson(source, type);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		return asObj;
	}

	private static Jsonb getInstance(@SuppressWarnings("rawtypes") JsonbAdapter... adapters) {
		if (adapters.length == 0) {
			return getDefaultJsonb();
		}
		return JsonbBuilder.create(new JsonbConfig().withAdapters(adapters).withFormatting(true));
	}

	public static PrintableJsonObject serializeWithJsonB(Object source,
			@SuppressWarnings("rawtypes") JsonbAdapter... adapters) {
		final String asStr;
		try (Jsonb jsonb = getInstance(adapters)) {
			asStr = jsonb.toJson(source);
			assert asStr.startsWith("\n");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		return JsonStringToObjectWrapper.wrapPrettyPrinted(asStr.substring(1));
	}

	public static <T> JsonbAdapter<T, JsonObject> getAdapter(Function<JsonObject, T> asOriginalFct,
			Function<T, JsonObject> asJsonFct) {
		/** TODO doesn’t work because type inference fails, it seems. Bug or feature? */
		return new JsonbAdapter<T, JsonObject>() {
			@Override
			public JsonObject adaptToJson(T obj) throws Exception {
				return asJsonFct.apply(obj);
			}

			@Override
			public T adaptFromJson(JsonObject obj) throws Exception {
				return asOriginalFct.apply(obj);
			}
		};
	}
}
