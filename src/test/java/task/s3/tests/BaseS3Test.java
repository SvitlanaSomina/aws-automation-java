package task.s3.tests;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

public class BaseS3Test {
	protected static S3Client s3Client;

	@BeforeAll
	static void setup() {
		s3Client = S3Client.builder()
				.region(Region.EU_CENTRAL_1)
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

	@AfterAll
	public static void tearDown() {
		if (s3Client != null) {
			s3Client.close();
		}
	}
}
