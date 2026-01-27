package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

// APIGatewayClient represents the API Gateway client
type APIGatewayClient struct {
	baseURL    string
	accessKey  string
	secretKey  *string
	httpClient *http.Client
}

// NewAPIGatewayClient creates a new API Gateway client
func NewAPIGatewayClient(baseURL, accessKey string, secretKey *string) *APIGatewayClient {
	if !strings.HasSuffix(baseURL, "/") {
		baseURL += "/"
	}

	return &APIGatewayClient{
		baseURL:    baseURL,
		accessKey:  accessKey,
		secretKey:  secretKey,
		httpClient: &http.Client{},
	}
}

// computeSHA256 computes SHA-256 hash of data
func (c *APIGatewayClient) computeSHA256(data []byte) string {
	hash := sha256.Sum256(data)
	return hex.EncodeToString(hash[:])
}

// signRequest generates HMAC signature for request
func (c *APIGatewayClient) signRequest(method, path, timestamp string, body []byte, query string) (string, error) {
	if c.secretKey == nil {
		return "", fmt.Errorf("secret key is required for HMAC signing")
	}

	// Compute body hash
	bodyHash := c.computeSHA256(body)

	// Create string to sign: METHOD\nPATH\nQUERY\nBODY_HASH\nTIMESTAMP
	stringToSign := fmt.Sprintf("%s\n%s\n%s\n%s\n%s", method, path, query, bodyHash, timestamp)

	// Generate HMAC signature
	h := hmac.New(sha256.New, []byte(*c.secretKey))
	h.Write([]byte(stringToSign))
	signature := hex.EncodeToString(h.Sum(nil))

	return signature, nil
}

// makeRequest makes an authenticated request to the API Gateway
func (c *APIGatewayClient) makeRequest(method, path string, body []byte, requireHMAC bool) (*http.Response, error) {
	// Get current timestamp
	timestamp := strconv.FormatInt(time.Now().Unix(), 10)

	// Parse path and query string
	parsedURL, err := url.Parse(path)
	if err != nil {
		return nil, err
	}
	cleanPath := parsedURL.Path
	query := parsedURL.RawQuery

	// Build authorization header
	var signature string
	if requireHMAC {
		if c.secretKey == nil {
			return nil, fmt.Errorf("secret key is required for HMAC-signed requests")
		}
		signature, err = c.signRequest(method, cleanPath, timestamp, body, query)
		if err != nil {
			return nil, err
		}
	} else {
		// For public credentials, use dummy signature (won't be validated)
		signature = "public"
	}

	authHeader := fmt.Sprintf("GL-HMAC access_key=%s signature=%s timestamp=%s", c.accessKey, signature, timestamp)

	// Create request
	fullURL := strings.TrimSuffix(c.baseURL, "/") + path
	req, err := http.NewRequest(method, fullURL, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}

	req.Header.Set("Authorization", authHeader)
	if len(body) > 0 {
		req.Header.Set("Content-Type", "application/json")
	}

	// Send request
	return c.httpClient.Do(req)
}

// Get makes a GET request
func (c *APIGatewayClient) Get(path string, requireHMAC bool) (*http.Response, error) {
	return c.makeRequest("GET", path, nil, requireHMAC)
}

// Post makes a POST request
func (c *APIGatewayClient) Post(path, body string, requireHMAC bool) (*http.Response, error) {
	return c.makeRequest("POST", path, []byte(body), requireHMAC)
}

// Response represents an HTTP response
type Response struct {
	StatusCode int
	Body       string
}

func main() {
	// Load configuration from environment variables with defaults
	gatewayURL := os.Getenv("GATEWAY_URL")
	if gatewayURL == "" {
		gatewayURL = "https://api-gateway.grouplinkone.com/"
	}

	publicAccessKey := os.Getenv("PUBLIC_ACCESS_KEY")
	if publicAccessKey == "" {
		publicAccessKey = "pub_test_abc123"
	}

	secretAccessKey := os.Getenv("SECRET_ACCESS_KEY")
	if secretAccessKey == "" {
		secretAccessKey = "sec_test_xyz789"
	}

	secretKeyValue := os.Getenv("SECRET_KEY")
	if secretKeyValue == "" {
		secretKeyValue = "super_secret_key_12345"
	}

	hasError := false

	// Example 1: Public credential (no HMAC)
	fmt.Println("============================================================")
	fmt.Println("Example 1: Public Credential (No HMAC)")
	fmt.Println("============================================================")

	publicClient := NewAPIGatewayClient(gatewayURL, publicAccessKey, nil)

	resp1, err := publicClient.Get("/internal/verify", false)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		hasError = true
	} else {
		defer resp1.Body.Close()
		body, _ := io.ReadAll(resp1.Body)
		fmt.Printf("Status: %d\n", resp1.StatusCode)
		fmt.Printf("Response: %s\n", string(body))
		if resp1.StatusCode >= 400 {
			hasError = true
		}
	}
	fmt.Println()

	// Example 2: Secret credential with HMAC
	fmt.Println("============================================================")
	fmt.Println("Example 2: Secret Credential (With HMAC)")
	fmt.Println("============================================================")

	secretClient := NewAPIGatewayClient(gatewayURL, secretAccessKey, &secretKeyValue)

	resp2, err := secretClient.Get("/internal/verify", true)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		hasError = true
	} else {
		defer resp2.Body.Close()
		body, _ := io.ReadAll(resp2.Body)
		fmt.Printf("Status: %d\n", resp2.StatusCode)
		fmt.Printf("Response: %s\n", string(body))
		if resp2.StatusCode >= 400 {
			hasError = true
		}
	}
	fmt.Println()

	// Example 3: POST request with body and HMAC
	fmt.Println("============================================================")
	fmt.Println("Example 3: POST Request with Body and HMAC")
	fmt.Println("============================================================")

	jsonBody := fmt.Sprintf(`{"message":"Hello from Go SDK!","timestamp":%d}`, time.Now().Unix())

	resp3, err := secretClient.Post("/internal/verify", jsonBody, true)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		hasError = true
	} else {
		defer resp3.Body.Close()
		body, _ := io.ReadAll(resp3.Body)
		fmt.Printf("Status: %d\n", resp3.StatusCode)
		fmt.Printf("Response: %s\n", string(body))
		if resp3.StatusCode >= 400 {
			hasError = true
		}
	}

	if hasError {
		os.Exit(1)
	}
}
