package task.vpc.tests;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.Route;
import software.amazon.awssdk.services.ec2.model.RouteTable;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.hamcrest.Matchers.containsString;


public class VpcDeploymentTest extends BaseVpcTest {

	@Test
	@DisplayName("CXQA-APP-01: Verify that cloudxinfo Swagger UI should be accessible on public instance")
	void testSwaggerUiAccessible() {
		Vpc vpc = getVpcByTag();
		List<Instance> publicInstances = getRunningInstances(vpc.vpcId()).stream()
				.filter(i -> i.publicIpAddress() != null)
				.toList();

		assertThat(publicInstances).as("Should have at least one public instance").isNotEmpty();

		for (Instance pub : publicInstances) {
			String baseUrl = "http://" + pub.publicIpAddress();

			// Verify Swagger UI is accessible
			given()
					.baseUri(baseUrl)
					.when()
					.get("/ui")
					.then()
					.statusCode(200)
					.and()
					.assertThat().statusLine(containsString("200"));

			// Verify API returns application/json content type
			given()
					.baseUri(baseUrl)
					.when()
					.get("/")
					.then()
					.statusCode(200)
					.contentType(ContentType.JSON);
		}
	}

	@Test
	@DisplayName("CXQA-VPC-01: Verify VPC CIDR, tag cloudx:qa, and subnet layout (1 public, 1 private)")
	void testVpcConfiguration() {
		Vpc vpc = getVpcByTag();

		// Verify VPC CIDR block
		assertThat(vpc.cidrBlock())
				.as("VPC CIDR block should be 10.0.0.0/16")
				.isEqualTo("10.0.0.0/16");

		// Verify VPC tags
		assertThat(vpc.tags())
				.as("VPC should have tag 'cloudx:qa'")
				.extracting(Tag::key, Tag::value)
				.contains(tuple("cloudx", "qa"));

		// Verify that there are exactly 2 subnets in the VPC
		List<Subnet> subnets = ec2Client.describeSubnets(r -> r.filters(Filter.builder()
						.name("vpc-id")
						.values(vpc.vpcId()).build())).subnets();
		assertThat(subnets)
				.as("VPC should have exactly 2 subnets")
				.hasSize(2);

		// Verify that one subnet is public and the other is private
		long publicSubnetCount = subnets.stream()
				.filter(Subnet::mapPublicIpOnLaunch)
				.count();
		long privateSubnetCount = subnets.stream()
				.filter(s -> !s.mapPublicIpOnLaunch())
				.count();

		assertThat(publicSubnetCount)
				.as("There should be exactly 1 public subnet")
				.isEqualTo(1);
		assertThat(privateSubnetCount)
				.as("There should be exactly 1 private subnet")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("CXQA-VPC-02: Verify subnets, routing, and instance connectivity configuration")
	void testSubnetsAndRouting() {
		Vpc vpc = getVpcByTag();
		String vpcId = vpc.vpcId();

		// Fetch subnets and instances
		List<Subnet> subnets = ec2Client.describeSubnets(r -> r.filters(
				Filter.builder().name("vpc-id").values(vpcId).build())).subnets();

		List<Instance> instances = ec2Client.describeInstances(r -> r.filters(
						Filter.builder().name("vpc-id").values(vpcId).build())).reservations().stream()
				.flatMap(res -> res.instances().stream())
				.filter(i -> !i.state().name().equals(InstanceStateName.TERMINATED))
				.toList();

		List<Instance> publicInstances = instances.stream().filter(i -> i.publicIpAddress() != null).toList();
		List<Instance> privateInstances = instances.stream().filter(i -> i.publicIpAddress() == null).toList();

		assertThat(publicInstances).as("Should have exactly 1 public instance").hasSize(1);
		assertThat(privateInstances).as("Should have exactly 1 private instance").hasSize(1);

		// Verify that public instance should be accessible from internet and private instance should not be accessible from internet
		assertThat(publicInstances).as("Requirement 1: Should have at least one public instance").isNotEmpty();

		for (Instance pub : publicInstances) {
			boolean allowsHttp = ec2Client.describeSecurityGroups(r -> r.groupIds(pub.securityGroups().getFirst().groupId()))
					.securityGroups().getFirst().ipPermissions().stream()
					.anyMatch(p -> p.ipRanges().stream().anyMatch(range -> range.cidrIp().equals("0.0.0.0/0"))
							&& p.fromPort() != null && p.fromPort() <= 80
							&& p.toPort() != null && p.toPort() >= 80);

			assertThat(allowsHttp)
					.as("Public instance %s SG must allow inbound HTTP port 80 from 0.0.0.0/0", pub.instanceId())
					.isTrue();
		}

		for (Instance priv : privateInstances) {
			assertThat(priv.publicIpAddress()).as("Private instance must not have public IP").isNull();

			boolean allowsInternet = ec2Client.describeSecurityGroups(r -> r.groupIds(priv.securityGroups().getFirst().groupId()))
					.securityGroups().getFirst().ipPermissions().stream()
					.anyMatch(p -> p.ipRanges().stream().anyMatch(range -> range.cidrIp().equals("0.0.0.0/0")));

			assertThat(allowsInternet)
					.as("Private instance %s must NOT allow inbound 0.0.0.0/0", priv.instanceId()).isFalse();
		}

		// Verify that both public and private instances should have access to the public internet
		for (Subnet subnet : subnets) {
			RouteTable rt = ec2Client.describeRouteTables(r -> r.filters(
							Filter.builder().name("association.subnet-id").values(subnet.subnetId()).build()))
					.routeTables().getFirst();

			List<Route> routes = rt.routes();
			if (subnet.mapPublicIpOnLaunch()) {
				boolean hasIgw = routes.stream().anyMatch(r -> r.gatewayId() != null && r.gatewayId().startsWith("igw-"));
				assertThat(hasIgw).as("Public subnet must have IGW route").isTrue();
			} else {
				boolean hasNat = routes.stream().anyMatch(r -> r.natGatewayId() != null);
				assertThat(hasNat).as("Private subnet must have NAT route").isTrue();
			}
		}

		// Verify SG egress allows outbound internet traffic for all instances
		for (Instance instance : instances) {
			boolean hasEgress = ec2Client.describeSecurityGroups(
							r -> r.groupIds(instance.securityGroups().getFirst().groupId()))
					.securityGroups().getFirst()
					.ipPermissionsEgress().stream()
					.anyMatch(p -> p.ipRanges().stream()
							.anyMatch(range -> range.cidrIp().equals("0.0.0.0/0")));

			assertThat(hasEgress)
					.as("Instance %s SG must allow outbound traffic to internet", instance.instanceId())
					.isTrue();
		}

		// Verify that public instance should have access to the private instance
		String publicSgId = publicInstances.getFirst().securityGroups().getFirst().groupId();
		for (Instance priv : privateInstances) {
			boolean allowsPublicSg = ec2Client.describeSecurityGroups(r -> r.groupIds(priv.securityGroups().getFirst().groupId()))
					.securityGroups().getFirst().ipPermissions().stream()
					.anyMatch(p -> p.userIdGroupPairs().stream().anyMatch(pair -> pair.groupId().equals(publicSgId)));

			assertThat(allowsPublicSg)
					.as("Private instance SG must allow inbound traffic from Public SG %s", publicSgId).isTrue();
		}
	}

	private Vpc getVpcByTag() {
		return ec2Client.describeVpcs(r -> r.filters(Filter.builder().name("tag:cloudx").values("qa").build()))
				.vpcs()
				.stream()
				.findFirst()
				.orElseThrow(() -> new AssertionError("VPC with tag 'cloudx:qa' not found. Ensure that the stack is deployed correctly."));
	}

	protected List<Instance> getRunningInstances(String vpcId) {
		return ec2Client.describeInstances(r -> r.filters(
						Filter.builder().name("vpc-id").values(vpcId).build()))
				.reservations().stream()
				.flatMap(res -> res.instances().stream())
				.filter(i -> i.state().name().equals(InstanceStateName.RUNNING))
				.toList();
	}
}
