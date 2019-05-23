package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;

public class AdditiveAggregator implements Aggregator {
	private ImmutableMap<Criterion, Double> additive;
	private Criterion parent;

	private AdditiveAggregator(Criterion parent, Map<Criterion, Double> additive) {
		this.parent = checkNotNull(parent);
		this.additive = ImmutableMap.copyOf(checkNotNull(additive));
	}

	@Override
	public ImmutableSet<Criterion> getAggregatedCriteria() {
		return ImmutableSet.<Criterion>builder().addAll(additive.keySet()).build();
	}

	@Override
	public AnonymousGrade aggregate(Set<Grade> grades) {
		checkArgument(!grades.isEmpty());
		final ImmutableMultiset<Criterion> criteriaMultiSet = grades.stream().map(Grade::getCriterion)
				.collect(ImmutableMultiset.toImmutableMultiset());
		checkArgument(criteriaMultiSet.size() == criteriaMultiSet.elementSet().size());
		final ImmutableSet<Criterion> criteria = criteriaMultiSet.elementSet();
		checkArgument(criteria.equals(additive.keySet()));
		final double points = grades.stream()
				.collect(Collectors.summingDouble((g) -> g.getPoints() * additive.get(g.getCriterion())));
		return Grade.anonymous(parent, points, "", grades);
	}

	@Override
	public Criterion getParentCriterion() {
		return parent;
	}

}
