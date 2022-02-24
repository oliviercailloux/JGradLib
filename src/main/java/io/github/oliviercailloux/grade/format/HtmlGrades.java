package io.github.oliviercailloux.grade.format;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableMap;
import com.google.common.math.Stats;
import io.github.oliviercailloux.grade.AbsoluteAggregator;
import io.github.oliviercailloux.grade.CriteriaWeighter;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarkAggregator;
import io.github.oliviercailloux.grade.MaxAggregator;
import io.github.oliviercailloux.grade.ParametricWeighter;
import io.github.oliviercailloux.grade.StaticWeighter;
import io.github.oliviercailloux.grade.SubGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.xml.HtmlDocument;
import java.net.URI;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;

public class HtmlGrades {
	private static final double DEFAULT_DENOMINATOR = 20d;

	private static final NumberFormat FORMATTER = NumberFormat.getNumberInstance(Locale.ENGLISH);

	public static HtmlGrades newInstance() {
		return new HtmlGrades();
	}

	public static Document asHtml(IGrade grade, String title) {
		return asHtml(grade, title, DEFAULT_DENOMINATOR);
	}

	public static Document asHtml(IGrade grade, String title, double denominator) {
		final HtmlGrades htmler = newInstance();
		htmler.setTitle(title);
		htmler.setDenominator(denominator);
		return htmler.asHtml(grade);
	}

	public static Document asHtml(Map<String, ? extends Grade> grades, String generalTitle, double denominator) {
		final HtmlDocument document = HtmlDocument.newInstance();
		document.setTitle(generalTitle);
		document.getBody().appendChild(document.createTitle1(generalTitle));

		for (String key : grades.keySet()) {
			final Grade grade = grades.get(key);
			document.getBody().appendChild(document.createTitle2(key));
			document.getBody()
					.appendChild(getDescription(new SubGrade(Criterion.given("Grade"), grade), document, denominator));
		}

		return document.getDocument();
	}

	@Deprecated
	public static Document asHtmlIGrades(Map<String, ? extends IGrade> grades, String generalTitle,
			double denominator) {
		final HtmlDocument document = HtmlDocument.newInstance();
		document.setTitle(generalTitle);
		document.getBody().appendChild(document.createTitle1(generalTitle));

		for (String key : grades.keySet()) {
			final IGrade grade = grades.get(key);
			document.getBody().appendChild(document.createTitle2(key));
			document.getBody()
					.appendChild(getDescription(Criterion.given("Grade"), grade, document, denominator, false));
		}

		return document.getDocument();
	}

	private static DocumentFragment getDescription(SubGrade subGrade, HtmlDocument document, double denominator) {
		checkNotNull(subGrade);
		checkNotNull(document);
		final DocumentFragment fragment = document.getDocument().createDocumentFragment();

		final Criterion criterion = subGrade.criterion();
		final Grade grade = subGrade.grade();

		final Mark mark = grade.getMark();
		final String comment = mark.getComment();
		final String pointsText = FORMATTER.format(mark.getPoints() * denominator) + " / "
				+ FORMATTER.format(denominator);

		final boolean isMark = grade.getMarksTree().isMark();
		if (isMark) {
			checkArgument(comment.isEmpty());
		}
		final String explanation;
		if (isMark) {
			explanation = comment;
		} else {
			final MarkAggregator aggregator = grade.getAggregator().getMarkAggregator();
			if (aggregator instanceof ParametricWeighter) {
				final ParametricWeighter a = (ParametricWeighter) aggregator;
				final Grade multipliedGrade = grade.getGrade(a.multipliedCriterion());
				final String basePointsText = FORMATTER.format(multipliedGrade.getMark().getPoints() * denominator)
						+ " / " + FORMATTER.format(denominator);
				explanation = basePointsText + " × " + a.weightingCriterion() + " = " + pointsText;
			} else if (aggregator instanceof AbsoluteAggregator) {
				explanation = "Sum";
			} else if (aggregator instanceof StaticWeighter) {
				explanation = "Weighted sum";
			} else if (aggregator instanceof MaxAggregator) {
				explanation = "Max";
			} else {
				throw new VerifyException();
			}
		}
		final String explanationSeparator = explanation.isEmpty() ? "" : ": ";
		final String criterionSumary = criterion.getName() + " — " + explanation + explanationSeparator + pointsText;
		fragment.appendChild(document.createParagraph(criterionSumary));

		if (!isMark) {
			final Element ul = document.createXhtmlElement("ul");
			fragment.appendChild(ul);
			final MarkAggregator aggregator = grade.getAggregator().getMarkAggregator();
			if (aggregator instanceof ParametricWeighter) {
				final ParametricWeighter a = (ParametricWeighter) aggregator;
				{
					final Element li = document.createXhtmlElement("li");
					ul.appendChild(li);

					final Criterion subCriterion = a.multipliedCriterion();
					final DocumentFragment description = getDescription(
							new SubGrade(subCriterion, grade.getGrade(subCriterion)), document, denominator);
					li.appendChild(description);
				}
				{
					final Element li = document.createXhtmlElement("li");
					ul.appendChild(li);

					final Criterion subCriterion = a.weightingCriterion();
					final Grade subSubGrade = grade.getGrade(subCriterion);
					checkArgument(subSubGrade.getMarksTree().isMark());
					final DocumentFragment description = getDescription(new SubGrade(subCriterion, subSubGrade),
							document, 1d);
					li.appendChild(description);
				}
			} else if (aggregator instanceof CriteriaWeighter) {
				for (Criterion subCriterion : grade.getMarksTree().getCriteria()) {
					final Element li = document.createXhtmlElement("li");
					ul.appendChild(li);

					final double subWeight = grade.getWeight(subCriterion);
					final DocumentFragment description = getDescription(
							new SubGrade(subCriterion, grade.getGrade(subCriterion)), document,
							denominator * subWeight);
					li.appendChild(description);
				}
			} else if (aggregator instanceof MaxAggregator) {
				for (Criterion subCriterion : grade.getMarksTree().getCriteria()) {
					final Element li = document.createXhtmlElement("li");
					ul.appendChild(li);

					final DocumentFragment description = getDescription(
							new SubGrade(subCriterion, grade.getGrade(subCriterion)), document, denominator);
					li.appendChild(description);
				}
			} else {
				throw new VerifyException();
			}
		}
		return fragment;
	}

	private static DocumentFragment getDescription(Criterion criterion, IGrade grade, HtmlDocument document,
			double denominator, boolean zeroWeight) {
		checkNotNull(criterion);
		checkNotNull(grade);
		final DocumentFragment fragment = document.getDocument().createDocumentFragment();

		final String weightZeroString;
		if (zeroWeight) {
			weightZeroString = " (for information only)";
		} else {
			weightZeroString = "";
		}

		final String startGradeText = criterion.getName() + ": " + FORMATTER.format(grade.getPoints() * denominator)
				+ " / " + FORMATTER.format(denominator) + weightZeroString;
		final String comment = grade.getComment();
		final String thisGradeText;
		if (comment.isEmpty()) {
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
							denominator * subWeight, false);
				} else if (subWeight == 0d) {
					description = getDescription(subCriterion, grade.getSubGrades().get(subCriterion), document,
							denominator, true);
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
		checkArgument(denominator >= 0d);

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

	private String title;
	private double denominator;
	private Stats stats;
	private ImmutableMap<Integer, Double> quantiles;

	private HtmlGrades() {
		title = "";
		denominator = DEFAULT_DENOMINATOR;
		stats = null;
		quantiles = ImmutableMap.of();
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = checkNotNull(title);
	}

	public double getDenominator() {
		return denominator;
	}

	public void setDenominator(double denominator) {
		checkArgument(denominator > 0d);
		checkArgument(Double.isFinite(denominator));
		this.denominator = denominator;
	}

	public Stats getStats() {
		return stats;
	}

	public void setStats(Stats stats) {
		checkArgument(stats == null || stats.count() >= 1);
		this.stats = stats;
	}

	public ImmutableMap<Integer, Double> getQuantiles() {
		return quantiles;
	}

	public void setQuantiles(Map<Integer, Double> quantiles) {
		this.quantiles = ImmutableMap.copyOf(checkNotNull(quantiles));
	}

	public Document asHtml(IGrade grade) {
		final HtmlDocument document = HtmlDocument.newInstance();
		document.setTitle(title);

		final String introText = "Hi! This is an automated e-mail containing your grade: " + title;
		document.getBody().appendChild(document.createParagraph(introText));

		document.getBody().appendChild(getDescription(Criterion.given("Grade"), grade, document, denominator, false));

		if (quantiles.containsKey(1) && quantiles.containsKey(2) && quantiles.containsKey(3) && stats != null) {
			final Element p = document.createXhtmlElement("p");
			p.appendChild(document.createAnchor(URI.create("https://en.wikipedia.org/wiki/Quartile"), "Quartiles"));
			final String quartilesString = ": [" + format(quantiles.get(1)) + " | " + format(quantiles.get(2)) + " | "
					+ format(quantiles.get(3)) + "].";
			p.appendChild(document.createTextNode(quartilesString));
			p.appendChild(document.createTextNode(" Mean: " + format(stats.mean())));
			if (stats.count() >= 2) {
				p.appendChild(document.createTextNode("; "));
				p.appendChild(document.createAnchor(
						URI.create(
								"https://en.wikipedia.org/wiki/Standard_deviation#Corrected_sample_standard_deviation"),
						"sd"));
				p.appendChild(document.createTextNode(": " + format(stats.sampleStandardDeviation())));
			}
			p.appendChild(document.createTextNode("."));
			document.getBody().appendChild(p);
		}
		return document.getDocument();
	}

	private String format(double pointsNormalized) {
		return FORMATTER.format(pointsNormalized * denominator);
	}
}
