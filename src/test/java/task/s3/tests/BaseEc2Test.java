package task.s3.tests;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

public class BaseEc2Test {
	protected static Ec2Client ec2Client;

	@BeforeAll
	static void setup() {
		ec2Client = Ec2Client.builder()
				.region(Region.EU_CENTRAL_1)
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

	@AfterAll
	public static void tearDown() {
		if (ec2Client != null) {
			ec2Client.close();
		}
	}
}
