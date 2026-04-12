package task.iam.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iam.model.Policy;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IamPolicyTest extends BaseIamTest {

	@Test
	@DisplayName("CXQA-IAM-01: Verify that all 3 policies exist in AWS")
	void testPoliciesPresence() {
		List<Policy> allPolicies = iamClient.listPolicies(r -> r.scope("Local")).policies();

		List<String> policyNames = allPolicies.stream()
				.map(Policy::policyName)
				.toList();

		assertThat(policyNames)
				.as("Check that required policies are created")
				.anyMatch(name -> name.contains("FullAccessPolicyEC2"))
				.anyMatch(name -> name.contains("FullAccessPolicyS3"))
				.anyMatch(name -> name.contains("ReadAccessPolicyS3"));
	}

	@Test
	@DisplayName("CXQA-IAM-01: FullAccessPolicyEC2 - ec2:* on all resources")
	void testFullAccessPolicyEc2Content() {
		String encodedDocument = getPolicyDocumentByName("FullAccessPolicyEC2");

		String document = URLDecoder.decode(encodedDocument, StandardCharsets.UTF_8);

		assertThat(document)
				.contains("\"Effect\":\"Allow\"")
				.contains("\"Action\":\"ec2:*\"")
				.contains("\"Resource\":\"*\"");
	}

	@Test
	@DisplayName("CXQA-IAM-01: FullAccessPolicyS3 - s3:* on all resources")
	void testFullAccessPolicyS3Content() {
		String encodedDocument = getPolicyDocumentByName("FullAccessPolicyS3");

		String document = URLDecoder.decode(encodedDocument, StandardCharsets.UTF_8);

		assertThat(document)
				.contains("\"Effect\":\"Allow\"")
				.contains("\"Action\":\"s3:*\"")
				.contains("\"Resource\":\"*\"");
	}

	@Test
	@DisplayName("CXQA-IAM-01: ReadAccessPolicyS3 - describe, get, list actions")
	void testReadAccessPolicyS3Content() {
		String encodedDocument = getPolicyDocumentByName("ReadAccessPolicyS3");

		String document = URLDecoder.decode(encodedDocument, StandardCharsets.UTF_8);

		assertThat(document).contains("\"Effect\":\"Allow\"");
		assertThat(document).contains("\"Resource\":\"*\"");

		assertThat(document)
				.contains("s3:Describe*")
				.contains("s3:Get*")
				.contains("s3:List*");
	}

	private String getPolicyDocumentByName(String partialName) {
		// Find the Amazon Resource Name (ARN) of the policy by filtering local policies
		String policyArn = iamClient.listPolicies(r -> r.scope("Local")).policies().stream()
				.filter(p -> p.policyName().contains(partialName))
				.map(Policy::arn)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Policy with name containing " + partialName + " not found"));

		// Retrieve the policy metadata to identify the current default version ID
		String versionId = iamClient.getPolicy(r -> r.policyArn(policyArn)).policy().defaultVersionId();

		// Fetch the specific version of the policy to get the JSON document
		return iamClient.getPolicyVersion(r -> r.policyArn(policyArn).versionId(versionId))
				.policyVersion().document();
	}
}
