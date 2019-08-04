package io.github.oliviercailloux.grade.format;

import static com.google.common.base.Preconditions.checkArgument;

import java.text.NumberFormat;
import java.util.Locale;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.xml.HtmlDocument;

public class HtmlGrade {
	private static final double DEFAULT_DENOMINATOR = 20d;

	public static Document asHtml(WeightingGrade grade, String title) {
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ENGLISH);

		final HtmlDocument document = HtmlDocument.newInstance();
		document.setTitle(title);

		final String introText = "Hi! This is an automated e-mail containing your grade: " + title;
		document.getBody().appendChild(document.createParagraph(introText));

		final String globalGradeText = "Grade: " + formatter.format(grade.getPoints() * DEFAULT_DENOMINATOR) + " / 20";
		document.getBody().appendChild(document.createParagraph(globalGradeText));

		final ImmutableSet<CriterionGradeWeight> subGrades = grade.getSubGradesAsSet();
		final Element ul = document.createXhtmlElement("ul");
		document.getBody().appendChild(ul);
		appendListTo(subGrades, document, ul, DEFAULT_DENOMINATOR);
		return document.getDocument();
	}

	private static void appendListTo(ImmutableSet<CriterionGradeWeight> subGrades, HtmlDocument document, Element ul,
			double denominator) {
		for (CriterionGradeWeight subGrade : subGrades) {
			final Element li = document.createXhtmlElement("li");
			ul.appendChild(li);

			final IGrade currentGrade = subGrade.getGrade();
			final double points = currentGrade.getPoints();
			final double weight = subGrade.getWeight();

			final String pointsString;
			if (weight > 0d) {
				pointsString = points * denominator * weight + " / " + denominator * weight;
			} else {
				assert weight < 0d;
				final double penalty = (1d - points) * denominator * weight;
				pointsString = "−" + -penalty;
			}

			final String thisGradeStartText = subGrade.getCriterion().getName() + ": " + pointsString;
			if (currentGrade.getSubGrades().isEmpty()) {
				final String comment = currentGrade.getComment();
				final String thisGradeText;
				if (comment.isEmpty()) {
					thisGradeText = thisGradeStartText;
				} else {
					thisGradeText = thisGradeStartText + " (" + currentGrade.getComment() + ")";
				}
				final Node liText = document.createParagraph(thisGradeText);
				li.appendChild(liText);
			} else {
				checkArgument(currentGrade instanceof WeightingGrade);
//				final Node liText = document.getDocument().createTextNode(thisGradeStartText);
				final Node liText = document.createParagraph(thisGradeStartText);
				li.appendChild(liText);
				final Element subUl = document.createXhtmlElement("ul");
				li.appendChild(subUl);
				appendListTo(((WeightingGrade) currentGrade).getSubGradesAsSet(), document, subUl,
						denominator * weight);
			}
		}
	}
}
