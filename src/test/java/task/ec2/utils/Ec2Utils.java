package task.ec2.utils;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;

public class Ec2Utils {
	public static String getPublicIp() {
			try (Ec2Client client = Ec2Client.builder()
					.region(Region.EU_CENTRAL_1)
					.credentialsProvider(DefaultCredentialsProvider.create())
					.build()) {

				return client.describeInstances().reservations().stream()
						.flatMap(r -> r.instances().stream())
						// Filter to find the running instance with a public IP
						.filter(i -> i.publicIpAddress() != null && "running".equals(i.state().name().toString()))
						.findFirst()
						.map(i -> "http://" + i.publicIpAddress())
						.orElseThrow(() -> new RuntimeException("Public instance not found!"));
			} catch (Ec2Exception e) {
				// Throw a clear exception if AWS communication fails
				throw new RuntimeException("Error occurred while communicating with AWS: " + e.getMessage());
			}
		}
	}

