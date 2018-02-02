package io.github.oliviercailloux.st_projects.services.read;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.asciidoctor.ast.DescriptionListEntry;
import org.asciidoctor.ast.ListItem;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.st_projects.model.Functionality;

public class TestRead {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestRead.class);

	@Test
	public void test() throws IOException, IllegalFormat {
		final FunctionalitiesReader reader = new FunctionalitiesReader();
		try (InputStreamReader sourceReader = new InputStreamReader(
				getClass().getResourceAsStream("Assisted Board Games.adoc"), StandardCharsets.UTF_8)) {
			reader.read(sourceReader);
		}
	}

	@Test
	public void testReadFct() throws Exception {
		final String termText = "A mocked title";
		final String descrText = "A description with difficulty.";
		final String fullDescrText = descrText + " (2,5)";

		final DescriptionListEntry input = getMockedInput(termText, fullDescrText);

		final FunctionalitiesReader functionalitiesReader = new FunctionalitiesReader();

		final Functionality obtained = functionalitiesReader.asFunctionality(input);
		assertEquals(termText, obtained.getName());
		assertEquals(descrText, obtained.getDescription());
		assertEquals(BigDecimal.valueOf(2.5d), obtained.getDifficulty());
	}

	@Test
	public void testReadFctDoubleDiff() throws Exception {
		final String termText = "A mocked title";
		final String descrText = "A description with difficulty. (2,5)";
		final String fullDescrText = descrText + " (3)";

		final DescriptionListEntry input = getMockedInput(termText, fullDescrText);

		final FunctionalitiesReader functionalitiesReader = new FunctionalitiesReader();

		final Functionality obtained = functionalitiesReader.asFunctionality(input);
		assertEquals(termText, obtained.getName());
		assertEquals(descrText, obtained.getDescription());
		assertEquals(3.0d, obtained.getDifficulty().doubleValue(), 0d);
	}

	@Test
	public void testReadFctFakeDiff() throws Exception {
		final String termText = "A mocked title";
		final String descrText = "A description without (2) difficulty.";

		final DescriptionListEntry input = getMockedInput(termText, descrText);

		final FunctionalitiesReader functionalitiesReader = new FunctionalitiesReader(
				Optional.of(BigDecimal.valueOf(4.5d)));

		final Functionality obtained = functionalitiesReader.asFunctionality(input);
		assertEquals(termText, obtained.getName());
		assertEquals(descrText, obtained.getDescription());
		assertEquals(4.5d, obtained.getDifficulty().doubleValue(), 0d);
	}

	private DescriptionListEntry getMockedInput(final String termText, final String fullDescrText) {
		final ListItem mockedTermItem = Mockito.mock(ListItem.class);
		Mockito.when(mockedTermItem.getText()).thenReturn(termText);

		final ListItem mockedDescriptionItem = Mockito.mock(ListItem.class);
		Mockito.when(mockedDescriptionItem.getSource()).thenReturn(fullDescrText);

		final DescriptionListEntry input = Mockito.mock(DescriptionListEntry.class);
		Mockito.when(input.getTerms()).thenReturn(ImmutableList.of(mockedTermItem));
		Mockito.when(input.getDescription()).thenReturn(mockedDescriptionItem);
		return input;
	}
}
