package task.iam.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.services.iam.model.User;
import software.amazon.awssdk.services.iam.model.Group;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IamUserTest extends BaseIamTest {

	@Test
	@DisplayName("CXQA-IAM-04: Verify that all 3 IAM users exist")
	void testUsersPresence() {
		// Fetch all user names from the current AWS account
		List<String> userNames = iamClient.listUsers().users().stream()
				.map(User::userName)
				.toList();

		// Verify that the three required users are present (ignoring dynamic CDK suffixes)
		assertThat(userNames)
				.as("Check if all 3 required users are present in AWS")
				.anyMatch(name -> name.contains("FullAccessUserEC2"))
				.anyMatch(name -> name.contains("FullAccessUserS3"))
				.anyMatch(name -> name.contains("ReadAccessUserS3"));
	}

	@ParameterizedTest
	@CsvSource({
			"FullAccessUserEC2, FullAccessGroupEC2",
			"FullAccessUserS3, FullAccessGroupS3",
			"ReadAccessUserS3, ReadAccessGroupS3"
	})
	@DisplayName("CXQA-IAM-04: Verify user membership in specific groups")
	void testUserGroupMembership(String userPart, String groupPart) {
		validateUserInGroup(userPart, groupPart);
	}

	private void validateUserInGroup(String userPartialName, String groupPartialName) {
		// Find the exact user name currently deployed in the environment
		String actualUserName = iamClient.listUsers().users().stream()
				.map(User::userName)
				.filter(name -> name.contains(userPartialName))
				.findFirst()
				.orElseThrow(() -> new AssertionError("User containing '" + userPartialName + "' not found"));

		// Retrieve the list of groups associated with this specific user
		List<Group> userGroups = iamClient.listGroupsForUser(r -> r.userName(actualUserName)).groups();

		// Assert that the required group is present in the user's group list
		assertThat(userGroups)
				.as("Checking if user '%s' is a member of group '%s'", actualUserName, groupPartialName)
				.extracting(Group::groupName)
				.anyMatch(name -> name.contains(groupPartialName));
	}
}
