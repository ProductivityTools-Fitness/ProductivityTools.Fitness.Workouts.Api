package top.productivitytools.fitness.api.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.productivitytools.fitness.api.entities.FitnessUser;
import top.productivitytools.fitness.api.repositories.FitnessUserRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(1)
public class FirebaseAuthFilter extends OncePerRequestFilter {

    public static final Set<String> ALLOWED_EMAILS = Set.of(
            "pwujczyk@gmail.com",
            "pwujczyk@google.com"
    );

    private static final String GOOGLE_CERTS_URL =
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
    private static final Pattern MAX_AGE_PATTERN = Pattern.compile("max-age=(\\d+)");
    private static final long DEFAULT_CACHE_SECONDS = 3600L;
    private static final long CLOCK_SKEW_SECONDS = 300L;

    private final FitnessUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final String firebaseProjectId;
    private final Function<String, PublicKey> publicKeyResolver;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private volatile Map<String, PublicKey> cachedPublicKeys = Collections.emptyMap();
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    @Autowired
    public FirebaseAuthFilter(
            FitnessUserRepository userRepository,
            ObjectMapper objectMapper,
            @Value("${firebase.project-id:ptprojectsweb}") String firebaseProjectId) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.firebaseProjectId = firebaseProjectId;
        this.publicKeyResolver = this::resolveGooglePublicKey;
    }

    public FirebaseAuthFilter(
            FitnessUserRepository userRepository,
            ObjectMapper objectMapper,
            String firebaseProjectId,
            Function<String, PublicKey> publicKeyResolver) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.firebaseProjectId = firebaseProjectId;
        this.publicKeyResolver = publicKeyResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        if (path == null) {
            return false;
        }
        // Exercise animations are served to <img> tags, and a browser cannot attach an
        // Authorization header to those. The endpoints expose nothing but pictures from the
        // shared catalogue - no user data - so they are left open.
        if (path.startsWith("/api/catalog/") && path.endsWith("/image")) {
            return true;
        }
        if (path.startsWith("/api/exercise/") && path.endsWith("/image")) {
            return true;
        }
        return path.startsWith("/api/debug") || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
                return;
            }

            String token = authHeader.substring(7).trim();
            FitnessUser user;
            try {
                user = resolveUserFromToken(token);
            } catch (AuthException e) {
                sendError(response, e.getStatusCode(), e.getMessage());
                return;
            }

            if (user == null) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired authentication token");
                return;
            }

            UserContext.setCurrentUser(user);
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String errorName = (status == HttpServletResponse.SC_FORBIDDEN) ? "Forbidden" : "Unauthorized";
        response.getWriter().write(String.format("{\"error\":\"%s\",\"message\":\"%s\"}", errorName, message));
    }

    private FitnessUser resolveUserFromToken(String token) throws AuthException {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            log.warn("Invalid JWT structure: expected 3 parts, got {}", parts.length);
            return null;
        }

        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode header = objectMapper.readTree(headerJson);

            String alg = header.hasNonNull("alg") ? header.get("alg").asText() : null;
            if (!"RS256".equals(alg)) {
                log.warn("Unsupported or missing JWT algorithm: {}", alg);
                return null;
            }

            String kid = header.hasNonNull("kid") ? header.get("kid").asText() : null;
            if (kid == null || kid.isBlank()) {
                log.warn("Missing 'kid' header in JWT");
                return null;
            }

            PublicKey publicKey = publicKeyResolver.apply(kid);
            if (publicKey == null) {
                log.warn("No matching Google public key found for kid: {}", kid);
                return null;
            }

            if (!verifySignature(parts[0], parts[1], parts[2], publicKey)) {
                log.warn("JWT cryptographic signature verification failed");
                return null;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(payloadJson);

            long now = Instant.now().getEpochSecond();

            if (!payload.hasNonNull("exp")) {
                log.warn("JWT is missing 'exp' claim");
                return null;
            }
            long exp = payload.get("exp").asLong();
            if (now >= exp) {
                log.warn("Token is expired (exp: {}, now: {})", exp, now);
                return null;
            }

            if (payload.hasNonNull("iat")) {
                long iat = payload.get("iat").asLong();
                if (iat > now + CLOCK_SKEW_SECONDS) {
                    log.warn("Token 'iat' is in the future (iat: {}, now: {})", iat, now);
                    return null;
                }
            }

            String expectedIssuer = "https://securetoken.google.com/" + firebaseProjectId;
            String iss = payload.hasNonNull("iss") ? payload.get("iss").asText() : null;
            if (!expectedIssuer.equals(iss)) {
                log.warn("Invalid JWT issuer: {}, expected: {}", iss, expectedIssuer);
                return null;
            }

            String aud = payload.hasNonNull("aud") ? payload.get("aud").asText() : null;
            if (!firebaseProjectId.equals(aud)) {
                log.warn("Invalid JWT audience: {}, expected: {}", aud, firebaseProjectId);
                return null;
            }

            String sub = payload.hasNonNull("sub") ? payload.get("sub").asText() : null;
            if (sub == null || sub.isBlank()) {
                log.warn("JWT is missing 'sub' claim");
                return null;
            }

            String email = payload.hasNonNull("email") ? payload.get("email").asText() : null;
            String name = payload.hasNonNull("name") ? payload.get("name").asText() : null;

            if (email == null || email.isBlank()) {
                log.warn("JWT is missing 'email' claim for sub: {}", sub);
                return null;
            }

            if (!isEmailAllowed(email)) {
                log.warn("Access denied for email: {}. Allowed emails: {}", email, ALLOWED_EMAILS);
                throw new AuthException(HttpServletResponse.SC_FORBIDDEN,
                        "Access denied. Only authorized users " + ALLOWED_EMAILS + " are permitted.");
            }

            return getOrCreateUser(email, name);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to verify Firebase JWT token: {}", e.getMessage());
            return null;
        }
    }

    private boolean verifySignature(String headerPart, String payloadPart, String signaturePart, PublicKey publicKey) {
        try {
            byte[] signedContent = (headerPart + "." + payloadPart).getBytes(StandardCharsets.US_ASCII);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signaturePart);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signedContent);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.warn("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private PublicKey resolveGooglePublicKey(String kid) {
        Map<String, PublicKey> keys = this.cachedPublicKeys;
        if (Instant.now().isBefore(this.cacheExpiresAt) && keys.containsKey(kid)) {
            return keys.get(kid);
        }
        synchronized (this) {
            if (Instant.now().isBefore(this.cacheExpiresAt) && this.cachedPublicKeys.containsKey(kid)) {
                return this.cachedPublicKeys.get(kid);
            }
            refreshGooglePublicKeys();
            return this.cachedPublicKeys.get(kid);
        }
    }

    private void refreshGooglePublicKeys() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_CERTS_URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Failed to fetch Google public keys, HTTP status: {}", response.statusCode());
                return;
            }

            JsonNode certsNode = objectMapper.readTree(response.body());
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            Map<String, PublicKey> newKeys = new HashMap<>();

            certsNode.properties().forEach(entry -> {
                try {
                    String pem = entry.getValue().asText();
                    X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
                            new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
                    newKeys.put(entry.getKey(), cert.getPublicKey());
                } catch (Exception e) {
                    log.warn("Failed to parse X.509 certificate for kid {}: {}", entry.getKey(), e.getMessage());
                }
            });

            long maxAgeSeconds = response.headers()
                    .firstValue("Cache-Control")
                    .map(this::extractMaxAgeSeconds)
                    .orElse(DEFAULT_CACHE_SECONDS);

            this.cachedPublicKeys = Collections.unmodifiableMap(newKeys);
            this.cacheExpiresAt = Instant.now().plusSeconds(maxAgeSeconds);
        } catch (Exception e) {
            log.error("Error refreshing Google public keys: {}", e.getMessage());
        }
    }

    private long extractMaxAgeSeconds(String cacheControl) {
        Matcher matcher = MAX_AGE_PATTERN.matcher(cacheControl);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_CACHE_SECONDS;
    }

    private boolean isEmailAllowed(String email) {
        if (email == null) {
            return false;
        }
        String trimmed = email.trim();
        return ALLOWED_EMAILS.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(trimmed));
    }

    private FitnessUser getOrCreateUser(String email, String name) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    try {
                        FitnessUser newUser = new FitnessUser();
                        newUser.setEmail(email);
                        String username = (name != null && !name.isBlank()) ? name : email.split("@")[0];
                        newUser.setUsername(username);
                        newUser.setDefaultRestTimerSeconds(90);
                        return userRepository.save(newUser);
                    } catch (Exception e) {
                        return userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("Failed to get or create user: " + email, e));
                    }
                });
    }

    private static class AuthException extends Exception {
        private final int statusCode;

        public AuthException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
