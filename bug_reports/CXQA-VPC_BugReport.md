# Bug Report: Incorrect Content-Type in cloudxinfo EC2 Info Endpoint

**Severity:** Major  
**Environment:** CloudX Infrastructure Version 3

### 1. Summary
`cloudxinfo` API EC2 info endpoint (`GET /`) returns incorrect response header `Content-Type: text/plain; charset=utf-8` instead of expected JSON content type.

### 2. Steps to Reproduce
1. Deploy infrastructure version 3 according to deployment instructions.
2. Run automated test:  
   `VpcDeploymentTest.testSwaggerUiAccessible`
3. (Optional manual check) Send request to public instance root endpoint:  
   `curl -v http://{INSTANCE_PUBLIC_IP}/`

### 3. Expected vs Actual Result

| Feature | Expected Result | Actual Result |
| :--- | :--- | :--- |
| **Content-Type header** | `application/json` | `text/plain; charset=utf-8` |

**Actual error from automated test:**
```text
java.lang.AssertionError: 1 expectation failed.
Expected content-type "JSON" doesn't match actual content-type "text/plain; charset=utf-8".