package task.iam.tests;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;

public class BaseIamTest {
	protected static IamClient iamClient;

	@BeforeAll
	static void setup() {
		iamClient = IamClient.builder()
				.region(Region.EU_CENTRAL_1)
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

	@AfterAll
	public static void tearDown() {
		if (iamClient != null) {
			iamClient.close();
		}
	}
}
