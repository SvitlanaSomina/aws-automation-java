package task.ec2.tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import task.ec2.utils.Ec2Utils;

import static org.hamcrest.Matchers.equalTo;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

public class ApiFunctionalTest extends BaseApiTest {
	private static final String PRIVATE_URL = "http://localhost:8080";
	private static final String REGION = "eu-central-1";

	private static String publicUrl;

	@BeforeAll
	static void setupTestData() {
		publicUrl = Ec2Utils.getPublicIp();
	}

	@Test
	@DisplayName("CXQA-EC2-04: Validate metadata API for Public Instance")
	void testPublicInstanceMetadata() {
		given()
				.baseUri(publicUrl)
		.when()
				.get("/")
		.then()
				.statusCode(200)
				.body("region", equalTo(REGION))
				.body("availability_zone", startsWith(REGION))
				.body("private_ipv4", notNullValue());
	}

	@Test
	@DisplayName("CXQA-EC2-04: Validate metadata API for Private Instance")
	void testPrivateInstanceMetadata() {
		given()
				.baseUri(PRIVATE_URL)
		.when()
				.get("/")
		.then()
				.statusCode(200)
				.body("region", equalTo(REGION))
				.body("availability_zone", startsWith(REGION))
				.body("private_ipv4", startsWith("10."));
	}
}