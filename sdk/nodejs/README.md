# Node.js SDK Example

This example demonstrates how to authenticate with the API Gateway using Node.js.

## Requirements

- Node.js >= 14.0.0
- No external dependencies (uses built-in crypto and http modules)

## Configuration

The SDK uses environment variables with sensible defaults:

- `GATEWAY_URL` - API Gateway URL (default: `https://api-gateway.grouplinkone.com/`)
- `PUBLIC_ACCESS_KEY` - Public access key (default: `pub_test_abc123`)
- `SECRET_ACCESS_KEY` - Secret access key (default: `sec_test_xyz789`)
- `SECRET_KEY` - Secret key for HMAC signing (default: `super_secret_key_12345`)

## Usage

```javascript
const APIGatewayClient = require('./apiGatewayClient');

// Load from environment variables (or use defaults)
const gatewayUrl = process.env.GATEWAY_URL || 'https://api-gateway.grouplinkone.com/';
const publicAccessKey = process.env.PUBLIC_ACCESS_KEY || 'pub_test_abc123';
const secretAccessKey = process.env.SECRET_ACCESS_KEY || 'sec_test_xyz789';
const secretKey = process.env.SECRET_KEY || 'super_secret_key_12345';

// Public credential (no HMAC)
const client = new APIGatewayClient(
  gatewayUrl,
  publicAccessKey
);
const response = await client.get('/service/path');

// Secret credential (with HMAC)
const client = new APIGatewayClient(
  gatewayUrl,
  secretAccessKey,
  secretKey
);
const response = await client.get('/service/path', true);
```

## Run Example

```bash
# Using defaults (production URL)
node apiGatewayClient.js

# Using custom configuration
GATEWAY_URL=http://localhost:8080 \
PUBLIC_ACCESS_KEY=pub_test_abc123 \
SECRET_ACCESS_KEY=sec_test_xyz789 \
SECRET_KEY=super_secret_key_12345 \
node apiGatewayClient.js
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
