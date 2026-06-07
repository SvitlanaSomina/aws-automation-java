# Bug Report: Missing Application Log Streams in CloudWatch Log Group

- **Severity:** Major
- **Environment:** CloudXServerless Infrastructure (Version 1 and Version 3)

## 1. Summary

The application successfully handles image uploads, but execution and routing logs are never delivered to AWS CloudWatch.
The designated log group `/var/log/cloudxserverless-app` is successfully created during deployment, but it remains completely empty containing no log streams. This blocks automated checks for endpoint history and compromises production visibility.

## 2. Steps to Reproduce

1. Deploy the infrastructure (Version 1 or Version 3).
2. Generate API traffic by uploading an image: send a multipart `POST` request to `/image` with key `"upfile"`.
3. Verify that the application responds with `200 OK` and a valid image ID.
4. Run the automated deployment validation suite `MonitoringDeploymentValidationTest.testCloudWatchLogGroupsAndStreams`.
5. Check AWS CloudWatch for log streams inside `/var/log/cloudxserverless-app`.

## 3. Expected vs Actual Result

| Feature | Expected Result | Actual Result |
|---|---|---|
| Log Stream Presence | Active log streams partitioned by EC2 Instance ID or Hostname | No log streams found (`The log group is empty`) |
| API Request History | Log messages containing HTTP method and path (e.g., `POST /image`) | Complete absence of logs, empty lookup results |

## 4. Root Cause Analysis

Since the application returns `200 OK`, the web server backend is functioning properly and executing logic. Additionally, `cloud-init` logs from the same instance are successfully streamed to `us-east-1`, confirming that the instance profile possesses correct IAM permissions to interact with CloudWatch.

The defect lies in the **CloudWatch Agent configuration on the EC2 instance (`amazon-cloudwatch-agent.json`)**. The agent is either missing the monitoring block for the application's actual standard output path, or the file path tracked in the configuration does not exist in the V1/V3 application bundle.

## 5. Actual Test Trace

```text
=== AVAILABLE STREAMS IN /var/log/cloudxserverless-app ===
NO STREAMS FOUND AT ALL! The log group is empty.
=========================================================

org.opentest4j.AssertionFailedError: [CXQA-MON-01 & MON-03: Application logs must have an active stream for current Instance ID] 
Expecting value to be true but was false
Expected :true
Actual   :false

org.opentest4j.AssertionFailedError: [CXQA-MON-07: Log group /var/log/cloudxserverless-app must log handled HTTP API requests] 
Expecting value to be true but was false
Expected :true
Actual   :false