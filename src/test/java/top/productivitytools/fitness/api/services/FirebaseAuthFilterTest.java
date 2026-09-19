package top.productivitytools.fitness.api.services;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import top.productivitytools.fitness.api.entities.FitnessUser;
import top.productivitytools.fitness.api.repositories.FitnessUserRepository;
import top.productivitytools.fitness.api.security.FirebaseAuthFilter;
import top.productivitytools.fitness.api.security.UserContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirebaseAuthFilterTest {

    private static final String PROJECT_ID = "ptprojectsweb";
    private static final String ISSUER = "https://securetoken.google.com/" + PROJECT_ID;
    private static final String KEY_ID = "test-kid-1";

    private static KeyPair validKeyPair;
    private static KeyPair attackerKeyPair;

    @Mock
    private FitnessUserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    private FirebaseAuthFilter filter;
    private ObjectMapper objectMapper;

    @BeforeAll
    static void initKeys() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        validKeyPair = keyGen.generateKeyPair();
        attackerKeyPair = keyGen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new FirebaseAuthFilter(
                userRepository,
                objectMapper,
                PROJECT_ID,
                kid -> KEY_ID.equals(kid) ? validKeyPair.getPublic() : null
        );
    }

    private String createSignedJwt(String email, String name, String sub, long exp, String iss, String aud, PrivateKey signingKey) {
        try {
            String headerJson = String.format("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"%s\"}", KEY_ID);
            long iat = Instant.now().getEpochSecond() - 60;
            String payloadJson = String.format(
                    "{\"email\":\"%s\",\"name\":\"%s\",\"sub\":\"%s\",\"iss\":\"%s\",\"aud\":\"%s\",\"iat\":%d,\"exp\":%d}",
                    email, name, sub, iss, aud, iat, exp
            );

            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(signingKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());

            return signingInput + "." + encodedSignature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void doFilter_WithAllowedUserToken_ResolvesAndSetsUserInContext() throws ServletException, IOException {
        long futureExp = Instant.now().getEpochSecond() + 3600;
        String token = createSignedJwt(
                "pwujczyk@gmail.com",
                "Pawel Wujczyk",
                "firebaseUid123",
                futureExp,
                ISSUER,
                PROJECT_ID,
                validKeyPair.getPrivate()
        );

        FitnessUser user = new FitnessUser();
        user.setId(1L);
        user.setEmail("pwujczyk@gmail.com");
        user.setUsername("Pawel Wujczyk");

        when(userRepository.findByEmail("pwujczyk@gmail.com")).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/workout/list");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<FitnessUser> capturedUserDuringFilter = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedUserDuringFilter.set(UserContext.getCurrentUser());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        assertNotNull(capturedUserDuringFilter.get());
        assertEquals("pwujczyk@gmail.com", capturedUserDuringFilter.get().getEmail());
        assertEquals(1L, capturedUserDuringFilter.get().getId());
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        assertNull(UserContext.getCurrentUser());
    }

    @Test
    void doFilter_WithGoogleUserToken_ResolvesAndSetsUserInContext() throws ServletException, IOException {
        long futureExp = Instant.now().getEpochSecond() + 3600;
        String token = createSignedJwt(
                "pwujczyk@google.com",
                "Pawel Wujczyk Google",
                "firebaseUidGoogle",
                futureExp,
                ISSUER,
                PROJECT_ID,
                validKeyPair.getPrivate()
        );

        FitnessUser user = new FitnessUser();
        user.setId(2L);
        user.setEmail("pwujczyk@google.com");
        user.setUsername("Pawel Wujczyk Google");

        when(userRepository.findByEmail("pwujczyk@google.com")).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/workout/list");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<FitnessUser> capturedUserDuringFilter = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedUserDuringFilter.set(UserContext.getCurrentUser());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        assertNotNull(capturedUserDuringFilter.get());
        assertEquals("pwujczyk@google.com", capturedUserDuringFilter.get().getEmail());
        assertEquals(2L, capturedUserDuringFilter.get().getId());
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        assertNull(UserContext.getCurrentUser());
    }

    @Test
    void doFilter_WithForgedSignature_Returns401Unauthorized() throws ServletException, IOException {
        long futureExp = Instant.now().getEpochSecond() + 3600;
        String forgedToken = createSignedJwt(
                "pwujczyk@gmail.com",
                "Pawel Wujczyk",
                "firebaseUid123",
                futureExp,
                ISSUER,
                PROJECT_ID,
                attackerKeyPair.getPrivate()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/workout/list");
        request.addHeader("Authorization", "Bearer " + forgedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void doFilter_WithPlaintextEmailFallback_Returns401Unauthorized() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/workout/list");
        request.addHeader("Authorization", "Bearer pwujczyk@gmail.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void doFilter_WithInvalidIssuerOrAudience_Returns401Unauthorized() throws ServletException, IOException {
        long futureExp = Instant.now().getEpochSecond() + 3600;
        String wrongAudToken = createSignedJwt(
                "pwujczyk@gmail.com",
                "Pawel Wujczyk",
                "firebaseUid123",
                futureExp,
                ISSUER,
                "some-other-project",
                validKeyPair.getPrivate()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/workout/list");
        request.addHeader("Authorization", "Bearer " + wrongAudToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_WithDisallowedUserToken_Returns403Forbidden() throws ServletException, IOException {
        long futureExp = Instant.now().getEpochSecond() + 3600;
        String token = createSignedJwt(
                "other.user@example.com",
                "Other User",
                "firebaseUid456",
                futureExp,
                ISSUER,
                PROJECT_ID,
                validKeyPair.getPrivate()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/workout/list");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getContentAsString().contains("Access denied. Only authorized users"));
        verify(filterChain, never()).doFilter(any(), any());
        verify(userRepository, never()).findByEmail(any());
        assertNull(UserContext.getCurrentUser());
    }

    @Test
    void doFilter_WithoutAuthorizationHeader_Returns401AndStopsChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/workout/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("Missing or invalid Authorization header"));
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(UserContext.getCurrentUser());
    }

    @Test
    void doFilter_WithExpiredToken_Returns401AndStopsChain() throws ServletException, IOException {
        long pastExp = Instant.now().getEpochSecond() - 3600;
        String token = createSignedJwt(
                "pwujczyk@gmail.com",
                "Pawel Wujczyk",
                "firebaseUid123",
                pastExp,
                ISSUER,
                PROJECT_ID,
                validKeyPair.getPrivate()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/workout/list");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid or expired authentication token"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_WithOptionsRequest_SkipsFilterAndContinuesChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("OPTIONS");
        request.setServletPath("/api/workout/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void doFilter_WithDebugEndpoint_SkipsFilterAndContinuesChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setServletPath("/api/debug/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }
}
