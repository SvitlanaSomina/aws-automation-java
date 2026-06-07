package task.serverless.tests;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.lambda.model.EventSourceMappingConfiguration;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;

import java.io.File;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class ServerlessDeploymentValidationTest extends BaseServerlessTest {
	private final File testFile = new File("src/test/resources/test.jpeg");
	private String idToDelete;

	private String uploadImage() {
		Object id = given()
				.contentType(ContentType.MULTIPART)
				.multiPart("upfile", testFile)
				.post("/image")
				.then()
				.statusCode(200)
				.extract().path("id");

		idToDelete = String.valueOf(id);
		return idToDelete;
	}

	@Test
	@DisplayName("CXQA-SLESS-01: Verify DynamoDB Table infrastructure and AutoScaling rules")
	void testDynamoDbConfiguration() {
		String tableName = getDynamoDbTableName();

		TableDescription tableDescription = dynamoDbClient.describeTable(b -> b.tableName(tableName)).table();

		String ttlStatus = dynamoDbClient.describeTimeToLive(b -> b.tableName(tableName))
				.timeToLiveDescription().timeToLiveStatusAsString();

		var tags = dynamoDbClient.listTagsOfResource(b -> b.resourceArn(tableDescription.tableArn())).tags();
		boolean hasCorrectTag = tags.stream()
				.anyMatch(t -> t.key().equals("cloudx") && t.value().equals("qa"));

		var scalableTargets = autoScalingClient.describeScalableTargets(b -> b
				.serviceNamespace(ServiceNamespace.DYNAMODB)
				.resourceIds("table/" + tableName)).scalableTargets();

		boolean isReadAutoscalingOn = scalableTargets.stream()
				.anyMatch(t -> t.scalableDimensionAsString().equals("dynamodb:table:ReadCapacityUnits"));

		boolean isWriteAutoscalingCorrect = scalableTargets.stream()
				.anyMatch(t -> t.scalableDimensionAsString().equals("dynamodb:table:WriteCapacityUnits")
						&& t.minCapacity() == 1
						&& t.maxCapacity() == 5);

		assertAll("CXQA-SLESS-01: DynamoDB Verification",
				() -> assertThat(tableDescription.tableName())
						.as("Table name must match pattern")
						.startsWith("cloudxserverless-DatabaseImagesTable"),
				() -> assertThat(tableDescription.globalSecondaryIndexes())
						.as("Global secondary indexes must be disabled")
						.isEmpty(),
				() -> assertThat(tableDescription.provisionedThroughput().readCapacityUnits())
						.as("Provisioned read capacity units must be 5")
						.isEqualTo(5L),
				() -> assertThat(isReadAutoscalingOn)
						.as("Autoscaling for reads must be Off")
						.isFalse(),
				() -> assertThat(isWriteAutoscalingCorrect)
						.as("Autoscaling for writes must be On with capacity 1-5")
						.isTrue(),
				() -> assertThat(ttlStatus)
						.as("Time to Live must be disabled")
						.isEqualTo("DISABLED"),
				() -> assertThat(hasCorrectTag)
						.as("Table must have tag 'cloudx: qa'")
						.isTrue()
		);
	}

	@Test
	@DisplayName("CXQA-SLESS-02: Verify DynamoDB table structure stores image metadata attributes")
	void testDynamoDbMetadataFields() {
		uploadImage();

		// Give the serverless chain (SQS -> Lambda) 5 seconds to write metadata to DynamoDB
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		String tableName = getDynamoDbTableName();

		var scanResponse = dynamoDbClient.scan(b -> b.tableName(tableName).limit(1));

		assertThat(scanResponse.items())
				.as("DynamoDB table should not be empty after image upload.")
				.isNotEmpty();

		var sampleItem = scanResponse.items().getFirst();

		assertAll("CXQA-SLESS-02: Metadata Attributes",
				() -> assertThat(sampleItem.keySet().stream().anyMatch(k -> k.toLowerCase().contains("creat")))
						.as("Table must store object creation-time").isTrue(),

				() -> assertThat(sampleItem.keySet().stream().anyMatch(k -> k.toLowerCase().contains("modif") || k.toLowerCase().contains("updat")))
						.as("Table must store object last modification date-time").isTrue(),

				() -> assertThat(sampleItem.keySet().stream().anyMatch(k -> k.toLowerCase().contains("key")))
						.as("Table must store object key").isTrue(),

				() -> assertThat(sampleItem.keySet().stream().anyMatch(k -> k.toLowerCase().contains("size")))
						.as("Table must store object size").isTrue(),

				() -> assertThat(sampleItem.keySet().stream().anyMatch(k -> k.toLowerCase().contains("type")))
						.as("Table must store object type").isTrue()
		);
	}

	@Test
	@DisplayName("CXQA-SLESS-03: Verify API can subscribe, list, and unsubscribe users via SNS topic")
	void testSnsTopicSubscriptionsViaApi() {
		// Generate email
		String testEmail = java.util.UUID.randomUUID().toString().substring(0, 8) + "@" + MailosaurHelper.MAILOSAUR_DOMAIN;

		// Subscribe user
		RestAssured.given()
				.contentType(ContentType.JSON)
				.post("/notification/" + testEmail)
				.then()
				.statusCode(200);

		// Get token from confirmation email
		String emailBody = MailosaurHelper.waitForEmail(testEmail);
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Token=([^\"&\\s]+)").matcher(emailBody);

		if (!matcher.find()) {
			throw new AssertionError("Token not found in confirmation email!");
		}
		String token = matcher.group(1);

		// Get Arn of the SNS topic used by the application
		String topicArn = snsClient.listTopics().topics().stream()
				.map(t -> t.topicArn())
				.filter(arn -> arn.contains("cloudxserverless"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Application SNS Topic not found"));

		// Confirm subscription
		snsClient.confirmSubscription(b -> b.topicArn(topicArn).token(token));

		try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

		// Get list and check that email is present in the list of subscriptions
		List<String> endpointsAfterSubscribe = RestAssured.given()
				.get("/notification")
				.then()
				.statusCode(200)
				.extract().path("Endpoint");

		assertThat(endpointsAfterSubscribe)
				.as("The email should be present in the 'Endpoint' field of the subscriptions list")
				.contains(testEmail);

		// Delete subscription via API
		RestAssured.given()
				.delete("/notification/" + testEmail)
				.then()
				.statusCode(200); // Или 204

		try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

		// Verify that email is removed from the list of subscriptions
		List<String> endpointsAfterDelete = RestAssured.given()
				.get("/notification")
				.then()
				.statusCode(200)
				.extract().path("Endpoint");

		assertThat(endpointsAfterDelete)
				.as("The email should be removed from the 'Endpoint' list after DELETE request")
				.doesNotContain(testEmail);
	}

	@Test
	@DisplayName("CXQA-SLESS-04: Verify SQS Queue exists for publishing event messages")
	void testSqsQueueConfiguration() {
		List<String> queueUrls = sqsClient.listQueues().queueUrls();
		String appQueueUrl = queueUrls.stream()
				.filter(url -> url.contains("cloudxserverless"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Application SQS Queue not found"));

		var attributes = sqsClient.getQueueAttributes(b -> b.queueUrl(appQueueUrl)
				.attributeNamesWithStrings("QueueArn")).attributesAsStrings();

		assertAll("CXQA-SLESS-04: SQS Queue Verification",
				() -> assertThat(appQueueUrl).as("SQS Queue URL should be valid").isNotBlank(),
				() -> assertThat(attributes).as("SQS Queue must have a valid ARN attribute").containsKey("QueueArn")
		);
	}

	@Test
	@DisplayName("CXQA-SLESS-05: Verify Lambda function is subscribed to the SQS queue")
	void testLambdaSqsSubscription() {
		String lambdaName = getLambdaFunctionName();

		// Get event source mappings (triggers) for Lambda
		List<EventSourceMappingConfiguration> eventSources = lambdaClient
				.listEventSourceMappings(b -> b.functionName(lambdaName)).eventSourceMappings();

		// Look for a trigger whose event source is SQS and that is enabled
		boolean hasActiveSqsTrigger = eventSources.stream()
				.anyMatch(mapping -> mapping.eventSourceArn().contains(":sqs:")
						&& mapping.state().equals("Enabled"));

		assertThat(hasActiveSqsTrigger)
				.as("A lambda function must be subscribed to the SQS queue and the trigger state must be Enabled")
				.isTrue();
	}

	@Test
	@DisplayName("CXQA-SLESS-06: Verify IAM Roles provide explicit access to S3, DynamoDB, SQS, and SNS")
	void testIamRolesDeepAccessCheck() {
		StringBuilder combinedPolicyData = new StringBuilder();

		String lambdaName = getLambdaFunctionName();
		String lambdaRoleArn = lambdaClient.getFunction(b -> b.functionName(lambdaName)).configuration().role();
		String lambdaRoleName = lambdaRoleArn.substring(lambdaRoleArn.lastIndexOf("/") + 1);

		var ec2Response = ec2Client.describeInstances(r -> r
				.filters(f -> f.name("tag:Name").values("*cloudxserverless*"),
						f -> f.name("instance-state-name").values("running")));
		var instance = ec2Response.reservations().get(0).instances().get(0);

		String profileArn = instance.iamInstanceProfile().arn();
		String profileName = profileArn.substring(profileArn.lastIndexOf("/") + 1);
		String ec2RoleName = iamClient.getInstanceProfile(b -> b.instanceProfileName(profileName))
				.instanceProfile().roles().get(0).roleName();

		List<String> rolesToCheck = java.util.Arrays.asList(lambdaRoleName, ec2RoleName);

		for (String roleName : rolesToCheck) {
			var inlinePolicies = iamClient.listRolePolicies(b -> b.roleName(roleName)).policyNames();
			for (String policyName : inlinePolicies) {
				String encodedDocument = iamClient.getRolePolicy(b -> b.roleName(roleName).policyName(policyName)).policyDocument();
				String decodedDocument = java.net.URLDecoder.decode(encodedDocument, java.nio.charset.StandardCharsets.UTF_8);
				combinedPolicyData.append(decodedDocument.toLowerCase());
			}

			var attachedPolicies = iamClient.listAttachedRolePolicies(b -> b.roleName(roleName)).attachedPolicies();
			for (var policy : attachedPolicies) {
				combinedPolicyData.append(policy.policyName().toLowerCase()).append(policy.policyArn().toLowerCase());
			}
		}

		String allPermissionsText = combinedPolicyData.toString();

		assertAll("CXQA-SLESS-06: IAM Roles Permissions Scanning",
				() -> assertThat(allPermissionsText.contains("s3") || allPermissionsText.contains("bucket"))
						.as("IAM Role policies must grant access to S3 (bucket)")
						.isTrue(),

				() -> assertThat(allPermissionsText.contains("dynamodb"))
						.as("IAM Role policies must grant access to DynamoDB")
						.isTrue(),

				() -> assertThat(allPermissionsText.contains("sqs"))
						.as("IAM Role policies must grant access to SQS")
						.isTrue(),

				() -> assertThat(allPermissionsText.contains("sns") || allPermissionsText.contains("topic"))
						.as("IAM Role policies must grant access to SNS (topic)")
						.isTrue()
		);
	}

	@Test
	@DisplayName("CXQA-SLESS-07: Verify AWS Lambda computing, logging, and tags requirements")
	void testLambdaConfiguration() {
		String lambdaName = getLambdaFunctionName();

		// Get Lambda function configuration and tags
		var functionResponse = lambdaClient.getFunction(b -> b.functionName(lambdaName));
		FunctionConfiguration config = functionResponse.configuration();
		var tags = functionResponse.tags();

		List<EventSourceMappingConfiguration> eventSources = lambdaClient
				.listEventSourceMappings(b -> b.functionName(lambdaName)).eventSourceMappings();
		boolean hasSqsTrigger = eventSources.stream().anyMatch(mapping -> mapping.eventSourceArn().contains(":sqs:"));

		assertAll("CXQA-SLESS-07: Lambda Specifications",
				() -> assertThat(hasSqsTrigger)
						.as("Lambda Trigger must be SQS Queue")
						.isTrue(),
				() -> assertThat(config.loggingConfig().logGroup())
						.as("Lambda application logs must be stored in specific CloudWatch log group")
						.contains("aws/lambda/cloudx-associate-aws-for-testers-v3"),
				() -> assertThat(config.memorySize())
						.as("Lambda Memory must be exactly 128 MB")
						.isEqualTo(128),
				() -> assertThat(config.ephemeralStorage().size())
						.as("Lambda Ephemeral storage must be exactly 512 MB")
						.isEqualTo(512),
				() -> assertThat(config.timeout())
						.as("Lambda Timeout must be exactly 3 seconds")
						.isEqualTo(3),
				() -> assertThat(tags)
						.as("Lambda must have tag 'cloudx: qa'")
						.containsEntry("cloudx", "qa")
		);
	}
}
