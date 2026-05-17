package task.rds.tests;

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
import software.amazon.awssdk.services.rds.RdsClient;

public class BaseRdsTest {
	protected static Ec2Client ec2Client;
	protected static RdsClient rdsClient;
	protected static String publicIp;
	protected static String appInstanceSgId;

	@BeforeAll
	static void setup() {
		Region region = Region.EU_CENTRAL_1;
		DefaultCredentialsProvider credentials = DefaultCredentialsProvider.create();

		ec2Client = Ec2Client.builder().region(region).credentialsProvider(credentials).build();
		rdsClient = RdsClient.builder().region(region).credentialsProvider(credentials).build();

		// Get instance of application and it's data
		DescribeInstancesResponse ec2Response = ec2Client.describeInstances(r -> r
				.filters(f -> f.name("tag:Name").values("*cloudximage*"),
						f -> f.name("instance-state-name").values("running")));

		Instance appInstance = ec2Response.reservations().stream()
				.flatMap(res -> res.instances().stream())
				.findFirst()
				.orElseThrow(() -> new AssertionError("Running EC2 instance not found"));

		publicIp = appInstance.publicIpAddress();

		appInstanceSgId = appInstance.securityGroups().get(0).groupId();

		// Set RestAssured
		RestAssured.baseURI = "http://" + publicIp;
		RestAssured.basePath = "/api";

		RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
		RestAssured.requestSpecification = new RequestSpecBuilder()
				.setContentType(ContentType.JSON)
				.setAccept(ContentType.JSON)
				.build();
	}

	@AfterAll
	static void tearDown() {
		if (ec2Client != null) ec2Client.close();
		if (rdsClient != null) rdsClient.close();
	}
}
