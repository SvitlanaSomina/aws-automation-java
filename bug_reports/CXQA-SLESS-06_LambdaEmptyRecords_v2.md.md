# Bug Report: Image Notification Email Not Sent Due to Empty Record Processing in Lambda

**Severity:** High  
**Environment:** CloudXServerless Version 2

### 1. Summary
`CXQA-SLESS-06` requirement fails because the `EventHandlerLambda` function fails to correctly parse records from the SQS event object. The function processes an empty array (`records=[]`) and terminates successfully without publishing a message to the SNS topic, preventing the delivery of the image upload notification email.

### 2. Steps to Reproduce
1. Deploy the `cloudxserverless` Version 2 infrastructure stack.
2. Run automated test:  
   `ServerlessNotificationFunctionalTest.testImageEventNotificationAndDownload`
3. Check AWS CloudWatch Logs for the `EventHandlerLambda` function execution stream.

### 3. Expected vs Actual Result

| Feature | Expected Result | Actual Result |
| :--- | :--- | :--- |
| **Notification Delivery** | Mailosaur mailbox receives an email notification with image metadata. | No email is received (Timeout); Lambda logs show `HANDLER: records=[]`. |

**Actual error from automated test:**
```text
java.lang.RuntimeException: Email to 354d6567@edvcjgzp.mailosaur.net not found or timeout reached
	at task.serverless.tests.MailosaurHelper.waitForEmail(MailosaurHelper.java:34)
	at task.serverless.tests.ServerlessNotificationFunctionalTest.testImageEventNotificationAndDownload(ServerlessNotificationFunctionalTest.java:91)
Caused by: com.mailosaur.MailosaurException: No matching messages found in time. By default, only messages received in the last hour are checked (use receivedAfter to override this). The search criteria used for this query was [{"sentTo":"354d6567@edvcjgzp.mailosaur.net"}] which timed out after 10000ms
	at com.mailosaur.Messages.search(Messages.java:344)
	at com.mailosaur.Messages.get(Messages.java:56)