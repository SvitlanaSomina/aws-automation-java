# Bug Report: Image Metadata Is Not Deleted from RDS Database Upon DELETE Request

- **Severity:** Critical
- **Environment:** CloudX Infrastructure Version 3

## 1. Summary

A critical regression in Version 3 causes deleted images to remain accessible via the GET API.  
After executing a successful DELETE request, the image metadata is not removed from the MySQL RDS database, violating data persistence and isolation requirements (`CXQA-RDS-05`).

## 2. Steps to Reproduce

1. Deploy infrastructure version 3: `invoke deploy.cloudximage --version=3`
2. Upload an image to get an active ID (e.g., ID `48`)
3. Send a DELETE request to `http://{INSTANCE_PUBLIC_IP}/api/image/48`
4. Send a GET request to `http://{INSTANCE_PUBLIC_IP}/api/image/48` to verify deletion

## 3. Expected vs Actual Result

| Action / Feature | Expected Result | Actual Result |
|---|---|---|
| `DELETE /api/image/48` Status | `200 OK` or `204 No Content` | `200 OK` |
| Subsequent `GET /api/image/48` Status | `404 Not Found` | `200 OK` |
| Metadata Data Retrieval | `null` / error message | Full JSON metadata returned |

## 4. Root Cause Analysis

The regression was introduced in the V3 application logic.

Possible causes:
- The DELETE endpoint handler behaves like a mock (returns `200` without executing SQL `DELETE` in MySQL RDS)
- A soft-delete mechanism was introduced incorrectly, and the GET endpoint does not filter records marked as deleted

## 5. Actual Test Trace

```text
java.lang.AssertionError: 1 expectation failed.
Expected status code <404> but was <200>.

Response Body:
{
  "id": 48,
  "last_modified": "2026-05-17T14:01:43Z",
  "object_key": "images/6083b45e-ba13-48ec-a981-6e37a6c4f382-test.jpeg",
  "object_size": 27844,
  "object_type": "binary/octet-stream"
}