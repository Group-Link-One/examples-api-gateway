import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.securestore.SecureStoreService

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * Signs the outgoing request with GL-HMAC for the GroupLink API Gateway.
 *
 * Exchange properties (set them in a Content Modifier before this step):
 *   GL_CredentialAlias  Secure Store "User Credentials" alias. User = access key, Password = secret key.
 *   GL_Method           HTTP method, default GET.
 *   GL_Path             Request path, e.g. /v1/device-keys/trust-decision
 *   GL_Query            Raw query string, already URL-encoded, without "?". Optional.
 *
 * HTTP receiver adapter: Address https://<gateway-host>${property.GL_Path}, Authentication None,
 * Query empty. Add Authorization to the iFlow's Allowed Header(s).
 */
def Message processData(Message message) {
    String alias = required(message, 'GL_CredentialAlias')
    String method = (message.getProperty('GL_Method') ?: 'GET').toString().trim().toUpperCase()
    String path = required(message, 'GL_Path')
    String query = (message.getProperty('GL_Query') ?: '').toString().trim()
    if (query.startsWith('?')) {
        query = query.substring(1)
    }

    def credential = ITApiFactory.getService(SecureStoreService.class, null).getUserCredential(alias)
    if (credential == null) {
        throw new IllegalStateException("Secure Store alias not found: " + alias)
    }
    String accessKey = credential.getUsername()
    String secretKey = new String(credential.getPassword())

    // The adapter does not send a body on GET/HEAD, so the gateway hashes an empty one.
    byte[] body
    if (method == 'GET' || method == 'HEAD') {
        body = new byte[0]
        message.setBody(body)
    } else {
        body = message.getBody(byte[].class) ?: new byte[0]
    }

    String timestamp = String.valueOf(System.currentTimeMillis().intdiv(1000))
    String bodyHash = MessageDigest.getInstance('SHA-256').digest(body).encodeHex().toString()
    String stringToSign = [method, path, query, bodyHash, timestamp].join('\n')

    Mac mac = Mac.getInstance('HmacSHA256')
    mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), 'HmacSHA256'))
    String signature = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)).encodeHex().toString()

    message.setHeader('Authorization', "GL-HMAC access_key=${accessKey} signature=${signature} timestamp=${timestamp}".toString())
    message.setHeader('CamelHttpMethod', method)
    if (query) {
        message.setHeader('CamelHttpQuery', query)
    }
    return message
}

private static String required(Message message, String name) {
    def value = message.getProperty(name)
    if (value == null || value.toString().trim().isEmpty()) {
        throw new IllegalArgumentException("Missing exchange property: " + name)
    }
    return value.toString().trim()
}
