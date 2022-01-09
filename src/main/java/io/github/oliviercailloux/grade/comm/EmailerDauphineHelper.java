package io.github.oliviercailloux.grade.comm;

import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import java.nio.file.Files;
import java.nio.file.Path;

public class EmailerDauphineHelper {

	public static final EmailAddressAndPersonal FROM = EmailAddressAndPersonal.given("olivier.cailloux@dauphine.fr",
			"Olivier Cailloux");

	public static final String USERNAME_DAUPHINE = "ocailloux@dauphine.fr";
	public static final String USERNAME_OTHERS = "olivier.cailloux";

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
		final String content = IO_UNCHECKER.getUsing(() -> Files.readString(path));
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
		final String content = IO_UNCHECKER.getUsing(() -> Files.readString(path));
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
		final String content = IO_UNCHECKER.getUsing(() -> Files.readString(path));
		return content.replaceAll("\n", "");
	}

	public static void connect(Emailer emailer) {
		emailer.connectToStore(Emailer.getZohoImapSession(), USERNAME_OTHERS, EmailerDauphineHelper.getZohoToken());
		emailer.connectToTransport(Emailer.getOutlookSmtpSession(), USERNAME_DAUPHINE,
				EmailerDauphineHelper.getDauphineToken());
	}

}
