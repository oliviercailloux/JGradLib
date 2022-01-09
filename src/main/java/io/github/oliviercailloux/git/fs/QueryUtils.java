package io.github.oliviercailloux.git.fs;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.net.PercentEscaper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class QueryUtils {
	final static public PercentEscaper QUERY_ENTRY_ESCAPER = new PercentEscaper("-_.*~!$'(),;@:/+?", false);
	final static private PercentEscaper RESTRICTED_ESCAPER = new PercentEscaper("-_.*%", false);

	/**
	 * Thanks to https://stackoverflow.com/a/13592567/.
	 */
	public static Map<String, String> splitQuery(URI uri) {
		if (Strings.isNullOrEmpty(uri.getRawQuery())) {
			return Collections.emptyMap();
		}
		final Map<String, List<String>> asLists = Arrays.stream(uri.getRawQuery().split("&"))
				.map(QueryUtils::splitQueryParameter).collect(Collectors.groupingBy(SimpleImmutableEntry::getKey,
						LinkedHashMap::new, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		if (asLists.keySet().stream().anyMatch(k -> asLists.get(k).size() >= 2)) {
			throw new IllegalArgumentException();
		}
		return asLists.keySet().stream()
				.collect(ImmutableMap.toImmutableMap(k -> k, k -> Iterables.getOnlyElement(asLists.get(k))));
	}

	/**
	 * Thanks to https://stackoverflow.com/a/13592567/.
	 */
	private static SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
		final int idx = it.indexOf("=");
		final String key = idx > 0 ? it.substring(0, idx) : it;
		final String value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
		/**
		 * Because URLDecoder only decodes very restricted strings, I need to encode a
		 * bit more before decoding. Importantly, I need to encode plus, otherwise
		 * converted back to space instead of left alone. But I need to leave % alone as
		 * this indicates a percent-escape, not a percent sign. Sob.
		 */
		final String decodedKey = URLDecoder.decode(RESTRICTED_ESCAPER.escape(key), StandardCharsets.UTF_8);
		final String decodedValue = URLDecoder.decode(RESTRICTED_ESCAPER.escape(value), StandardCharsets.UTF_8);
		return new SimpleImmutableEntry<>(decodedKey, decodedValue);
	}

}
