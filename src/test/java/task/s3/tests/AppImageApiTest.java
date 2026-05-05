package task.s3.tests;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class AppImageApiTest extends BaseApiTest {
	private final String testFilePath = "src/test/resources/test.jpeg";
	private String idToDelete;

	@AfterEach
	void cleanup() {
		if (idToDelete != null) {
			given()
					.pathParam("id", idToDelete)
					.when()
					.delete("/api/images/{id}")
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
	@DisplayName("CXQA-S3-03: Verify upload images to the S3 bucket")
	void testUploadImage() {
		File imageFile = new File(testFilePath);
		assertThat(imageFile.exists()).as("Test image file must be in src/test/resources/").isTrue();

		Object uploadedId = given()
				.contentType(ContentType.MULTIPART)
				.multiPart("upfile", imageFile)
		.when()
				.post("/image")
		.then()
				.statusCode(200)
				.body("id", notNullValue())
				.extract().path("id");
		idToDelete = String.valueOf(uploadedId);
	}

	@Test
	@DisplayName("CXQA-S3-05: View a list of uploaded images")
	void testViewImageList() {
		String imageId = uploadImagePrecondition();

		given()
		.when()
				.get("/image")
		.then()
				.statusCode(200)
				.contentType(ContentType.JSON)
				.body("id", hasItem(Integer.valueOf(imageId)));
	}

	@Test
	@DisplayName("CXQA-S3-04: Verify download images from the S3 bucket")
	void testDownloadImage() {
		String imageId = uploadImagePrecondition();
		File originalFile = new File(testFilePath);

		byte[] downloadedContent = given()
				.pathParam("image_id", Integer.valueOf(imageId))
		.when()
				.get("/image/file/{image_id}")
		.then()
				.statusCode(200)
				.contentType(containsString("image/"))
				.extract().asByteArray();

		assertThat(downloadedContent.length)
				.as("Downloaded content should match original file size")
				.isEqualTo(originalFile.length());
	}

	@Test
	@DisplayName("CXQA-S3-06: Verify delete an image from the S3 bucket")
	void testDeleteImage() {
		String imageId = uploadImagePrecondition();

		given()
				.pathParam("image_id", Integer.valueOf(imageId))
		.when()
				.delete("/image/{image_id}")
		.then()
				.statusCode(200);

		idToDelete = null;

		// Verify that the image is no longer in the list
		given()
		.when()
				.get("/image")
		.then()
				.statusCode(200)
				.body("id", not(hasItem(Integer.valueOf(imageId))));
	}

	private String uploadImagePrecondition() {
		Object id = given()
				.contentType(ContentType.MULTIPART)
				.multiPart("upfile", new File(testFilePath))
		.when()
				.post("/image")
		.then()
				.statusCode(200)
				.extract().path("id");
		idToDelete = String.valueOf(id);
		return idToDelete;
	}
}
