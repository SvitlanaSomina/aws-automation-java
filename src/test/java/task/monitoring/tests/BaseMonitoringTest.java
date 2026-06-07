package task.monitoring.tests;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.lambda.LambdaClient;

public class BaseMonitoringTest {
	protected static CloudWatchLogsClient logsClient;
	protected static CloudTrailClient trailClient;
	protected static Ec2Client ec2Client;
	protected static LambdaClient lambdaClient;

	protected static String instanceId;
	protected static String lambdaUniqueName;

	@BeforeAll
	static void setUp() {
		Region region = Region.EU_CENTRAL_1;
		var credentials = DefaultCredentialsProvider.create();

		logsClient = CloudWatchLogsClient.builder()
				.region(region)
				.credentialsProvider(credentials)
				.build();

		trailClient = CloudTrailClient.builder()
				.region(region)
				.credentialsProvider(credentials)
				.build();

		ec2Client = Ec2Client.builder()
				.region(region)
				.credentialsProvider(credentials)
				.build();

		lambdaClient = LambdaClient.builder()
				.region(region)
				.credentialsProvider(credentials)
				.build();

		// Dynamic search for the running EC2 instance
		var instanceResponse = ec2Client.describeInstances(b -> b.filters(
				f -> f.name("instance-state-name").values("running"),
				f -> f.name("tag:aws:cloudformation:stack-name").values("cloudxserverless")
		));
		Instance appInstance = instanceResponse.reservations().getFirst().instances().getFirst();
		instanceId = appInstance.instanceId();
		String publicIp = appInstance.publicIpAddress();

		// Dynamic search for the Lambda function
		var functionsResponse = lambdaClient.listFunctions();
		lambdaUniqueName = functionsResponse.functions().stream()
				.map(f -> f.functionName())
				.filter(name -> name.startsWith("cloudxserverless-EventHandlerLambda"))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("EventHandlerLambda function not found!"));

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
		if (logsClient != null) logsClient.close();
		if (trailClient != null) trailClient.close();
		if (ec2Client != null) ec2Client.close();
		if (lambdaClient != null) lambdaClient.close();
		RestAssured.reset();
	}
}
