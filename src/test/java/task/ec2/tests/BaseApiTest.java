package task.ec2.tests;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;

public class BaseApiTest {
	@BeforeAll
	static void setup() {
		RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
		RestAssured.requestSpecification = RestAssured.given()
				.contentType(ContentType.JSON)
				.accept(ContentType.JSON);
	}
}
