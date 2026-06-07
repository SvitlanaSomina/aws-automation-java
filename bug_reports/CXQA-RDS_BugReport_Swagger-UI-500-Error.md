# Bug Report: Swagger UI returns 500 Internal Server Error

- **Severity:** Major
- **Environment:** CloudX Infrastructure Version 3

## 1. Summary

After deploying Version 3 of the infrastructure, the API documentation endpoint (`/api/ui`) becomes unavailable.  
Instead of rendering the Swagger UI page, the server returns **HTTP 500 Internal Server Error**, preventing manual validation via OpenAPI documentation.

## 2. Steps to Reproduce

1. Deploy infrastructure version 3: `invoke deploy.cloudximage --version=3`
2. Run automated test `RdsFunctionalTest.testSwaggerUiAvailability`  
   (or send a GET request to `http://{INSTANCE_PUBLIC_IP}/api/ui`)

## 3. Expected vs Actual Result

| Feature | Expected Result | Actual Result |
|---|---|---|
| HTTP Status Code | `200 OK` | `500 Internal Server Error` |
| Content-Type | `text/html` (or containing `"html"`) | `application/problem+json` |

## 4. Root Cause Analysis

The application server (`uvicorn`) is up and responding, but routing or asset handling for the `/api/ui` path is broken in the V3 deployment package.

Possible causes:
- Missing Swagger static assets (HTML/JS/CSS) in the bundle
- Runtime crash during OpenAPI schema generation

## 5. Actual Test Trace

```http
HTTP/1.1 500 Internal Server Error
date: Sun, 17 May 2026 14:01:40 GMT
server: uvicorn
content-type: application/problem+json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "detail": "The server encountered an internal error and was unable to complete your request. Either the server is overloaded or there is an error in the application.",
  "status": 500
}