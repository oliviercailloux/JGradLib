package io.github.oliviercailloux.grade.comm;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.email.Emailer;
import io.github.oliviercailloux.utils.Utils;

public class EmailerDauphineHelper {

	public static final EmailAddress FROM = EmailAddress.given("olivier.cailloux@dauphine.fr", "Olivier Cailloux");

	public static String getDauphineToken() {
		{
			final String token = System.getenv("token_dauphine");
			if (token != null) {
				return token;
			}
		}
		{
			final String token = System.getProperty("token_dauphine");
			if (token != null) {
				return token;
			}
		}
		final Path path = Path.of("token_dauphine.txt");
		if (!Files.exists(path)) {
			throw new IllegalStateException();
		}
		final String content = Utils.getOrThrow(() -> Files.readString(path));
		return content.replaceAll("\n", "");
	}

	public static String getGmailToken() {
		{
			final String token = System.getenv("token_gmail");
			if (token != null) {
				return token;
			}
		}
		{
			final String token = System.getProperty("token_gmail");
			if (token != null) {
				return token;
			}
		}
		final Path path = Path.of("token_gmail.txt");
		if (!Files.exists(path)) {
			throw new IllegalStateException();
		}
		final String content = Utils.getOrThrow(() -> Files.readString(path));
		return content.replaceAll("\n", "");
	}

	public static String getZohoToken() {
		{
			final String token = System.getenv("token_zoho");
			if (token != null) {
				return token;
			}
		}
		{
			final String token = System.getProperty("token_zoho");
			if (token != null) {
				return token;
			}
		}
		final Path path = Path.of("token_zoho.txt");
		if (!Files.exists(path)) {
			throw new IllegalStateException();
		}
		final String content = Utils.getOrThrow(() -> Files.readString(path));
		return content.replaceAll("\n", "");
	}

	public static void connect(BetterEmailer emailer) {
		emailer.connectToStore(BetterEmailer.getZohoImapSession(), Emailer.USERNAME_OTHERS,
				EmailerDauphineHelper.getZohoToken());
		emailer.connectToTransport(BetterEmailer.getOutlookSmtpSession(), Emailer.USERNAME_DAUPHINE,
				EmailerDauphineHelper.getDauphineToken());
	}

}
