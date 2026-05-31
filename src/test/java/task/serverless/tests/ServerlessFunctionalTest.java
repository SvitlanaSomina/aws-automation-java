package task.serverless.tests;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class ServerlessFunctionalTest extends BaseServerlessTest {
	private String idToDelete;
	private final File file = new File("src/test/resources/test.jpeg");

	private String uploadImage() {
		Object id = given()
				.contentType(ContentType.MULTIPART)
				.multiPart("upfile", file)
				.post("/image")
				.then()
				.statusCode(200)
				.extract().path("id");

		idToDelete = String.valueOf(id);

		// Wait for the Lambda to process the upload and store metadata in DynamoDB
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return idToDelete;
	}

	@AfterEach
	void cleanup() {
		if (idToDelete != null) {
			given()
					.pathParam("id", idToDelete)
					.when()
					.delete("/image/{id}")
					.then()
					.statusCode(anyOf(is(200), is(404)));

			idToDelete = null;
		}
	}

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
	@DisplayName("CXQA-FUNC-01: Verify metadata is stored and accessible via API upon upload")
	void testMetadataIsStored() {
		String uploadedId = uploadImage();

		// Verify metadata
		given()
				.pathParam("id", uploadedId)
				.get("/image/{id}")
				.then()
				.statusCode(200)
				.body("object_size", equalTo((float) file.length()))
				.body("object_key", notNullValue())
				.body("object_type", notNullValue());
	}

	@Test
	@DisplayName("CXQA-FUNC-02: Verify metadata retrieval via GET API")
	void testMetadataRetrieval() {
		String id = uploadImage();

		given()
				.pathParam("id", id)
				.get("/image/{id}")
				.then()
				.statusCode(200)
				.body("id", equalTo(id))
				.body("object_key", notNullValue())
				.body("object_type", notNullValue())
				.body("object_size", notNullValue());
	}

	@Test
	@DisplayName("CXQA-FUNC-03: Verify metadata deletion")
	void testMetadataDeletion() {
		String id = uploadImage();

		// Delete the image metadata
		given()
				.pathParam("id", id)
				.delete("/image/{id}")
				.then()
				.statusCode(anyOf(is(200), is(204)));

		// Wait until the deletion is fully processed (Lambda + DynamoDB consistency)
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// Verify image deleted and metadata is no longer accessible
		given()
				.pathParam("id", id)
				.get("/image/{id}")
				.then()
				.statusCode(404);

		idToDelete = null;
	}
}
