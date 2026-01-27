#!/usr/bin/env node
/**
 * API Gateway Client SDK for Node.js
 *
 * This SDK demonstrates how to authenticate with the API Gateway using:
 * 1. Public credentials (access key only)
 * 2. Secret credentials with HMAC signing
 *
 * HMAC Signature Format:
 *     Authorization: GL-HMAC access_key=<key> signature=<sig> timestamp=<ts>
 *
 *     Where signature = HMAC-SHA256(secret_key, "<method>\n<path>\n<timestamp>\n<body_sha256>")
 */

const crypto = require('crypto');
const https = require('https');
const http = require('http');
const { URL } = require('url');

class APIGatewayClient {
  /**
   * Initialize the API Gateway client
   *
   * @param {string} baseUrl - Base URL of the API Gateway (e.g., "http://localhost:8080")
   * @param {string} accessKey - Your access key credential
   * @param {string|null} secretKey - Your secret key (required for HMAC-signed requests)
   */
  constructor(baseUrl, accessKey, secretKey = null) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.accessKey = accessKey;
    this.secretKey = secretKey;
  }

  /**
   * Compute SHA-256 hash of request body
   *
   * @param {Buffer} body - Request body
   * @returns {string} - Hex-encoded SHA-256 hash
   */
  _computeBodyHash(body) {
    return crypto.createHash('sha256').update(body).digest('hex');
  }

  /**
   * Generate HMAC signature for request
   *
   * @param {string} method - HTTP method (GET, POST, etc.)
   * @param {string} path - Request path (e.g., "/api/v1/test")
   * @param {string} timestamp - Unix timestamp as string
   * @param {Buffer} body - Request body
   * @param {string} query - Query string (optional)
   * @returns {string} - HMAC signature (hex encoded)
   */
  _signRequest(method, path, timestamp, body, query = '') {
    if (!this.secretKey) {
      throw new Error('Secret key is required for HMAC signing');
    }

    // Compute body hash
    const bodyHash = this._computeBodyHash(body);

    // Create string to sign: METHOD\nPATH\nQUERY\nBODY_HASH\nTIMESTAMP
    const stringToSign = `${method}\n${path}\n${query}\n${bodyHash}\n${timestamp}`;

    // Generate HMAC signature
    const signature = crypto
      .createHmac('sha256', this.secretKey)
      .update(stringToSign)
      .digest('hex');

    return signature;
  }

  /**
   * Make an authenticated request to the API Gateway
   *
   * @param {string} method - HTTP method
   * @param {string} path - Request path
   * @param {Buffer|null} body - Request body (optional)
   * @param {boolean} requireHmac - Whether to include HMAC signature
   * @param {Object} headers - Additional headers (optional)
   * @returns {Promise<Object>} - Response object with status, headers, and data
   */
  async _makeRequest(method, path, body = null, requireHmac = false, headers = {}) {
    if (body === null) {
      body = Buffer.alloc(0);
    }

    // Get current timestamp
    const timestamp = Math.floor(Date.now() / 1000).toString();

    // Parse path and query string for signing
    const parsedUrl = new URL(path, 'http://dummy');
    const cleanPath = parsedUrl.pathname;
    const query = parsedUrl.search ? parsedUrl.search.substring(1) : '';

    // Build authorization header
    let signature;
    if (requireHmac) {
      if (!this.secretKey) {
        throw new Error('Secret key is required for HMAC-signed requests');
      }
      signature = this._signRequest(method, cleanPath, timestamp, body, query);
    } else {
      // For public credentials, use dummy signature (won't be validated)
      signature = 'public';
    }

    const authHeader = `GL-HMAC access_key=${this.accessKey} signature=${signature} timestamp=${timestamp}`;

    // Prepare headers
    const reqHeaders = {
      ...headers,
      'Authorization': authHeader,
    };

    if (body.length > 0 && !reqHeaders['Content-Type']) {
      reqHeaders['Content-Type'] = 'application/json';
    }

    // Make request
    const url = new URL(path, this.baseUrl);
    const protocol = url.protocol === 'https:' ? https : http;

    return new Promise((resolve, reject) => {
      const options = {
        method,
        hostname: url.hostname,
        port: url.port,
        path: url.pathname + url.search,
        headers: reqHeaders,
      };

      const req = protocol.request(options, (res) => {
        let data = '';

        res.on('data', (chunk) => {
          data += chunk;
        });

        res.on('end', () => {
          try {
            const jsonData = data ? JSON.parse(data) : null;
            resolve({
              status: res.statusCode,
              headers: res.headers,
              data: jsonData,
            });
          } catch (error) {
            resolve({
              status: res.statusCode,
              headers: res.headers,
              data: data,
            });
          }
        });
      });

      req.on('error', (error) => {
        reject(error);
      });

      if (body.length > 0) {
        req.write(body);
      }

      req.end();
    });
  }

  /**
   * Make a GET request
   *
   * @param {string} path - Request path
   * @param {boolean} requireHmac - Whether to include HMAC signature
   * @param {Object} headers - Additional headers (optional)
   * @returns {Promise<Object>} - Response object
   */
  async get(path, requireHmac = false, headers = {}) {
    return this._makeRequest('GET', path, null, requireHmac, headers);
  }

  /**
   * Make a POST request
   *
   * @param {string} path - Request path
   * @param {Object|null} data - Request data (will be JSON-encoded)
   * @param {boolean} requireHmac - Whether to include HMAC signature
   * @param {Object} headers - Additional headers (optional)
   * @returns {Promise<Object>} - Response object
   */
  async post(path, data = null, requireHmac = false, headers = {}) {
    const body = data ? Buffer.from(JSON.stringify(data)) : Buffer.alloc(0);
    return this._makeRequest('POST', path, body, requireHmac, headers);
  }
}

// Example usage
async function main() {
  // Load configuration from environment variables with defaults
  const GATEWAY_URL = process.env.GATEWAY_URL || 'https://api-gateway.grouplinkone.com/';
  const PUBLIC_ACCESS_KEY = process.env.PUBLIC_ACCESS_KEY || 'pub_test_abc123';
  const SECRET_ACCESS_KEY = process.env.SECRET_ACCESS_KEY || 'sec_test_xyz789';
  const SECRET_KEY = process.env.SECRET_KEY || 'super_secret_key_12345';

  let hasError = false;

  // Example 1: Public credential (no HMAC)
  console.log('='.repeat(60));
  console.log('Example 1: Public Credential (No HMAC)');
  console.log('='.repeat(60));

  const publicClient = new APIGatewayClient(
    GATEWAY_URL,
    PUBLIC_ACCESS_KEY
  );

  try {
    const response = await publicClient.get('/internal/verify');
    console.log(`Status: ${response.status}`);
    console.log(`Response:`, response.data);
    if (response.status >= 400) {
      hasError = true;
    }
  } catch (error) {
    console.error(`Error: ${error.message}`);
    hasError = true;
  }

  console.log();

  // Example 2: Secret credential with HMAC
  console.log('='.repeat(60));
  console.log('Example 2: Secret Credential (With HMAC)');
  console.log('='.repeat(60));

  const secretClient = new APIGatewayClient(
    GATEWAY_URL,
    SECRET_ACCESS_KEY,
    SECRET_KEY
  );

  try {
    const response = await secretClient.get('/internal/verify', true);
    console.log(`Status: ${response.status}`);
    console.log(`Response:`, response.data);
    if (response.status >= 400) {
      hasError = true;
    }
  } catch (error) {
    console.error(`Error: ${error.message}`);
    hasError = true;
  }

  console.log();

  // Example 3: POST request with body and HMAC
  console.log('='.repeat(60));
  console.log('Example 3: POST Request with Body and HMAC');
  console.log('='.repeat(60));

  try {
    const response = await secretClient.post(
      '/internal/verify',
      { message: 'Hello from Node.js SDK!', timestamp: Math.floor(Date.now() / 1000) },
      true
    );
    console.log(`Status: ${response.status}`);
    console.log(`Response:`, response.data);
    if (response.status >= 400) {
      hasError = true;
    }
  } catch (error) {
    console.error(`Error: ${error.message}`);
    hasError = true;
  }

  if (hasError) {
    process.exit(1);
  }
}

// Export the class for use as a module
module.exports = APIGatewayClient;

// Run examples if executed directly
if (require.main === module) {
  main().catch(console.error);
}
