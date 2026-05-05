package task.s3.tests;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;

public class BaseApiTest {
	protected static String publicIp;

	@BeforeAll
	static void setup() {
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

		RestAssured.baseURI = "http://" + publicIp;
		RestAssured.basePath = "/api";

		RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

		RestAssured.requestSpecification = new RequestSpecBuilder()
				.setContentType(ContentType.JSON)
				.setAccept(ContentType.JSON)
				.build();
	}
}
