package task.snssqs.tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class SnsSqsDeploymentTest extends BaseSnsSqsTest {

	private static Instance appInstance;
	private static String profileName;

	@BeforeAll
	static void fetchSharedInfrastructure() {
		var ec2Response = ec2Client.describeInstances(b -> b.filters(
				f -> f.name("tag:aws:cloudformation:stack-name").values("cloudximage")
		));

		appInstance = ec2Response.reservations().stream()
				.flatMap(r -> r.instances().stream())
				.findFirst()
				.orElseThrow(() -> new AssertionError("EC2 Application instance not found"));

		String instanceProfileArn = appInstance.iamInstanceProfile().arn();
		profileName = instanceProfileArn.substring(instanceProfileArn.lastIndexOf("/") + 1);
	}

	@Test
	@DisplayName("CXQA-SNSSQS-01: Verify Application Instance IAM Access Permissions")
	void testInstanceIamRolePermissions() {
		var profileResponse = iamClient.getInstanceProfile(b -> b.instanceProfileName(profileName));
		String roleName = profileResponse.instanceProfile().roles().getFirst().roleName();

		List<AttachedPolicy> attachedPolicies = iamClient.listAttachedRolePolicies(b -> b.roleName(roleName)).attachedPolicies();
		List<String> inlinePolicies = iamClient.listRolePolicies(b -> b.roleName(roleName)).policyNames();

		boolean hasSqsAccess = attachedPolicies.stream().anyMatch(p -> p.policyName().toLowerCase().contains("sqs"))
				|| !inlinePolicies.isEmpty();

		boolean hasSnsAccess = attachedPolicies.stream().anyMatch(p -> p.policyName().toLowerCase().contains("sns"))
				|| !inlinePolicies.isEmpty();

		assertAll("IAM Role and Access Validation",
				() -> assertThat(roleName).as("Application EC2 instance must have an IAM Role attached")
						.isNotBlank(),
				() -> assertThat(hasSqsAccess).as("IAM Role must grant application access to the SQS queue")
						.isTrue(),
				() -> assertThat(hasSnsAccess).as("IAM Role must grant application access to the SNS topic")
						.isTrue());
	}

	@Test
	@DisplayName("CXQA-SNSSQS-02: Verify SNS Topic Requirements")
	void testSnsTopicRequirements() {
		var topicsResponse = snsClient.listTopics();
		String topicArn = topicsResponse.topics().stream()
				.map(t -> t.topicArn())
				.filter(arn -> arn.contains("cloudximage-TopicSNSTopic"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("SNS Topic with prefix 'cloudximage-TopicSNSTopic' not found"));

		var attributes = snsClient.getTopicAttributes(b -> b.topicArn(topicArn)).attributes();
		var tagResponse = snsClient.listTagsForResource(b -> b.resourceArn(topicArn));

		boolean isStandard = !topicArn.endsWith(".fifo") && !"true".equalsIgnoreCase(attributes.get("FifoTopic"));
		String kmsKeyId = attributes.get("KmsMasterKeyId");
		boolean isEncryptionDisabled = (kmsKeyId == null) || kmsKeyId.isBlank();

		boolean hasRequiredTag = tagResponse.tags().stream()
				.anyMatch(tag -> "cloudx".equals(tag.key()) && "qa".equals(tag.value()));

		assertAll("SNS Topic Configuration",
				() -> assertThat(topicArn).contains("cloudximage-TopicSNSTopic"),
				() -> assertThat(isStandard).as("Type must be Standard (not FIFO)").isTrue(),
				() -> assertThat(isEncryptionDisabled).as("Encryption must be disabled").isTrue(),
				() -> assertThat(hasRequiredTag).as("Topic must be tagged with 'cloudx: qa'").isTrue());
	}

	@Test
	@DisplayName("CXQA-SNSSQS-03: Verify SQS Queue Requirements")
	void testSqsQueueRequirements() {
		var queuesResponse = sqsClient.listQueues(b -> b.queueNamePrefix("cloudximage-QueueSQSQueue"));
		String queueUrl = queuesResponse.queueUrls().stream()
				.findFirst().orElseThrow(() -> new AssertionError("SQS Queue with prefix 'cloudximage-QueueSQSQueue' not found"));

		var attributesResponse = sqsClient.getQueueAttributes(b -> b.queueUrl(queueUrl)
				.attributeNamesWithStrings("SqsManagedSseEnabled", "KmsMasterKeyId", "RedrivePolicy"));
		var attributes = attributesResponse.attributes();
		var tagsResponse = sqsClient.listQueueTags(b -> b.queueUrl(queueUrl));

		boolean isStandard = !queueUrl.endsWith(".fifo");
		boolean isEncryptionEnabled = "true".equalsIgnoreCase(attributes.get(QueueAttributeName.SQS_MANAGED_SSE_ENABLED))
				|| attributes.containsKey(QueueAttributeName.KMS_MASTER_KEY_ID);

		boolean hasNoDlq = !attributes.containsKey(QueueAttributeName.REDRIVE_POLICY) ||
				attributes.get(QueueAttributeName.REDRIVE_POLICY).isBlank();

		boolean hasRequiredTag = tagsResponse.tags().entrySet().stream()
				.anyMatch(entry -> "cloudx".equals(entry.getKey()) && "qa".equals(entry.getValue()));

		assertAll("SQS Queue Configuration",
				() -> assertThat(queueUrl).contains("cloudximage-QueueSQSQueue"),
				() -> assertThat(isStandard).as("Type must be Standard (not FIFO)").isTrue(),
				() -> assertThat(isEncryptionEnabled).as("Encryption must be enabled").isTrue(),
				() -> assertThat(hasNoDlq).as("Dead-letter queue must be disabled").isTrue(),
				() -> assertThat(hasRequiredTag).as("Queue must be tagged with 'cloudx: qa'").isTrue());
	}
}
