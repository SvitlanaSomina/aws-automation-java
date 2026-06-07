# Bug Report: Application Startup Crash (Connection refused) due to TypeError
**Severity:** Critical
**Environment:** CloudXImage Version 4

**1. Summary**
In the fourth version of the application (V4), the backend service completely fails to start on the EC2 instance. All API functional tests fail with a `Connection refused` error because port 80 is not open. The failure is caused by an invalid `debug=True` parameter passed during the application's initialization, which immediately crashes the process.

**2. Steps to Reproduce**
1. Deploy application version 4.
2. Run the automated functional test suite (`ApplicationFunctionalTest`).
3. Observe test failures with connection errors.
4. Connect to the EC2 AppInstance via SSH.
5. Inspect the initialization logs by executing: `cat /var/log/cloud-init-output.log`

**3. Expected vs Actual Result**

| Feature | Expected Result | Actual Result |
| :--- | :--- | :--- |
| **Application Process** | Application runs successfully and listens on port 80 | Application crashes immediately on startup |
| **Functional Tests** | API requests return valid HTTP status codes (200 OK) | API requests fail with `java.net.ConnectException: Connection refused` |

**Root Cause Analysis:** The regression was introduced in the V4 application source code update. In the `/root/image/main.py` file (line 57), the `app.run()` method is being called with a `debug=True` argument. The underlying ASGI server (`uvicorn` used by `connexion`) does not support the `debug` keyword argument. This incompatibility triggers a fatal `TypeError` during the `cloud-init` deployment phase, killing the application instantly and leaving the server unreachable.

**Actual Test Trace:**
```text
java.net.ConnectException: Connection refused
    at java.base/sun.nio.ch.Net.connect0(Native Method)
    at java.base/sun.nio.ch.Net.connect(Net.java:589)
    ...
```
**Application Log Trace (/var/log/cloud-init-output.log):**
```python
Traceback (most recent call last):
  File "<frozen runpy>", line 198, in _run_module_as_main
  File "<frozen runpy>", line 88, in _run_code
  File "/root/image/main.py", line 57, in <module>
    app.run(host='0.0.0.0', port=80, debug=True)
  ...
TypeError: run() got an unexpected keyword argument 'debug'
```