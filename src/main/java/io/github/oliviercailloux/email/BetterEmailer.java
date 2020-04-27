package io.github.oliviercailloux.email;

public class BetterEmailer implements AutoCloseable {

	public static BetterEmailer newInstance() {
		return new BetterEmailer();
	}

	private BetterEmailer() {
		// nothing yet
	}

	@Override
	public void close() throws Exception {
		TODO();

	}

	static Session getOutlookImapSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "outlook.office365.com");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		final Session session = Session.getInstance(props);
		return session;
	}

	static Session getGmailImapSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "imap.gmail.com");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		// props.setProperty("mail.debug", "true");
		final Session session = Session.getInstance(props);
		return session;
	}

	static Session getZohoImapSession() {
		final Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imap");
		props.setProperty("mail.host", "imap.zoho.eu");
		props.setProperty("mail.imap.connectiontimeout", "2000");
		props.setProperty("mail.imap.timeout", "60*1000");
		props.setProperty("mail.imap.connectionpooltimeout", "10");
		props.setProperty("mail.imap.ssl.enable", "true");
		props.setProperty("mail.imap.ssl.checkserveridentity", "true");
		// props.setProperty("mail.debug", "true");
		final Session session = Session.getInstance(props);
		return session;
	}

}
