#!/usr/bin/env python3
"""
API Gateway Client SDK for Python

This SDK demonstrates how to authenticate with the API Gateway using:
1. Public credentials (access key only)
2. Secret credentials with HMAC signing

HMAC Signature Format:
    Authorization: GL-HMAC access_key=<key> signature=<sig> timestamp=<ts>

    Where signature = HMAC-SHA256(secret_key, "<method>\n<path>\n<timestamp>\n<body_sha256>")
"""

import hashlib
import hmac
import time
import requests
import sys
from typing import Optional, Dict, Any


class APIGatewayClient:
    """Client for making authenticated requests to the API Gateway"""

    def __init__(self, base_url: str, access_key: str, secret_key: Optional[str] = None):
        """
        Initialize the API Gateway client

        Args:
            base_url: Base URL of the API Gateway (e.g., "http://localhost:8080")
            access_key: Your access key credential
            secret_key: Your secret key (required for HMAC-signed requests)
        """
        self.base_url = base_url.rstrip('/')
        self.access_key = access_key
        self.secret_key = secret_key

    def _compute_body_hash(self, body: bytes) -> str:
        """Compute SHA-256 hash of request body"""
        return hashlib.sha256(body).hexdigest()

    def _sign_request(self, method: str, path: str, timestamp: str, body: bytes, query: str = '') -> str:
        """
        Generate HMAC signature for request

        Args:
            method: HTTP method (GET, POST, etc.)
            path: Request path (e.g., "/api/v1/test")
            timestamp: Unix timestamp as string
            body: Request body as bytes
            query: Query string (optional)

        Returns:
            HMAC signature (hex encoded)
        """
        if not self.secret_key:
            raise ValueError("Secret key is required for HMAC signing")

        # Compute body hash
        body_hash = self._compute_body_hash(body)

        # Create string to sign: METHOD\nPATH\nQUERY\nBODY_HASH\nTIMESTAMP
        string_to_sign = f"{method}\n{path}\n{query}\n{body_hash}\n{timestamp}"

        # Generate HMAC signature
        signature = hmac.new(
            self.secret_key.encode('utf-8'),
            string_to_sign.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()

        return signature

    def _make_request(
        self,
        method: str,
        path: str,
        body: Optional[bytes] = None,
        require_hmac: bool = False,
        headers: Optional[Dict[str, str]] = None
    ) -> requests.Response:
        """
        Make an authenticated request to the API Gateway

        Args:
            method: HTTP method
            path: Request path
            body: Request body (optional)
            require_hmac: Whether to include HMAC signature
            headers: Additional headers (optional)

        Returns:
            Response object
        """
        if body is None:
            body = b''

        # Prepare headers
        req_headers = headers.copy() if headers else {}

        # Get current timestamp
        timestamp = str(int(time.time()))

        # Parse path and query string
        from urllib.parse import urlparse, parse_qs
        parsed = urlparse(path)
        clean_path = parsed.path
        query = parsed.query or ''

        # Build authorization header
        if require_hmac:
            if not self.secret_key:
                raise ValueError("Secret key is required for HMAC-signed requests")

            signature = self._sign_request(method, clean_path, timestamp, body, query)
        else:
            # For public credentials, use dummy signature (won't be validated)
            signature = "public"

        auth_header = f"GL-HMAC access_key={self.access_key} signature={signature} timestamp={timestamp}"
        req_headers['Authorization'] = auth_header

        # Make request
        url = f"{self.base_url}{path}"

        response = requests.request(
            method=method,
            url=url,
            data=body if body else None,
            headers=req_headers,
            timeout=5
        )

        return response

    def get(
        self,
        path: str,
        require_hmac: bool = False,
        headers: Optional[Dict[str, str]] = None
    ) -> requests.Response:
        """Make a GET request"""
        return self._make_request('GET', path, require_hmac=require_hmac, headers=headers)

    def post(
        self,
        path: str,
        data: Optional[Dict[str, Any]] = None,
        require_hmac: bool = False,
        headers: Optional[Dict[str, str]] = None
    ) -> requests.Response:
        """Make a POST request"""
        import json
        body = json.dumps(data).encode('utf-8') if data else b''

        req_headers = headers.copy() if headers else {}
        if data:
            req_headers['Content-Type'] = 'application/json'

        return self._make_request('POST', path, body=body, require_hmac=require_hmac, headers=req_headers)


def main():
    """Example usage of the API Gateway client"""

    # Load configuration from environment variables with defaults
    import os
    gateway_url = os.getenv('GATEWAY_URL', 'https://api-gateway.grouplinkone.com/')
    public_access_key = os.getenv('PUBLIC_ACCESS_KEY', 'pub_test_abc123')
    secret_access_key = os.getenv('SECRET_ACCESS_KEY', 'sec_test_xyz789')
    secret_key = os.getenv('SECRET_KEY', 'super_secret_key_12345')

    failed = False

    # Example 1: Public credential (no HMAC)
    print("=" * 60)
    print("Example 1: Public Credential (No HMAC)")
    print("=" * 60)

    public_client = APIGatewayClient(
        base_url=gateway_url,
        access_key=public_access_key
    )

    try:
        response = public_client.get("/internal/verify")
        if response.status_code == 200:
            print(f"Status: {response.status_code}")
            print(f"Response: {response.json()}")
        else:
            print(f"Status: {response.status_code}")
            print(f"Response: {response.text}")
            failed = True
    except Exception as e:
        print(f"Error: {e}")
        failed = True

    print()

    # Example 2: Secret credential with HMAC
    print("=" * 60)
    print("Example 2: Secret Credential (With HMAC)")
    print("=" * 60)

    secret_client = APIGatewayClient(
        base_url=gateway_url,
        access_key=secret_access_key,
        secret_key=secret_key
    )

    try:
        response = secret_client.get("/internal/verify", require_hmac=True)
        if response.status_code == 200:
            print(f"Status: {response.status_code}")
            print(f"Response: {response.json()}")
        else:
            print(f"Status: {response.status_code}")
            print(f"Response: {response.text}")
            failed = True
    except Exception as e:
        print(f"Error: {e}")
        failed = True

    print()

    # Example 3: POST request with body and HMAC
    print("=" * 60)
    print("Example 3: POST Request with Body and HMAC")
    print("=" * 60)

    try:
        response = secret_client.post(
            "/internal/verify",
            data={"message": "Hello from Python SDK!", "timestamp": int(time.time())},
            require_hmac=True
        )
        if response.status_code == 200:
            print(f"Status: {response.status_code}")
            print(f"Response: {response.json()}")
        else:
            print(f"Status: {response.status_code}")
            print(f"Response: {response.text}")
            failed = True
    except Exception as e:
        print(f"Error: {e}")
        failed = True

    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
