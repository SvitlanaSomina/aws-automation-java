package task.iam.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.Role;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IamRoleTest extends BaseIamTest {

	@Test
	@DisplayName("CXQA-IAM-02: Verify that all 3 IAM roles exist")
	void testRolesPresence() {
		List<String> roleNames = iamClient.listRoles().roles().stream()
				.map(Role::roleName)
				.toList();

		assertThat(roleNames)
				.as("Check that all required roles are created in AWS")
				.anyMatch(name -> name.contains("FullAccessRoleEC2"))
				.anyMatch(name -> name.contains("FullAccessRoleS3"))
				.anyMatch(name -> name.contains("ReadAccessRoleS3"));
	}

	@ParameterizedTest
	@CsvSource({
			"FullAccessRoleEC2, FullAccessPolicyEC2",
			"FullAccessRoleS3, FullAccessPolicyS3",
			"ReadAccessRoleS3, ReadAccessPolicyS3"
	})
	@DisplayName("CXQA-IAM-02: Role to Policy attachment validation")
	void testRoleAttachments(String roleName, String policyName) {
		validateRoleHasPolicy(roleName, policyName);
	}

	private void validateRoleHasPolicy(String rolePartialName, String policyPartialName) {
		// Find the actual role name (handling dynamic CDK suffixes)
		String actualRoleName = iamClient.listRoles().roles().stream()
				.map(Role::roleName)
				.filter(name -> name.contains(rolePartialName))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Role containing '" + rolePartialName + "' not found"));

		// Get the list of policies attached to this role
		List<AttachedPolicy> attachedPolicies = iamClient.listAttachedRolePolicies(r -> r.roleName(actualRoleName))
				.attachedPolicies();

		// Check if any attached policy contains the required name
		assertThat(attachedPolicies)
				.as("Check that role '" + actualRoleName + "' has policy '" + policyPartialName + "' attached")
				.extracting(AttachedPolicy::policyName)
				.anyMatch(name -> name.contains(policyPartialName));
	}
}
