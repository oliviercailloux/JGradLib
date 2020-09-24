package io.github.oliviercailloux.git;

public enum GitScheme {
	FILE, GIT, HTTP, HTTPS, FTP, FTPS, SSH;

	@Override
	public String toString() {
		return super.toString().toLowerCase();
	}
}
