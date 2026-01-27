# Python SDK Example

This example demonstrates how to authenticate with the API Gateway using Python.

## Installation

```bash
pip install -r requirements.txt
```

## Configuration

The SDK uses environment variables with sensible defaults:

- `GATEWAY_URL` - API Gateway URL (default: `https://api-gateway.grouplinkone.com/`)
- `PUBLIC_ACCESS_KEY` - Public access key (default: `pub_test_abc123`)
- `SECRET_ACCESS_KEY` - Secret access key (default: `sec_test_xyz789`)
- `SECRET_KEY` - Secret key for HMAC signing (default: `super_secret_key_12345`)

## Usage

```python
import os
from api_gateway_client import APIGatewayClient

# Load from environment variables (or use defaults)
gateway_url = os.getenv('GATEWAY_URL', 'https://api-gateway.grouplinkone.com/')
public_access_key = os.getenv('PUBLIC_ACCESS_KEY', 'pub_test_abc123')
secret_access_key = os.getenv('SECRET_ACCESS_KEY', 'sec_test_xyz789')
secret_key = os.getenv('SECRET_KEY', 'super_secret_key_12345')

# Public credential (no HMAC)
client = APIGatewayClient(
    base_url=gateway_url,
    access_key=public_access_key
)
response = client.get("/service/path")

# Secret credential (with HMAC)
client = APIGatewayClient(
    base_url=gateway_url,
    access_key=secret_access_key,
    secret_key=secret_key
)
response = client.get("/service/path", require_hmac=True)
```

## Run Example

```bash
# Using defaults (production URL)
python api_gateway_client.py

# Using custom configuration
GATEWAY_URL=http://localhost:8080 \
PUBLIC_ACCESS_KEY=pub_test_abc123 \
SECRET_ACCESS_KEY=sec_test_xyz789 \
SECRET_KEY=super_secret_key_12345 \
python api_gateway_client.py
```

## HMAC Signature Algorithm

The signature is computed as follows:

```
string_to_sign = "{METHOD}\n{PATH}\n{TIMESTAMP}\n{BODY_SHA256}"
signature = HMAC-SHA256(secret_key, string_to_sign)
Authorization: {access_key}:{timestamp}:{signature}
```

Where:
- `METHOD`: HTTP method in uppercase (GET, POST, etc.)
- `PATH`: Request path (e.g., /api/v1/test)
- `TIMESTAMP`: Unix timestamp as string
- `BODY_SHA256`: SHA-256 hash of request body (hex encoded)
