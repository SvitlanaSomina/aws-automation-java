package task.serverless.tests;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ec2.Ec2Client;

public class BaseServerlessTest {
	protected static DynamoDbClient dynamoDbClient;
	protected static LambdaClient lambdaClient;
	protected static SnsClient snsClient;
	protected static SqsClient sqsClient;
	protected static IamClient iamClient;
	protected static ApplicationAutoScalingClient autoScalingClient;
	protected static Ec2Client ec2Client;

	protected static String publicIp;

	private static final Region AWS_REGION = Region.EU_CENTRAL_1;

	@BeforeAll
	public static void setup() {
		var credentialsProvider = DefaultCredentialsProvider.create();

		dynamoDbClient = DynamoDbClient.builder()
				.region(AWS_REGION)
				.credentialsProvider(credentialsProvider)
				.build();

		lambdaClient = LambdaClient.builder()
				.region(AWS_REGION)
				.credentialsProvider(credentialsProvider)
				.build();

		snsClient = SnsClient.builder()
				.region(AWS_REGION)
				.credentialsProvider(credentialsProvider)
				.build();

		sqsClient = SqsClient.builder()
				.region(AWS_REGION)
				.credentialsProvider(credentialsProvider)
				.build();

		iamClient = IamClient.builder()
				.region(AWS_REGION)
				.credentialsProvider(credentialsProvider)
				.build();

		autoScalingClient = ApplicationAutoScalingClient.builder()
				.region(AWS_REGION)
				.credentialsProvider(credentialsProvider)
				.build();

		ec2Client = Ec2Client.builder()
				.region(AWS_REGION).credentialsProvider(credentialsProvider)
				.build();

		DescribeInstancesResponse ec2Response = ec2Client.describeInstances(r -> r
				.filters(f -> f.name("tag:Name").values("*cloudxserverless*"),
						f -> f.name("instance-state-name").values("running")));

		Instance appInstance = ec2Response.reservations().stream()
				.flatMap(res -> res.instances().stream())
				.findFirst()
				.orElseThrow(() -> new AssertionError("Running EC2 instance not found"));

		publicIp = appInstance.publicIpAddress();

		// Set RestAssured
		RestAssured.baseURI = "http://" + publicIp;
		RestAssured.basePath = "/api";
		RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
		RestAssured.requestSpecification = new RequestSpecBuilder()
				.setAccept(ContentType.JSON)
				.build();
	}

	@AfterAll
	public static void tearDown() {
		if (dynamoDbClient != null) dynamoDbClient.close();
		if (lambdaClient != null) lambdaClient.close();
		if (snsClient != null) snsClient.close();
		if (sqsClient != null) sqsClient.close();
		if (iamClient != null) iamClient.close();
		if (autoScalingClient != null) autoScalingClient.close();
	}

	protected String getDynamoDbTableName() {
		return dynamoDbClient.listTables().tableNames().stream()
				.filter(name -> name.startsWith("cloudxserverless-DatabaseImagesTable"))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("DynamoDB table starting with 'cloudxserverless-DatabaseImagesTable' not found."));
	}

	protected String getLambdaFunctionName() {
		return lambdaClient.listFunctions().functions().stream()
				.filter(f -> f.functionName().contains("cloudxserverless"))
				.filter(f -> !f.functionName().contains("AutoDelete") && !f.functionName().contains("CustomResource"))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("Lambda function not found"))
				.functionName();
	}
}
