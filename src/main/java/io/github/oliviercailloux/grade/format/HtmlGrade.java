package io.github.oliviercailloux.grade.format;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.text.NumberFormat;
import java.util.Locale;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.xml.HtmlDocument;

public class HtmlGrade {
	private static final double DEFAULT_DENOMINATOR = 20d;

	public static Document asHtml(IGrade grade, String title) {
		final HtmlDocument document = HtmlDocument.newInstance();
		document.setTitle(title);

		final String introText = "Hi! This is an automated e-mail containing your grade: " + title;
		document.getBody().appendChild(document.createParagraph(introText));

		document.getBody().appendChild(getDescription(Criterion.given("Grade"), grade, document, DEFAULT_DENOMINATOR));

		return document.getDocument();
	}

	private static DocumentFragment getDescription(Criterion criterion, IGrade grade, HtmlDocument document,
			double denominator) {
		checkNotNull(criterion);
		checkNotNull(grade);
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ENGLISH);

		final DocumentFragment fragment = document.getDocument().createDocumentFragment();

		final String startGradeText = criterion.getName() + ": " + formatter.format(grade.getPoints() * denominator)
				+ " / " + formatter.format(denominator);
		final String comment = grade.getComment();
		final String thisGradeText;
		if (comment.isEmpty() || grade instanceof WeightingGrade) {
			thisGradeText = startGradeText;
		} else {
			thisGradeText = startGradeText + " (" + comment + ")";
		}
		fragment.appendChild(document.createParagraph(thisGradeText));

		if (!grade.getSubGrades().isEmpty()) {
			final Element ul = document.createXhtmlElement("ul");
			fragment.appendChild(ul);
			for (Criterion subCriterion : grade.getSubGrades().keySet()) {
				final Element li = document.createXhtmlElement("li");
				ul.appendChild(li);
				final double subWeight;
				if (grade instanceof WeightingGrade) {
					subWeight = ((WeightingGrade) grade).getWeights().get(subCriterion);
				} else {
					subWeight = 1d;
				}
				final DocumentFragment description;
				if (subWeight > 0d) {
					description = getDescription(subCriterion, grade.getSubGrades().get(subCriterion), document,
							denominator * subWeight);
				} else {
					description = getDescriptionOfPenalty(subCriterion, grade.getSubGrades().get(subCriterion),
							document, denominator * -subWeight);
				}
				li.appendChild(description);
			}
		}
		return fragment;
	}

	private static DocumentFragment getDescriptionOfPenalty(Criterion criterion, IGrade grade, HtmlDocument document,
			double denominator) {
		checkArgument(denominator > 0d);

		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ENGLISH);

		final DocumentFragment fragment = document.getDocument().createDocumentFragment();

		final String startGradeText = criterion.getName() + ": " + "−"
				+ formatter.format((1d - grade.getPoints()) * denominator) + " / " + "−"
				+ formatter.format(denominator);
		final String comment = grade.getComment();
		final String thisGradeText;
		if (comment.isEmpty() || grade instanceof WeightingGrade) {
			thisGradeText = startGradeText;
		} else {
			thisGradeText = startGradeText + " (" + comment + ")";
		}
		fragment.appendChild(document.createParagraph(thisGradeText));

		checkArgument(grade.getSubGrades().isEmpty());
		return fragment;
	}
}
