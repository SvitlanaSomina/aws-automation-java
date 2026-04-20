# Bug Report: Incorrect permissions in FullAccessPolicyS3

**Severity:** Critical  
**Environment:** CloudX Infrastructure Version 2

### 1. Summary
IAM Policy `FullAccessPolicyS3` incorrectly restricts permissions to **Read-Only** (`s3:Get*`) instead of providing **Full Access** (`s3:*`).

### 2. Steps to Reproduce
1. Deploy infrastructure version 2:  
   `invoke deploy.cloudxiam --version=2`
2. Run the automated test:  
   `IamPolicyTest.testFullAccessPolicyS3Content`

### 3. Expected vs Actual Result

| Feature | Expected Result | Actual Result |
| :--- | :--- | :--- |
| **S3 Action** | `"Action": "s3:*"` | `"Action": "s3:Get*"` |

**Actual JSON snippet:**
```json
{
  "Effect": "Allow",
  "Action": "s3:Get*",
  "Resource": "*"
}