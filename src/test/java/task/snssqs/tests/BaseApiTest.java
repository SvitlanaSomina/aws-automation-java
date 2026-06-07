package task.snssqs.tests;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.sns.SnsClient;

public class BaseApiTest {
	protected static String publicIp;
	protected static SnsClient snsClient;
	protected static String snsTopicArn;

	protected static final String MAILOSAUR_DOMAIN = System.getenv("MAILOSAUR_DOMAIN");

	@BeforeAll
	static void setup() {
		if (MAILOSAUR_DOMAIN == null || MAILOSAUR_DOMAIN.isBlank()) {
			throw new IllegalStateException("Environment variable MAILOSAUR_DOMAIN is not set!");
		}

		// AWS clients initialization
		snsClient = SnsClient.builder()
				.region(Region.EU_CENTRAL_1)
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();

		snsTopicArn = snsClient.listTopics().topics().stream()
				.map(t -> t.topicArn())
				.filter(arn -> arn.contains("cloudximage-TopicSNSTopic"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("SNS Topic with prefix 'cloudximage-TopicSNSTopic' not found"));

		try (Ec2Client ec2Client = Ec2Client.builder()
				.region(Region.EU_CENTRAL_1)
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build()) {

			DescribeInstancesResponse response = ec2Client.describeInstances(r -> r
					.filters(f -> f.name("instance-state-name").values("running")));

			publicIp = response.reservations().stream()
					.flatMap(res -> res.instances().stream())
					.filter(i -> i.tags().stream().anyMatch(t -> t.value().contains("cloudximage")))
					.map(Instance::publicIpAddress)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Running EC2 instance not found"));
		}

		// RestAssured configuration
		RestAssured.baseURI = "http://" + publicIp;
		RestAssured.basePath = "/api";
		RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
		RestAssured.requestSpecification = new RequestSpecBuilder()
				.setContentType(ContentType.JSON)
				.setAccept(ContentType.JSON)
				.build();
	}

	@AfterAll
	static void teardown() {
		if (snsClient != null) snsClient.close();
	}
}
