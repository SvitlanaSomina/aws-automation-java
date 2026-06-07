# Bug Report: Event Handler Lambda Fails to Log Image Processing Events and Metadata

- **Severity:** Critical
- **Environment:** CloudXServerless Infrastructure (Version 1 and Version 3)

## 1. Summary

After an image is successfully uploaded to the backend, the event-driven notification loop fails to log processing records.
The Event Handler Lambda log group (`/aws/lambda/cloudxserverless-EventHandlerLambda{unique_id}`) contains no text event messages regarding the processed images. As a result, all required fields (object key, type, size, modification date, download link) evaluate to empty strings, breaking analytical traceability.

## 2. Steps to Reproduce

1. Deploy the infrastructure (Version 1 or Version 3).
2. Upload a valid image file using a multipart `POST` request to `/image`.
3. Wait up to 35 seconds to account for CloudWatch eventual consistency.
4. Run automated test `ServerlessAppLoggingTest.testApplicationAndLambdaLogging`.
5. Fetch and inspect text events from the CloudWatch Lambda log group.

## 3. Expected vs Actual Result

| Feature | Expected Result | Actual Result |
|---|---|---|
| Log Content Ingestion | Text message stream detailing processed notification event | Empty string block `""` returned from CloudWatch |
| Image Metadata Payload | Logs containing `object_key`, `jpeg` type, `size`, `date`, and `download_link` | None of the predicates or metadata patterns are found |

## 4. Root Cause Analysis

The application successfully outputs the image ID, meaning the file enters the system. However, the event pipeline between S3 and the Lambda function is broken.

Possible structural causes:
- **S3 Event Notifications Defect:** The S3 bucket deployment script in IaC/CDK is missing the trigger mapping, meaning notifications are never emitted to trigger the Lambda execution.
- **Lambda Code Regression:** The handler inside the Lambda function does not log the processing metadata to standard output (`System.out.println` or logger framework is missing/commented out).
- **IAM Permission Policy:** The Lambda Execution Role might lack `logs:PutLogEvents` permission specifically for pushing text payloads, allowing stream allocation but blocking message indexing.

## 5. Actual Test Trace

```text
java.lang.AssertionError: [CXQA-MON-06: Lambda log group must contain logs for processed notification event with ID: 34d343ca-dcda-4508-b33a-7b61fb66ff44] 
Expecting actual not to be empty

java.lang.AssertionError: [CXQA-MON-06: Lambda logs must include object key / image ID information] 
Expecting actual:
  ""
to contain:
  "34d343ca-dcda-4508-b33a-7b61fb66ff44" 

java.lang.AssertionError: [CXQA-MON-06: Lambda logs must include object type (image/jpeg)] 
Expecting actual:
  ""
to contain:
  "jpeg"