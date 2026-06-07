package task.snssqs.tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

public class BaseSnsSqsTest {
	protected static Ec2Client ec2Client;
	protected static SnsClient snsClient;
	protected static SqsClient sqsClient;
	protected static IamClient iamClient;

	@BeforeAll
	static void setup() {
		var credentialsProvider = DefaultCredentialsProvider.create();
		Region region = Region.EU_CENTRAL_1;

		ec2Client = Ec2Client.builder()
				.region(region)
				.credentialsProvider(credentialsProvider)
				.build();

		snsClient = SnsClient.builder()
				.region(region)
				.credentialsProvider(credentialsProvider)
				.build();

		sqsClient = SqsClient.builder()
				.region(region)
				.credentialsProvider(credentialsProvider)
				.build();

		iamClient = IamClient.builder()
				.region(Region.EU_CENTRAL_1)
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

	@AfterAll
	public static void tearDown() {
		if (ec2Client != null) ec2Client.close();
		if (snsClient != null) snsClient.close();
		if (sqsClient != null) sqsClient.close();
		if (iamClient != null) iamClient.close();
	}
}
