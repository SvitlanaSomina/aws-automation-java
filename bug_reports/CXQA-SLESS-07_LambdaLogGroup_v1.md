# Bug Report: Incorrect CloudWatch Log Group Name for Lambda Function

**Severity:** Medium (Major for infrastructure observability)  
**Environment:** CloudXServerless Version 1

### 1. Summary
`CXQA-SLESS-07` requirement fails because the deployed AWS Lambda function uses the default CloudWatch log group naming convention instead of the required custom log group name.

### 2. Steps to Reproduce
1. Deploy the `cloudxserverless` infrastructure according to deployment instructions.
2. Run automated test:  
   `ServerlessDeploymentValidationTest.testLambdaConfiguration`
3. (Optional manual check) Navigate to AWS Console -> CloudWatch -> Log groups and search for the deployed Lambda function's logs.

### 3. Expected vs Actual Result

| Feature | Expected Result | Actual Result |
| :--- | :--- | :--- |
| **Lambda CloudWatch Log Group** | `aws/lambda/cloudx-associate-aws-for-testers-v3` | `/aws/lambda/cloudxserverless-EventHandlerLambda...` (default dynamic name) |

**Actual error from automated test:**
```text
java.lang.AssertionError: [Lambda application logs must be stored in specific CloudWatch log group] 
Expecting actual:
  "/aws/lambda/cloudxserverless-EventHandlerLambdaECEF2D13-DFPbMLtSB1eu"
to contain:
  "aws/lambda/cloudx-associate-aws-for-testers-v3"