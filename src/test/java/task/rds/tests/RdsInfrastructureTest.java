package task.rds.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


public class RdsInfrastructureTest extends BaseRdsTest {

	@Test
	@DisplayName("CXQA-RDS-01: Verify RDS Security Group Isolation")
	void testRdsSecurityGroupIsolation() {
		// Fetch RDS instance details
		var dbInstance = rdsClient.describeDBInstances().dbInstances().getFirst();
		String rdsSgId = dbInstance.vpcSecurityGroups().getFirst().vpcSecurityGroupId();

		// Verify the RDS instance does not have a public DNS endpoint reachable from the internet
		assertThat(dbInstance.publiclyAccessible())
				.as("RDS instance must have 'Public accessibility' set to No")
				.isFalse();

		// Ensure that the subnets associated with RDS do not have a route to an Internet Gateway (IGW)
		dbInstance.dbSubnetGroup().subnets().forEach(subnet -> {
			var routeTables = ec2Client.describeRouteTables(b -> b.filters(
					f -> f.name("association.subnet-id").values(subnet.subnetIdentifier()))).routeTables();

			boolean hasIgw = routeTables.stream()
					.flatMap(rt -> rt.routes().stream())
					.anyMatch(route -> route.gatewayId() != null && route.gatewayId().startsWith("igw-"));

			assertThat(hasIgw)
					.as("Subnet %s is public (IGW found), but must be private", subnet.subnetIdentifier())
					.isFalse();
		});

		var sgDetails = ec2Client.describeSecurityGroups(b -> b.groupIds(rdsSgId)).securityGroups().getFirst();
		var permissions = sgDetails.ipPermissions();

		// Ensure the Security Group is not empty and strictly controlled
		assertThat(permissions).as("RDS Security Group must have defined ingress rules").isNotEmpty();

		for (var permission : permissions) {
			// Validate that only MySQL protocol (port 3306) is allowed
			assertThat(permission.fromPort())
					.as("Only MySQL port (3306) should be opened")
					.isEqualTo(3306);

			// Ensure no direct IP-based access (CIDR) is allowed, fulfilling the "via Security Group" requirement
			assertThat(permission.ipRanges())
					.as("Security Group must not allow access via CIDR blocks (IP ranges)")
					.isEmpty();

			// Strict check: verify that ONLY the specific Application Security Group is granted access
			boolean onlyAppSg = permission.userIdGroupPairs().stream()
					.allMatch(pair -> pair.groupId().equals(appInstanceSgId));

			assertThat(onlyAppSg)
					.as("Access must be restricted exclusively to the Application Security Group ID")
					.isTrue();
		}
	}

		@Test
		@DisplayName("CXQA-RDS-02: Verify RDS Instance Specs")
		void testRdsSpecs() {
		var db = rdsClient.describeDBInstances().dbInstances().getFirst();

		assertAll(
				() -> assertThat(db.engine()).isEqualTo("mysql"),
				() -> assertThat(db.engineVersion()).isEqualTo("8.4.5"),
				() -> assertThat(db.dbInstanceClass()).isEqualTo("db.t3.micro"),
				() -> assertThat(db.allocatedStorage()).isEqualTo(100),
				() -> assertThat(db.storageType()).isEqualTo("gp2"),
				() -> assertThat(db.multiAZ()).isFalse(),
				() -> assertThat(db.storageEncrypted()).isFalse(),
				() -> {
					var tags = rdsClient.listTagsForResource(b -> b.resourceName(db.dbInstanceArn())).tagList();
					boolean hasCloudxTags = tags.stream()
							.anyMatch(tag -> tag.key().equals("cloudx") && tag.value().equals("qa"));
					assertThat(hasCloudxTags).isTrue();
				}
		);
		}
}
