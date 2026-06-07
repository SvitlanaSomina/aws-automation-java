package task.rds.tests;

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

public class RdsFunctionalTest extends BaseRdsTest {
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
	@DisplayName("CXQA-RDS-03: Verify metadata is stored in RDS upon upload")
	void testMetadataIsStored() {
		// Upload the image and get the ID
		idToDelete = given()
				.contentType(ContentType.MULTIPART)
				.multiPart("upfile", file)
				.post("/image")
		.then()
				.statusCode(200)
				.body("id", notNullValue())
				.extract().path("id").toString();

		// Verify metadata is stored
		given()
				.pathParam("id", idToDelete)
				.get("/image/{id}")
		.then()
				.statusCode(200)
				.body("object_size", equalTo((int) file.length()))
				.body("object_key", notNullValue())
				.body("object_type", notNullValue())
				.body("last_modified", notNullValue());
	}

	@Test
	@DisplayName("CXQA-RDS-04: Verify metadata retrieval via GET API")
	void testMetadataRetrieval() {
		String id = uploadImage();

		given()
				.pathParam("id", id)
				.get("/image/{id}")
		.then()
				.statusCode(200)
				.body("id", equalTo(Integer.parseInt(id)))
				.body("object_key", notNullValue())
				.body("object_type", notNullValue())
				.body("object_size", notNullValue())
				.body("last_modified", notNullValue());
	}

	@Test
	@DisplayName("CXQA-RDS-05: Verify metadata deletion")
	void testMetadataDeletion() {
		String id = uploadImage();

		given()
				.pathParam("id", id)
				.delete("/image/{id}")
		.then()
				.statusCode(200);

		given()
		        .pathParam("id", id)
				.get("/image/{id}")
		.then()
				.statusCode(404);

		idToDelete = null;
	}
}
