package task.serverless.tests;

import com.mailosaur.MailosaurClient;
import com.mailosaur.models.Message;
import com.mailosaur.models.SearchCriteria;

public class MailosaurHelper {
	private static final String API_KEY = System.getenv("MAILOSAUR_API_KEY");
	public static final String SERVER_ID = System.getenv("MAILOSAUR_SERVER_ID");

	public static final String MAILOSAUR_DOMAIN = SERVER_ID + ".mailosaur.net";

	private static final MailosaurClient mailosaur = new MailosaurClient(API_KEY);

	public static String waitForEmail(String recipientEmail) {
		SearchCriteria criteria = new SearchCriteria();
		criteria.withSentTo(recipientEmail);

		try {
			Message message = mailosaur.messages().get(SERVER_ID, criteria);

			String text = (message.text() != null && message.text().body() != null) ? message.text().body() : "";
			String html = (message.html() != null && message.html().body() != null) ? message.html().body() : "";

			StringBuilder links = new StringBuilder();
			if (message.html() != null && message.html().links() != null) {
				message.html().links().forEach(link -> links.append("Found Link: ").append(link.href()).append("\n"));
			}

			mailosaur.messages().delete(message.id());

			return text + "\n" + html + "\n" + links;
		} catch (Exception e) {
			throw new RuntimeException("Email to " + recipientEmail + " not found or timeout reached", e);
		}
	}
}
