package task.snssqs.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.model.ConfirmSubscriptionRequest;

import java.util.UUID;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;

public class ApplicationFunctionalTest extends BaseApiTest {

	@Test
	@DisplayName("Validate Swagger UI Availability")
	void testSwaggerUiAvailability() {
		given()
				.basePath("/")
		.when()
				.get("/api/ui")
		.then()
				.statusCode(200)
				.contentType(containsString("html"));
	}

	@Test
	@DisplayName("CXQA-SNSSQS-04, 05, 11: Subscribe, Confirm and Verify List")
	void testUserSubscriptionFlow() {
		String testEmail = UUID.randomUUID().toString().substring(0, 8) + "@" + MAILOSAUR_DOMAIN;

		// CXQA-SNSSQS-04: subscribe
		given()
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
				.topicArn(snsTopicArn)
				.token(token)
				.build());
		assertThat(confirmResponse.subscriptionArn()).isNotBlank();

		// CXQA-SNSSQS-11: verify list
		String notificationsList = given().get("/notification").then().statusCode(200).extract().asString();
		assertThat(notificationsList).contains(testEmail);
	}

	@Test
	@DisplayName("CXQA-SNSSQS-06, 07, 08: Receive Notification, Validate Metadata and Download")
	void testImageEventNotificationAndDownload() {
		String testEmail = UUID.randomUUID().toString().substring(0, 8) + "@" + MAILOSAUR_DOMAIN;
		subscribeAndConfirm(testEmail);

		given().contentType("multipart/form-data")
				.multiPart("upfile", new File("src/test/resources/test.jpeg"))
				.post("/image").then().statusCode(200);

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
		byte[] downloadedFile = given().baseUri(downloadLink).basePath("").get().then().statusCode(200).extract().asByteArray();
		assertThat(downloadedFile).isNotEmpty();
	}

	@Test
	@DisplayName("CXQA-SNSSQS-09, 10: Unsubscribe and Verify No Further Notifications")
	void testUnsubscribeFlow() {
		Object rawId = given()
				.contentType("multipart/form-data")
				.multiPart("upfile", new File("src/test/resources/test.jpeg")) // Обратите внимание на "upfile"!
				.when()
				.post("/image")
				.then()
				.statusCode(200)
				.extract().path("id");

		Integer imageId = Integer.valueOf(rawId.toString());

		// Create user and subscribe him
		String testEmail = UUID.randomUUID().toString().substring(0, 8) + "@" + MAILOSAUR_DOMAIN;
		subscribeAndConfirm(testEmail);

		// CXQA-SNSSQS-09: unsubscribe
		given()
				.pathParam("email_address", testEmail)
				.when()
				.delete("/notification/{email_address}")
				.then()
				.statusCode(200);

		// Delete image
		given()
				.pathParam("image_id", imageId)
				.when()
				.delete("/image/{image_id}")
				.then()
				.statusCode(200);

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
		// Меняем POST-запрос и здесь
		given()
				.pathParam("email_address", email)
				.when()
				.post("/notification/{email_address}")
				.then()
				.statusCode(200);

		String emailBody = MailosaurHelper.waitForEmail(email);
		snsClient.confirmSubscription(ConfirmSubscriptionRequest.builder()
				.topicArn(snsTopicArn).token(extractToken(emailBody)).build());
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
