package task.s3.tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class Ec2DeploymentTest extends BaseEc2Test {
	private static Instance appInstance;

	@BeforeAll
	static void findInstance() {
		DescribeInstancesResponse response = ec2Client.describeInstances(r -> r
				.filters(f -> f.name("instance-state-name").values("running")));

		appInstance = response.reservations().stream()
				.flatMap(res -> res.instances().stream())
				.filter(i -> i.tags().stream().anyMatch(t -> t.value().contains("cloudximage")))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Running EC2 instance for cloudximage not found"));
	}

	@Test
	@DisplayName("CXQA-S3-01: Verify that instance has public IP and FQDN")
	void testPublicConnectivity() {
		assertAll("Public Access",
				() -> assertThat(appInstance.publicIpAddress())
						.as("Instance must have a public IP")
						.isNotBlank(),
				() -> assertThat(appInstance.publicDnsName())
						.as("Instance must have a public FQDN (DNS name)")
						.isNotBlank()
		);
	}

	@Test
	@DisplayName("CXQA-S3-01: Verify thet Security Group allows HTTP and SSH")
	void testSecurityGroupRules() {
		String groupId = appInstance.securityGroups().getFirst().groupId();
		var sgResponse = ec2Client.describeSecurityGroups(b -> b.groupIds(groupId));
		var permissions = sgResponse.securityGroups().getFirst().ipPermissions();

		boolean hasHttp = permissions.stream()
				.anyMatch(p -> p.fromPort() == 80 && p.ipRanges().stream()
						.anyMatch(r -> r.cidrIp().equals("0.0.0.0/0")));

		boolean hasSsh = permissions.stream()
				.anyMatch(p -> p.fromPort() == 22 );

		assertAll("Firewall Rules",
				() -> assertThat(hasHttp)
						.as("Security Group should allow HTTP (port 80) from anywhere")
						.isTrue(),
				() -> assertThat(hasSsh)
						.as("Security Group should allow SSH (port 22)")
						.isTrue()
		);
	}

	@Test
	@DisplayName("CXQA-S3-01: Verify that instance has IAM Role for S3 access")
	void testIamRoleAttachment() {
		assertThat(appInstance.iamInstanceProfile())
				.as("Instance must have an IAM Instance Profile attached")
				.isNotNull();

		assertThat(appInstance.iamInstanceProfile().arn())
				.as("IAM Profile ARN should contain the project name")
				.contains("cloudximage");
	}
}
