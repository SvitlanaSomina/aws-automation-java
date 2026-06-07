package task.monitoring.tests;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class ServerlessAppLoggingTest extends BaseMonitoringTest {


	@Test
	@DisplayName("CXQA-MON-06, 07: Verify Functional Application HTTP Requests and Lambda Metadata Logging")
	void testApplicationAndLambdaLogging() throws InterruptedException {
		// Generate events by uploading an image, which should trigger both application logs and Lambda processing
		File file = new File("src/test/resources/test.jpeg");

		String uploadedImageId = given()
				.contentType(ContentType.MULTIPART)
				.multiPart("upfile", file)
				.when()
				.post("/image")
				.then()
				.statusCode(200)
				.extract()
				.path("id");

		// Wait 10 seconds (CloudWatch requires time to aggregate and ingest logs)
		Thread.sleep(35000);

		// Validation for CXQA-MON-07: Application HTTP API requests logging
		FilterLogEventsResponse appLogsResponse = logsClient.filterLogEvents(b -> b
				.logGroupName("/var/log/cloudxserverless-app")
				.filterPattern("POST")
				.limit(20)
		);

		// Verify that the application log stream contains the specific upload endpoint
		String allAppLogsText = appLogsResponse.events().stream()
				.map(e -> e.message())
				.reduce("", (acc, el) -> acc + el);

		boolean hasAppLoggedHttpRequest = allAppLogsText.contains("/image");

		// Validation for CXQA-MON-06: Event and metadata logging by Lambda
		String lambdaLogGroup = "/aws/lambda/" + lambdaUniqueName;

		// CRITICAL: Filter Lambda logs strictly by the unique image ID to ensure
		// that Lambda processed OUR specific notification (Each notification event processed is logged)
		FilterLogEventsResponse lambdaLogsResponse = logsClient.filterLogEvents(b -> b
				.logGroupName(lambdaLogGroup)
				.filterPattern(uploadedImageId)
				.limit(10)
		);

		String allLambdaLogsText = lambdaLogsResponse.events().stream()
				.map(e -> e.message().toLowerCase()) // Переводим в нижний регистр для стабильности проверок
				.reduce("", (acc, el) -> acc + el);

		// Comprehensive assertion of all functional logging requirements
		assertAll("Monitoring and Logging Validation",
				// Verification for requirement CXQA-MON-07
				() -> assertThat(hasAppLoggedHttpRequest)
						.as("CXQA-MON-07: Log group /var/log/cloudxserverless-app must log handled HTTP API requests")
						.isTrue(),
				() -> assertThat(allLambdaLogsText)
						.as("CXQA-MON-06: Lambda log group must contain logs for processed notification event with ID: " + uploadedImageId)
						.isNotEmpty(),
				() -> assertThat(allLambdaLogsText)
						.as("CXQA-MON-06: Lambda logs must include object key / image ID information")
						.contains(uploadedImageId.toLowerCase()),
				() -> assertThat(allLambdaLogsText)
						.as("CXQA-MON-06: Lambda logs must include object type (image/jpeg)")
						.contains("jpeg"),
				() -> assertThat(allLambdaLogsText)
						.as("CXQA-MON-06: Lambda logs must include object size info")
						.matches(s -> s.contains("size") || s.contains("bytes") || s.contains("length")),
				() -> assertThat(allLambdaLogsText)
						.as("CXQA-MON-06: Lambda logs must include modification date info")
						.matches(s -> s.contains("date") || s.contains("time") || s.contains("timestamp")),
				() -> assertThat(allLambdaLogsText)
						.as("CXQA-MON-06: Lambda logs must include download link info (url/s3/link)")
						.matches(s -> s.contains("link") || s.contains("url") || s.contains("http") || s.contains("s3"))
		);
	}
}
