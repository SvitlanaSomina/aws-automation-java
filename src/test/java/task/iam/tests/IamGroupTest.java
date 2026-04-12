package task.iam.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.Group;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IamGroupTest extends BaseIamTest {

	@Test
	@DisplayName("CXQA-IAM-03: Verify that all 3 IAM groups exist")
	void testGroupsPresence() {
		List<String> groupNames = iamClient.listGroups().groups().stream()
				.map(Group::groupName)
				.toList();

		assertThat(groupNames)
				.as("Check that all required groups are created in AWS")
				.anyMatch(name -> name.contains("FullAccessGroupEC2"))
				.anyMatch(name -> name.contains("FullAccessGroupS3"))
				.anyMatch(name -> name.contains("ReadAccessGroupS3"));
	}

	@ParameterizedTest
	@CsvSource({
			"FullAccessGroupEC2, FullAccessPolicyEC2",
			"FullAccessGroupS3, FullAccessPolicyS3",
			"ReadAccessGroupS3, ReadAccessPolicyS3"
	})
	@DisplayName("CXQA-IAM-03: Verify policy attachment for groups")
	void testGroupPolicyAttachments(String groupPart, String policyPart) {
		validateGroupHasPolicy(groupPart, policyPart);
	}

	private void validateGroupHasPolicy(String groupPartialName, String policyPartialName) {
		// Find exact group name
		String actualGroupName = iamClient.listGroups().groups().stream()
				.map(Group::groupName)
				.filter(name -> name.contains(groupPartialName))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Group containing '" + groupPartialName + "' not found"));

		// Get attached policies
		List<AttachedPolicy> attachedPolicies = iamClient.listAttachedGroupPolicies(r -> r.groupName(actualGroupName))
				.attachedPolicies();

		assertThat(attachedPolicies)
				.as("Group '" + actualGroupName + "' should have policy '" + policyPartialName + "'")
				.extracting(AttachedPolicy::policyName)
				.anyMatch(name -> name.contains(policyPartialName));
	}
}
