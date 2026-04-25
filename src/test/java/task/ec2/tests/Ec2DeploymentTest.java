package task.ec2.tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.GroupIdentifier;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Ec2DeploymentTest extends BaseEc2Test {
	private List<Instance> instances;

	@BeforeEach
	void loadInstances() {
		DescribeInstancesRequest request = DescribeInstancesRequest.builder()
				.filters(Filter.builder().name("tag:cloudx").values("qa").build())
				.build();

		this.instances = ec2Client.describeInstances(request)
				.reservations()
				.stream()
				.flatMap(reservation -> reservation.instances().stream())
				.toList();
	}

	@Test
	@DisplayName("CXQA-EC2-01: Verify that 2 application instances (public and private) are deployed")
	void testInstanceCountAndRoles() {
		assertThat(instances)
				.as("Check if exactly 2 instances are deployed")
				.hasSize(2);

		long publicInstances = instances.stream().filter(i -> i.publicIpAddress() != null).count();
		long privateInstances = instances.stream().filter(i -> i.publicIpAddress() == null).count();

		assertThat(publicInstances).as("Public instance count").isEqualTo(1);
		assertThat(privateInstances).as("Private instance count").isEqualTo(1);
	}

	@Test
	@DisplayName("CXQA-EC2-02: Verify instance configuration: t3.micro, tags, 8GB root device and OS assignment")
	void testInstanceConfiguration() {
		for (Instance instance : instances) {
			assertThat(instance.instanceType())
					.as("Instance %s type check", instance.instanceId())
					.isEqualTo(InstanceType.T3_MICRO);

			assertThat(instance.tags())
					.as("Instance %s tags check", instance.instanceId())
					.anyMatch(t -> t.key().equals("cloudx") && t.value().equals("qa"));

			String imageId = instance.imageId();
			DescribeImagesResponse imageResponse = ec2Client.describeImages(DescribeImagesRequest.builder().imageIds(imageId).build());
			String imageName = imageResponse.images().getFirst().name();
			assertThat(imageName)
					.as("Instance %s should have Amazon Linux 2023", instance.instanceId())
					.contains("al2023");

			String rootDeviceName = instance.rootDeviceName();
			String volumeId = instance.blockDeviceMappings().stream()
					.filter(bdm -> bdm.deviceName().equals(rootDeviceName))
					.findFirst().orElseThrow().ebs().volumeId();

			DescribeVolumesResponse volumesResponse = ec2Client.describeVolumes((DescribeVolumesRequest.builder().volumeIds(volumeId).build()));

			assertThat(volumesResponse.volumes().getFirst().size())
					.as("Root volume size for %s", instance.instanceId())
					.isEqualTo(8);
		}
	}

	@Test
	@DisplayName("CXQA-EC2-03: Verify Security Groups configuration")
	void testSecurityAndAccess() {
		String publicSecurityGroupId = getPublicSecurityGroupId();

		for(Instance instance : instances) {
			// Проходим по ВСЕМ привязанным группам, а не только по первой
			for (GroupIdentifier groupIdentifier : instance.securityGroups()) {
				SecurityGroup sg = ec2Client.describeSecurityGroups(
						DescribeSecurityGroupsRequest.builder()
								.groupIds(groupIdentifier.groupId())
								.build()).securityGroups().getFirst();

				// 1. Verify outbound traffic (Egress) to ensure internet access
				boolean allowsInternetEgress = sg.ipPermissionsEgress().stream()
						.anyMatch(p -> p.ipRanges().stream().anyMatch(r -> r.cidrIp().equals("0.0.0.0/0")));

				assertThat(allowsInternetEgress)
						.as("Security Group %s should allow internet access (egress)", sg.groupId())
						.isTrue();

				// 2. Verify inbound traffic (Ingress) rules
				if(instance.publicIpAddress() != null) {
					// Public instance checks
					assertThat(hasRuleWithCidr(sg, 22, "0.0.0.0/0")).isTrue();
					assertThat(hasRuleWithCidr(sg, 80, "0.0.0.0/0")).isTrue();
				} else {
					// Private instance checks
					assertThat(hasRuleForSecurityGroup(sg, 22, publicSecurityGroupId)).isTrue();
					assertThat(hasRuleForSecurityGroup(sg, 80, publicSecurityGroupId)).isTrue();
				}
			}
		}
	}

	private String getPublicSecurityGroupId() {
		return instances.stream()
				.filter(i -> i.publicIpAddress() != null)
				.findFirst()
				.orElseThrow(() -> new RuntimeException("Public instance not found"))
				.securityGroups().get(0).groupId();
	}

	private boolean hasRuleWithCidr(SecurityGroup sg, int port, String cidr) {
		return sg.ipPermissions().stream()
				.anyMatch(p -> p.fromPort() != null && p.fromPort() == port
						&& p.ipRanges().stream().anyMatch(r -> r.cidrIp().equals(cidr)));
	}

	private boolean hasRuleForSecurityGroup(SecurityGroup sg, int port, String sourceSgId) {
		return sg.ipPermissions().stream()
				.anyMatch(p -> p.fromPort() != null && p.fromPort() == port
						&& p.userIdGroupPairs().stream().anyMatch(g -> g.groupId().equals(sourceSgId)));
	}
}
