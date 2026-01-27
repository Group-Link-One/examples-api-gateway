using System;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;

/// <summary>
/// API Gateway Client SDK for C#
///
/// This SDK demonstrates how to authenticate with the API Gateway using:
/// 1. Public credentials (access key only)
/// 2. Secret credentials with HMAC signing
///
/// HMAC Signature Format:
///     Authorization: GL-HMAC access_key=&lt;key&gt; signature=&lt;sig&gt; timestamp=&lt;ts&gt;
///
///     Where signature = HMAC-SHA256(secret_key, "&lt;method&gt;\n&lt;path&gt;\n&lt;timestamp&gt;\n&lt;body_sha256&gt;")
/// </summary>
public class APIGatewayClient
{
    private readonly string _baseUrl;
    private readonly string _accessKey;
    private readonly string _secretKey;
    private readonly HttpClient _httpClient;

    /// <summary>
    /// Initialize the API Gateway client
    /// </summary>
    /// <param name="baseUrl">Base URL of the API Gateway (e.g., "http://localhost:8080")</param>
    /// <param name="accessKey">Your access key credential</param>
    /// <param name="secretKey">Your secret key (required for HMAC-signed requests)</param>
    public APIGatewayClient(string baseUrl, string accessKey, string secretKey = null)
    {
        _baseUrl = baseUrl.TrimEnd('/');
        _accessKey = accessKey;
        _secretKey = secretKey;
        _httpClient = new HttpClient();
    }

    /// <summary>
    /// Compute SHA-256 hash of data
    /// </summary>
    /// <param name="data">Data to hash</param>
    /// <returns>Hex-encoded SHA-256 hash</returns>
    private string ComputeSHA256(byte[] data)
    {
        using (var sha256 = SHA256.Create())
        {
            byte[] hash = sha256.ComputeHash(data);
            return BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
        }
    }

    /// <summary>
    /// Generate HMAC signature for request
    /// </summary>
    /// <param name="method">HTTP method (GET, POST, etc.)</param>
    /// <param name="path">Request path (e.g., "/api/v1/test")</param>
    /// <param name="timestamp">Unix timestamp as string</param>
    /// <param name="body">Request body</param>
    /// <param name="query">Query string (optional)</param>
    /// <returns>HMAC signature (hex encoded)</returns>
    private string SignRequest(string method, string path, string timestamp, byte[] body, string query = "")
    {
        if (string.IsNullOrEmpty(_secretKey))
        {
            throw new InvalidOperationException("Secret key is required for HMAC signing");
        }

        // Compute body hash
        string bodyHash = ComputeSHA256(body);

        // Create string to sign: METHOD\nPATH\nQUERY\nBODY_HASH\nTIMESTAMP
        string stringToSign = $"{method}\n{path}\n{query}\n{bodyHash}\n{timestamp}";

        // Generate HMAC signature
        using (var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(_secretKey)))
        {
            byte[] signature = hmac.ComputeHash(Encoding.UTF8.GetBytes(stringToSign));
            return BitConverter.ToString(signature).Replace("-", "").ToLowerInvariant();
        }
    }

    /// <summary>
    /// Make an authenticated request to the API Gateway
    /// </summary>
    /// <param name="method">HTTP method</param>
    /// <param name="path">Request path</param>
    /// <param name="body">Request body (null for no body)</param>
    /// <param name="requireHmac">Whether to include HMAC signature</param>
    /// <returns>HTTP response message</returns>
    private async Task<HttpResponseMessage> MakeRequestAsync(
        HttpMethod method,
        string path,
        string body = null,
        bool requireHmac = false)
    {
        byte[] bodyBytes = string.IsNullOrEmpty(body) ? Array.Empty<byte>() : Encoding.UTF8.GetBytes(body);

        // Get current timestamp
        string timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds().ToString();

        // Parse path and query string
        string cleanPath = path;
        string query = "";
        int queryIndex = path.IndexOf('?');
        if (queryIndex != -1)
        {
            cleanPath = path.Substring(0, queryIndex);
            query = path.Substring(queryIndex + 1);
        }

        // Build authorization header
        string signature;
        if (requireHmac)
        {
            if (string.IsNullOrEmpty(_secretKey))
            {
                throw new InvalidOperationException("Secret key is required for HMAC-signed requests");
            }
            signature = SignRequest(method.Method, cleanPath, timestamp, bodyBytes, query);
        }
        else
        {
            // For public credentials, use dummy signature (won't be validated)
            signature = "public";
        }

        string authHeader = $"GL-HMAC access_key={_accessKey} signature={signature} timestamp={timestamp}";

        // Create request
        var request = new HttpRequestMessage(method, $"{_baseUrl}{path}");
        request.Headers.Add("Authorization", authHeader);

        if (!string.IsNullOrEmpty(body))
        {
            request.Content = new StringContent(body, Encoding.UTF8, "application/json");
        }

        // Send request
        return await _httpClient.SendAsync(request);
    }

    /// <summary>
    /// Make a GET request
    /// </summary>
    /// <param name="path">Request path</param>
    /// <param name="requireHmac">Whether to include HMAC signature</param>
    /// <returns>Response object</returns>
    public async Task<ApiResponse> GetAsync(string path, bool requireHmac = false)
    {
        var response = await MakeRequestAsync(HttpMethod.Get, path, null, requireHmac);
        string responseBody = await response.Content.ReadAsStringAsync();
        return new ApiResponse((int)response.StatusCode, responseBody);
    }

    /// <summary>
    /// Make a POST request
    /// </summary>
    /// <param name="path">Request path</param>
    /// <param name="body">Request body (JSON string)</param>
    /// <param name="requireHmac">Whether to include HMAC signature</param>
    /// <returns>Response object</returns>
    public async Task<ApiResponse> PostAsync(string path, string body = null, bool requireHmac = false)
    {
        var response = await MakeRequestAsync(HttpMethod.Post, path, body, requireHmac);
        string responseBody = await response.Content.ReadAsStringAsync();
        return new ApiResponse((int)response.StatusCode, responseBody);
    }

    /// <summary>
    /// Response class to hold HTTP response data
    /// </summary>
    public class ApiResponse
    {
        public int StatusCode { get; }
        public string Body { get; }

        public ApiResponse(int statusCode, string body)
        {
            StatusCode = statusCode;
            Body = body;
        }

        public override string ToString()
        {
            return $"Response{{StatusCode={StatusCode}, Body='{Body}'}}";
        }
    }
}

/// <summary>
/// Example usage
/// </summary>
class Program
{
    static async Task<int> Main(string[] args)
    {
        // Load configuration from environment variables with defaults
        string gatewayUrl = Environment.GetEnvironmentVariable("GATEWAY_URL") ?? "https://api-gateway.grouplinkone.com/";
        string publicAccessKey = Environment.GetEnvironmentVariable("PUBLIC_ACCESS_KEY") ?? "pub_test_abc123";
        string secretAccessKey = Environment.GetEnvironmentVariable("SECRET_ACCESS_KEY") ?? "sec_test_xyz789";
        string secretKey = Environment.GetEnvironmentVariable("SECRET_KEY") ?? "super_secret_key_12345";

        bool hasError = false;

        try
        {
            // Example 1: Public credential (no HMAC)
            Console.WriteLine("============================================================");
            Console.WriteLine("Example 1: Public Credential (No HMAC)");
            Console.WriteLine("============================================================");

            var publicClient = new APIGatewayClient(
                gatewayUrl,
                publicAccessKey
            );

            var response1 = await publicClient.GetAsync("/internal/verify", false);
            Console.WriteLine($"Status: {response1.StatusCode}");
            Console.WriteLine($"Response: {response1.Body}");
            if (response1.StatusCode >= 400)
            {
                hasError = true;
            }
            Console.WriteLine();

            // Example 2: Secret credential with HMAC)
            Console.WriteLine("============================================================");
            Console.WriteLine("Example 2: Secret Credential (With HMAC)");
            Console.WriteLine("============================================================");

            var secretClient = new APIGatewayClient(
                gatewayUrl,
                secretAccessKey,
                secretKey
            );

            var response2 = await secretClient.GetAsync("/internal/verify", true);
            Console.WriteLine($"Status: {response2.StatusCode}");
            Console.WriteLine($"Response: {response2.Body}");
            if (response2.StatusCode >= 400)
            {
                hasError = true;
            }
            Console.WriteLine();

            // Example 3: POST request with body and HMAC
            Console.WriteLine("============================================================");
            Console.WriteLine("Example 3: POST Request with Body and HMAC");
            Console.WriteLine("============================================================");

            string jsonBody = $"{{\"message\":\"Hello from C# SDK!\",\"timestamp\":{DateTimeOffset.UtcNow.ToUnixTimeSeconds()}}}";

            var response3 = await secretClient.PostAsync("/internal/verify", jsonBody, true);
            Console.WriteLine($"Status: {response3.StatusCode}");
            Console.WriteLine($"Response: {response3.Body}");
            if (response3.StatusCode >= 400)
            {
                hasError = true;
            }

        }
        catch (Exception e)
        {
            Console.Error.WriteLine($"Error: {e.Message}");
            hasError = true;
        }

        if (hasError)
        {
            Console.WriteLine($"We encountered errors during execution.");
            Environment.Exit(1);
        }

        return 0;
    }
}
