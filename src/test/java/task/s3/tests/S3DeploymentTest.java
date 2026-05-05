package task.s3.tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketTaggingResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockResponse;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class S3DeploymentTest extends BaseS3Test {
	private static String bucketName;

	@BeforeAll
	static void findTargetBucket() {
		bucketName = s3Client.listBuckets().buckets().stream()
				.map(Bucket::name)
				.filter(name -> name.contains("imagestorebucket"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("CRITICAL: No bucket with 'imagestorebucket' found in AWS account"));
	}

	@Test
	@DisplayName("CXQA-S3-02: Verify bucket name pattern")
	void testBucketNamePattern() {
		String prefix = "cloudximage-imagestorebucket";

		assertThat(bucketName)
				.as("Bucket name should start with '%s'", prefix)
				.startsWith(prefix)
				.hasSizeGreaterThan(prefix.length());
	}

	@Test
	@DisplayName("CXQA-S3-02: Verify bucket tags")
	void testBucketTags() {
		GetBucketTaggingResponse taggingResponse = s3Client.getBucketTagging(b -> b.bucket(bucketName));

		assertThat(taggingResponse.tagSet())
				.as("Bucket should have the mandatory tag 'cloudx: qa'")
				.anySatisfy(tag -> {
					assertThat(tag.key()).isEqualTo("cloudx");
					assertThat(tag.value()).isEqualTo("qa");
				});
	}

	@Test
	@DisplayName("CXQA-S3-02: Verify encryption type is SSE-S3")
	void testBucketEncryption() {
		GetBucketEncryptionResponse encryptionResponse = s3Client.getBucketEncryption(b -> b.bucket(bucketName));
		String algorithm = encryptionResponse.serverSideEncryptionConfiguration()
				.rules().getFirst()
				.applyServerSideEncryptionByDefault()
				.sseAlgorithmAsString();

		assertThat(algorithm)
				.as("Encryption algorithm should be SSE-S3 (AES256)")
				.isEqualTo("AES256");
	}

	@Test
	@DisplayName("CXQA-S3-02: Verify versioning is disabled")
	void testBucketVersioning() {
		GetBucketVersioningResponse versioningResponse = s3Client.getBucketVersioning(b -> b.bucket(bucketName));
		String status = versioningResponse.statusAsString();

		assertThat(status)
				.as("Versioning should be disabled (null or Suspended)")
				.satisfiesAnyOf(
						s -> assertThat(s).isNull(),
						s -> assertThat(s).isEqualTo("Suspended"),
						s -> assertThat(s).isEmpty()
				);
	}

	@Test
	@DisplayName("CXQA-S3-02: Verify public access is blocked")
	void testPublicAccessSettings() {
		GetPublicAccessBlockResponse publicAccessResponse = s3Client.getPublicAccessBlock(b -> b.bucket(bucketName));
		PublicAccessBlockConfiguration config = publicAccessResponse.publicAccessBlockConfiguration();

		assertAll("Public Access Block Configuration",
				() -> assertThat(config.blockPublicAcls()).isTrue(),
				() -> assertThat(config.ignorePublicAcls()).isTrue(),
				() -> assertThat(config.blockPublicPolicy()).isTrue(),
				() -> assertThat(config.restrictPublicBuckets()).isTrue()
		);
	}
}
