package task.serverless.tests;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.model.ConfirmSubscriptionRequest;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

public class ServerlessNotificationFunctionalTest extends BaseServerlessTest {

	private String getSnsTopicArn() {
		return snsClient.listTopics().topics().stream()
				.map(t -> t.topicArn())
				.filter(arn -> arn.contains("cloudxserverless"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Application SNS Topic not found"));
	}

	@Test
	@DisplayName("Validate Swagger UI Availability")
	void testSwaggerUiAvailability() {
		given()
				.basePath("/")
				.when()
				.get("/api/ui")
				.then()
				.statusCode(200);
	}

	@Test
	@DisplayName("CXQA-SNSSQS-04, 05, 11: Subscribe, Confirm and Verify List")
	void testUserSubscriptionFlow() {
		String testEmail = UUID.randomUUID().toString().substring(0, 8) + "@" + MailosaurHelper.MAILOSAUR_DOMAIN;

		// CXQA-SNSSQS-04: subscribe
		given()
				.contentType(ContentType.JSON)
				.pathParam("email_address", testEmail)
				.when()
				.post("/notification/{email_address}")
				.then()
				.statusCode(200);

		String emailBody = MailosaurHelper.waitForEmail(testEmail);
		assertThat(emailBody).isNotNull();

		// CXQA-SNSSQS-05: confirm
		String token = extractToken(emailBody);
		var confirmResponse = snsClient.confirmSubscription(ConfirmSubscriptionRequest.builder()
				.topicArn(getSnsTopicArn())
				.token(token)
				.build());
		assertThat(confirmResponse.subscriptionArn()).isNotBlank();

		try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

		// CXQA-SNSSQS-11: verify list
		List<String> endpoints = given()
				.get("/notification")
				.then()
				.statusCode(200)
				.extract().path("Endpoint");

		assertThat(endpoints).contains(testEmail);
	}

	@Test
	@DisplayName("CXQA-SNSSQS-06, 07, 08: Receive Notification, Validate Metadata and Download")
	void testImageEventNotificationAndDownload() {
		String testEmail = UUID.randomUUID().toString().substring(0, 8) + "@" + MailosaurHelper.MAILOSAUR_DOMAIN;
		subscribeAndConfirm(testEmail);

		given().contentType(ContentType.MULTIPART)
				.multiPart("upfile", new File("src/test/resources/test.jpeg"))
				.post("/image")
				.then()
				.statusCode(200);

		// CXQA-SNSSQS-06: receive notification
		String emailBody = MailosaurHelper.waitForEmail(testEmail);

		// CXQA-SNSSQS-07: validate metadata and link
		String downloadLink = extractDownloadLink(emailBody);
		assertAll("Notification Content Validation",
				() -> assertThat(emailBody).doesNotContain("{").as("Message should not be raw JSON"),
				() -> assertThat(emailBody).containsIgnoringCase("Size").as("Should contain image size metadata"),
				() -> assertThat(downloadLink).isNotBlank().as("Should contain a download link")
		);

		// CXQA-SNSSQS-08: download
		byte[] downloadedFile = given()
				.baseUri(downloadLink)
				.basePath("")
				.get()
				.then()
				.statusCode(200)
				.extract().asByteArray();

		assertThat(downloadedFile).isNotEmpty();
	}

	@Test
	@DisplayName("CXQA-SNSSQS-09, 10: Unsubscribe and Verify No Further Notifications")
	void testUnsubscribeFlow() {
		// Upload image
		Object rawId = given()
				.contentType(ContentType.MULTIPART)
				.multiPart("upfile", new File("src/test/resources/test.jpeg"))
				.when()
				.post("/image")
				.then()
				.statusCode(200)
				.extract().path("id");

		String imageId = String.valueOf(rawId);

		String testEmail = UUID.randomUUID().toString().substring(0, 8) + "@" + MailosaurHelper.MAILOSAUR_DOMAIN;
		subscribeAndConfirm(testEmail);

		// CXQA-SNSSQS-09: unsubscribe
		given()
				.pathParam("email_address", testEmail)
				.when()
				.delete("/notification/{email_address}")
				.then()
				.statusCode(anyOf(is(200), is(204)));

		try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

		// Delete image
		given()
				.pathParam("image_id", imageId)
				.when()
				.delete("/image/{image_id}")
				.then()
				.statusCode(anyOf(is(200), is(204)));

		// CXQA-SNSSQS-10: Verify No Further Notifications
		boolean emailReceived = true;
		try {
			MailosaurHelper.waitForEmail(testEmail);
		} catch (RuntimeException e) {
			emailReceived = false;
		}

		assertThat(emailReceived).as("Unsubscribed user must not receive notifications").isFalse();
	}

	private void subscribeAndConfirm(String email) {
		given()
				.contentType(ContentType.JSON)
				.pathParam("email_address", email)
				.when()
				.post("/notification/{email_address}")
				.then()
				.statusCode(200);

		String emailBody = MailosaurHelper.waitForEmail(email);

		snsClient.confirmSubscription(ConfirmSubscriptionRequest.builder()
				.topicArn(getSnsTopicArn())
				.token(extractToken(emailBody))
				.build());

		try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
	}

	private String extractToken(String body) {
		Matcher matcher = Pattern.compile("Token=([^\"&\\s]+)").matcher(body);
		if (matcher.find()) {
			return matcher.group(1);
		}
		throw new AssertionError("SNS Token not found in email body. Body was: \n" + body);
	}

	private String extractDownloadLink(String body) {
		Matcher matcher = Pattern.compile("(http[s]?://[^\\s]+)").matcher(body);
		if (matcher.find()) return matcher.group(1);
		throw new AssertionError("Download link not found in email body");
	}
}
