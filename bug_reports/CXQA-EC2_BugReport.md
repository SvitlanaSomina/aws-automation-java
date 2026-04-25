# Bug Report: Regression in Metadata API Response Format (Public & Private)

**Severity:** Critical  
**Environment:** CloudX Infrastructure Version 2

## 1. Summary
The Metadata API endpoint (`/`) on **both Public and Private instances** incorrectly returns a plain text string (`regionavailability_zone`) with `Content-Type: text/plain` instead of returning a valid `application/json` object with the expected metadata fields. This causes functional validation tests to fail on all deployed instances.

## 2. Steps to Reproduce
1. Deploy infrastructure version 2: `invoke deploy.cloudxinfo --version=2`
2. Reboot the EC2 instances.
3. Execute the automated functional tests:
    - `ApiFunctionalTest.testPublicInstanceMetadata` (Public Instance)
    - `ApiFunctionalTest.testPrivateInstanceMetadata` (Private Instance)
4. Alternatively, perform a manual request to either instance: `curl -v http://<INSTANCE_IP>/`

## 3. Expected vs Actual Result

| Feature | Expected Result | Actual Result |
| :--- | :--- | :--- |
| **Response Format** | `application/json` | `text/plain` |
| **Response Body** | Valid JSON object with fields: `region`, `availability_zone`, `private_ipv4` | Concatenated string: `regionavailability_zone` |

### Actual output received (from both Public and Private instances):
```text
regionavailability_zone