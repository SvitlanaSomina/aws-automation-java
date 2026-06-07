package task.monitoring.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudtrail.model.DescribeTrailsResponse;
import software.amazon.awssdk.services.cloudtrail.model.GetTrailStatusResponse;
import software.amazon.awssdk.services.cloudtrail.model.Trail;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class MonitoringDeploymentValidationTest extends BaseMonitoringTest {

	@Test
	@DisplayName("CXQA-MON-01, 02, 03, 04: Verify CloudWatch Integration, Log Groups, and Instance Streams")
	void testCloudWatchLogGroupsAndStreams() {
		// Create a dedicated CloudWatch client for us-east-1 specifically for cloud-init logs
		var logsClientUsEast1 = software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient.builder()
				.region(software.amazon.awssdk.regions.Region.US_EAST_1)
				.credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create())
				.build();

		// 1. Fetch logs from us-east-1 for the cloud-init requirement (CXQA-MON-02)
		var logGroupsResponseUsEast1 = logsClientUsEast1.describeLogGroups();
		boolean hasCloudInitGroup = logGroupsResponseUsEast1.logGroups().stream()
				.anyMatch(g -> g.logGroupName().equals("/var/log/cloud-init"));

		// 2. Fetch logs from the current region (from Base class) for application and Lambda functions
		DescribeLogGroupsResponse logGroupsResponseCurrent = logsClient.describeLogGroups();
		var logGroupsCurrent = logGroupsResponseCurrent.logGroups();

		boolean hasAppGroup = logGroupsCurrent.stream()
				.anyMatch(g -> g.logGroupName().equals("/var/log/cloudxserverless-app"));

		String expectedLambdaLogGroup = "/aws/lambda/" + lambdaUniqueName;
		boolean hasLambdaGroup = logGroupsCurrent.stream()
				.anyMatch(g -> g.logGroupName().equals(expectedLambdaLogGroup));

		// 3. Verify LogStreams by active Instance ID or AWS Hostname format in their respective regions
		final boolean finalCloudInitStream = hasCloudInitGroup && logsClientUsEast1
				.describeLogStreams(b -> b.logGroupName("/var/log/cloud-init"))
				.logStreams().stream()
				.anyMatch(s -> s.logStreamName().contains(instanceId) || s.logStreamName().contains("ip-"));

		// Temporary debugging to see actual stream names in the console for investigation
		if (hasAppGroup) {
			var streams = logsClient.describeLogStreams(b -> b.logGroupName("/var/log/cloudxserverless-app")).logStreams();
			System.out.println("=== AVAILABLE STREAMS IN /var/log/cloudxserverless-app ===");
			if (streams.isEmpty()) {
				System.out.println("NO STREAMS FOUND AT ALL! The log group is empty.");
			} else {
				streams.forEach(s -> System.out.println("Stream Name: " + s.logStreamName()));
			}
			System.out.println("=========================================================");
		}

		final boolean finalAppStream = hasAppGroup && logsClient
				.describeLogStreams(b -> b.logGroupName("/var/log/cloudxserverless-app"))
				.logStreams().stream()
				.anyMatch(s -> s.logStreamName().contains(instanceId) || s.logStreamName().contains("ip-"));

		// Close the temporary us-east-1 client to prevent resource leaks
		logsClientUsEast1.close();

		// Comprehensive assertion block mapping all infrastructure requirements
		assertAll("CloudWatch Deployment Validation",
				() -> assertThat(hasCloudInitGroup)
						.as("CXQA-MON-02: LogGroup /var/log/cloud-init must exist in us-east-1 region")
						.isTrue(),
				() -> assertThat(hasAppGroup)
						.as("CXQA-MON-03: LogGroup /var/log/cloudxserverless-app must exist in current stack region")
						.isTrue(),
				() -> assertThat(hasLambdaGroup)
						.as("CXQA-MON-04: Event handler Lambda LogGroup [" + expectedLambdaLogGroup + "] must exist")
						.isTrue(),
				() -> assertThat(finalCloudInitStream)
						.as("CXQA-MON-01 & MON-02: CloudInit logs must have an active stream for current Instance ID in us-east-1")
						.isTrue(),
				() -> assertThat(finalAppStream)
						.as("CXQA-MON-01 & MON-03: Application logs must have an active stream for current Instance ID")
						.isTrue()
		);
	}

	@Test
	@DisplayName("CXQA-MON-05: Verify CloudTrail Configuration and Security Requirements")
	void testCloudTrailConfiguration() {
		DescribeTrailsResponse trailsResponse = trailClient.describeTrails();
		Trail appTrail = trailsResponse.trailList().stream()
				.filter(t -> t.name().contains("cloudxserverless-Trail"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("CloudTrail with name pattern 'cloudxserverless-Trail' not found"));

		GetTrailStatusResponse trailStatus = trailClient.getTrailStatus(b -> b.name(appTrail.trailARN()));

		var listTagsResponse = trailClient.listTags(b -> b.resourceIdList(appTrail.trailARN()));

		boolean hasRequiredTag = listTagsResponse.resourceTagList().stream()
				.flatMap(r -> r.tagsList().stream())
				.anyMatch(tag -> tag.key().equals("cloudx") && tag.value().equals("qa"));

		assertAll("CloudTrail Requirements Validation",
				() -> assertThat(trailStatus.isLogging())
						.as("CloudTrail must be enabled and actively collecting logs")
						.isTrue(),
				() -> assertThat(appTrail.isMultiRegionTrail())
						.as("CXQA-MON-05: Trail should be single-region (Multi-region: false)")
						.isFalse(),
				() -> assertThat(appTrail.logFileValidationEnabled())
						.as("CXQA-MON-05: Log file validation must be enabled")
						.isTrue(),
				() -> assertThat(appTrail.kmsKeyId())
						.as("CXQA-MON-05: SSE-KMS encryption should not be enabled (uses default S3 encryption)")
						.isNull(),
				() -> assertThat(hasRequiredTag)
						.as("CXQA-MON-05: CloudTrail must be tagged with cloudx: qa")
						.isTrue()
		);
	}
}
