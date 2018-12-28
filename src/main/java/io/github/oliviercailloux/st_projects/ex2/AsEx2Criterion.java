package io.github.oliviercailloux.st_projects.ex2;

import javax.json.bind.adapter.JsonbAdapter;

import io.github.oliviercailloux.st_projects.model.Criterion;

public class AsEx2Criterion implements JsonbAdapter<Criterion, String> {

	@Override
	public String adaptToJson(Criterion criterion) {
		return criterion.toString();
	}

	@Override
	public Criterion adaptFromJson(String stringRepr) {
		return Ex2Criterion.valueOf(stringRepr);
	}

}
