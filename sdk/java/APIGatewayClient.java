import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * API Gateway Client SDK for Java
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
public class APIGatewayClient {
    private final String baseUrl;
    private final String accessKey;
    private final String secretKey;

    /**
     * Initialize the API Gateway client
     *
     * @param baseUrl   Base URL of the API Gateway (e.g., "http://localhost:8080")
     * @param accessKey Your access key credential
     * @param secretKey Your secret key (required for HMAC-signed requests)
     */
    public APIGatewayClient(String baseUrl, String accessKey, String secretKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    /**
     * Compute SHA-256 hash of data
     *
     * @param data Data to hash
     * @return Hex-encoded SHA-256 hash
     */
    private String computeSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Generate HMAC signature for request
     *
     * @param method    HTTP method (GET, POST, etc.)
     * @param path      Request path (e.g., "/api/v1/test")
     * @param timestamp Unix timestamp as string
     * @param body      Request body
     * @param query     Query string (optional)
     * @return HMAC signature (hex encoded)
     */
    private String signRequest(String method, String path, String timestamp, byte[] body, String query) {
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("Secret key is required for HMAC signing");
        }

        try {
            // Compute body hash
            String bodyHash = computeSHA256(body);

            // Create string to sign: METHOD\nPATH\nQUERY\nBODY_HASH\nTIMESTAMP
            String stringToSign = method + "\n" + path + "\n" + query + "\n" + bodyHash + "\n" + timestamp;

            // Generate HMAC signature
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            hmac.init(secretKeySpec);
            byte[] signature = hmac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign request", e);
        }
    }

    /**
     * Convert byte array to hex string
     *
     * @param bytes Byte array
     * @return Hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Make an authenticated request to the API Gateway
     *
     * @param method      HTTP method
     * @param path        Request path
     * @param body        Request body (null for no body)
     * @param requireHmac Whether to include HMAC signature
     * @return Response object
     */
    public Response makeRequest(String method, String path, String body, boolean requireHmac) throws IOException {
        byte[] bodyBytes = (body != null) ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];

        // Get current timestamp
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        // Parse path and query string
        String cleanPath = path;
        String query = "";
        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            cleanPath = path.substring(0, queryIndex);
            query = path.substring(queryIndex + 1);
        }

        // Build authorization header
        String signature;
        if (requireHmac) {
            if (secretKey == null || secretKey.isEmpty()) {
                throw new IllegalStateException("Secret key is required for HMAC-signed requests");
            }
            signature = signRequest(method, cleanPath, timestamp, bodyBytes, query);
        } else {
            // For public credentials, use dummy signature (won't be validated)
            signature = "public";
        }

        String authHeader = "GL-HMAC access_key=" + accessKey + " signature=" + signature + " timestamp=" + timestamp;

        // Make HTTP request
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", authHeader);

        if (bodyBytes.length > 0) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }
        }

        // Read response
        int statusCode = conn.getResponseCode();
        BufferedReader reader;
        if (statusCode >= 200 && statusCode < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return new Response(statusCode, response.toString());
    }

    /**
     * Make a GET request
     *
     * @param path        Request path
     * @param requireHmac Whether to include HMAC signature
     * @return Response object
     */
    public Response get(String path, boolean requireHmac) throws IOException {
        return makeRequest("GET", path, null, requireHmac);
    }

    /**
     * Make a POST request
     *
     * @param path        Request path
     * @param body        Request body (JSON string)
     * @param requireHmac Whether to include HMAC signature
     * @return Response object
     */
    public Response post(String path, String body, boolean requireHmac) throws IOException {
        return makeRequest("POST", path, body, requireHmac);
    }

    /**
     * Response class to hold HTTP response data
     */
    public static class Response {
        public final int statusCode;
        public final String body;

        public Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public String toString() {
            return "Response{statusCode=" + statusCode + ", body='" + body + "'}";
        }
    }

    /**
     * Example usage
     */
    public static void main(String[] args) {
        // Load configuration from environment variables with defaults
        String gatewayUrl = System.getenv("GATEWAY_URL");
        if (gatewayUrl == null || gatewayUrl.isEmpty()) {
            gatewayUrl = "https://api-gateway.grouplinkone.com/";
        }

        String publicAccessKey = System.getenv("PUBLIC_ACCESS_KEY");
        if (publicAccessKey == null || publicAccessKey.isEmpty()) {
            publicAccessKey = "pub_test_abc123";
        }

        String secretAccessKey = System.getenv("SECRET_ACCESS_KEY");
        if (secretAccessKey == null || secretAccessKey.isEmpty()) {
            secretAccessKey = "sec_test_xyz789";
        }

        String secretKey = System.getenv("SECRET_KEY");
        if (secretKey == null || secretKey.isEmpty()) {
            secretKey = "super_secret_key_12345";
        }

        boolean hasError = false;

        try {
            // Example 1: Public credential (no HMAC)
            System.out.println("============================================================");
            System.out.println("Example 1: Public Credential (No HMAC)");
            System.out.println("============================================================");

            APIGatewayClient publicClient = new APIGatewayClient(
                gatewayUrl,
                publicAccessKey,
                null
            );

            Response response1 = publicClient.get("/internal/verify", false);
            System.out.println("Status: " + response1.statusCode);
            System.out.println("Response: " + response1.body);
            if (response1.statusCode >= 400) {
                hasError = true;
            }
            System.out.println();

            // Example 2: Secret credential with HMAC
            System.out.println("============================================================");
            System.out.println("Example 2: Secret Credential (With HMAC)");
            System.out.println("============================================================");

            APIGatewayClient secretClient = new APIGatewayClient(
                gatewayUrl,
                secretAccessKey,
                secretKey
            );

            Response response2 = secretClient.get("/internal/verify", true);
            System.out.println("Status: " + response2.statusCode);
            System.out.println("Response: " + response2.body);
            if (response2.statusCode >= 400) {
                hasError = true;
            }
            System.out.println();

            // Example 3: POST request with body and HMAC
            System.out.println("============================================================");
            System.out.println("Example 3: POST Request with Body and HMAC");
            System.out.println("============================================================");

            String jsonBody = "{\"message\":\"Hello from Java SDK!\",\"timestamp\":" +
                System.currentTimeMillis() / 1000 + "}";

            Response response3 = secretClient.post("/internal/verify", jsonBody, true);
            System.out.println("Status: " + response3.statusCode);
            System.out.println("Response: " + response3.body);
            if (response3.statusCode >= 400) {
                hasError = true;
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            hasError = true;
        }

        if (hasError) {
            System.exit(1);
        }
    }
}
