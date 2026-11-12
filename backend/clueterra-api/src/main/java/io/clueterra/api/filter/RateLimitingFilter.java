package io.clueterra.api.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.config.RateLimitingProperties;
import io.clueterra.api.exception.ApiErrorResponse;
import io.clueterra.api.security.ClientIpResolver;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(1)
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

  private static final String HEADER_NAME = "RateLimit-Remaining";
  private static final String HEADER_RESET = "RateLimit-Reset";
  private static final String HEADER_RETRY_AFTER = "Retry-After";
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String HASH_ALGORITHM = "SHA-256";
  private static final int OAUTH_CALLBACK_LIMIT = 10;
  private static final String GUEST_SESSION_CREATE_PATH = "/api/game/sessions/guest";
  private static final String BUDGET_GUEST_SESSION_CREATE_PATH = "/api/budget/sessions/guest";
  private static final String RIVER_GUEST_SESSION_CREATE_PATH = "/api/rivers/session/guest";
  private static final String SILHOUETTE_GUEST_SESSION_CREATE_PATH = "/api/silhouettes/session/guest";
  private static final String FLAG_GUEST_SESSION_CREATE_PATH = "/api/flag-game/session/guest";
  private static final String QUIZ_GUEST_SESSION_CREATE_PATH = "/api/quiz-game/session/guest";
  private static final String PROFILE_AVATAR_UPLOAD_PATH = "/api/profile/avatar";
  private static final String ASSET_PATH_PREFIX = "/api/assets";
  private static final String BUDGET_PATH_PREFIX = "/api/budget";
  private static final String SHARE_PATH_PREFIX = "/api/s";
  private static final String FRIEND_REQUEST_PREFIX = "/api/friends/requests/";
  private static final Set<String> AUTH_LOGIN_PATHS = Set.of(
          "/api/auth/login",
          "/api/auth/verify-totp");
  private static final Set<String> AUTH_REGISTER_PATHS = Set.of(
          "/api/auth/register",
          "/api/auth/oauth2/complete-registration");

  private final ObjectMapper objectMapper;
  private final RateLimitingProperties properties;
  private final ProxyManager<String> proxyManager;
  private final ClientIpResolver clientIpResolver;
  private final ConcurrentHashMap<String, Bucket> localFallbackBuckets = new ConcurrentHashMap<>();

  public RateLimitingFilter(
      ObjectMapper objectMapper,
      RateLimitingProperties properties,
      RedisConnectionFactory connectionFactory,
      ClientIpResolver clientIpResolver) {
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.clientIpResolver = clientIpResolver;

    ProxyManager<String> resolvedProxyManager = null;
    if (connectionFactory instanceof LettuceConnectionFactory lettuceFactory) {
      try {
        RedisClient redisClient = (RedisClient) lettuceFactory.getNativeClient();
        if (redisClient == null) {
          log.warn("RedisClient is not initialized in LettuceConnectionFactory; rate limiting will use in-memory fallback");
        } else {
          StatefulRedisConnection<String, byte[]> redisConnection = redisClient
                  .connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

          ClientSideConfig clientSideConfig = ClientSideConfig.getDefault()
                  .withExpirationAfterWriteStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)));

          resolvedProxyManager = LettuceBasedProxyManager.builderFor(redisConnection)
                  .withClientSideConfig(clientSideConfig)
                  .build();
        }
      } catch (Exception e) {
        log.warn("Failed to initialize Redis-based rate limiting; falling back to in-memory buckets: {}", e.getMessage());
      }
    } else {
      log.warn("Only LettuceConnectionFactory is supported for Redis rate limiting; falling back to in-memory buckets");
    }
    this.proxyManager = resolvedProxyManager;
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
    String path = request.getRequestURI();
    String method = request.getMethod();

    if (isWhitelisted(path)) {
      filterChain.doFilter(request, response);
      return;
    }

    String clientKey = getClientKey(request);
    String bucketType = getBucketType(path, method);
    String bucketKey = "rl:" + bucketType + ":" + clientKey;

    Bucket bucket = resolveBucket(path, method, bucketType, bucketKey);
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

    if (probe.isConsumed()) {
      response.addHeader(HEADER_NAME, String.valueOf(probe.getRemainingTokens()));
      filterChain.doFilter(request, response);
    } else {
      log.warn("Rate limit exceeded for bucket type {} on path {}", bucketType, path);
      writeErrorResponse(response, probe);
    }
  }

  private Bucket resolveBucket(String path, String method, String bucketType, String bucketKey) {
    if (proxyManager != null) {
      try {
        Supplier<BucketConfiguration> configurationSupplier = () -> createConfiguration(path, method);
        return proxyManager.builder().build(bucketKey, configurationSupplier);
      } catch (Exception e) {
        log.warn("Redis rate limiting unavailable for bucket type {}; using in-memory fallback: {}", bucketType, e.getMessage());
      }
    }
    return localFallbackBuckets.computeIfAbsent(bucketKey, k -> {
          int limit = getLimit(path, method);
          Bandwidth bandwidth = Bandwidth.builder()
                  .capacity(limit)
                  .refillGreedy(limit, Duration.ofMinutes(1))
                  .build();
          return Bucket.builder().addLimit(bandwidth).build();
        });
  }

  private boolean isWhitelisted(String path) {
    return path.startsWith("/api-docs") || path.startsWith("/swagger") || path.startsWith("/actuator") || path.equals("/api/health");
  }

  private void writeErrorResponse(HttpServletResponse response, ConsumptionProbe probe) throws IOException {
    long retryAfterSeconds = Math.max(1L, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader(HEADER_NAME, "0");
    response.setHeader(HEADER_RESET, String.valueOf(retryAfterSeconds));
    response.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfterSeconds));

    objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.ofRateLimit(retryAfterSeconds));
  }

  private String getClientKey(HttpServletRequest request) {
    String clientIp = clientIpResolver.resolveClientIp(request);
    return "ip:" + fingerprint(clientIp == null ? "unknown" : clientIp);
  }

  private String fingerprint(String value) {
    try {
      byte[] input = value.getBytes(StandardCharsets.UTF_8);
      String keySalt = properties.getKeySalt();
      if (keySalt != null && !keySalt.isBlank()) {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(keySalt.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return HexFormat.of().formatHex(mac.doFinal(input));
      }
      return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(input));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to fingerprint rate limit client key", e);
    }
  }

  private String getBucketType(String path, String method) {
    if (isGuestSessionCreatePath(path)) return "guest-session-create";
    if (path.startsWith(ASSET_PATH_PREFIX)) return "asset";
    if (path.startsWith(SHARE_PATH_PREFIX)) return "share";
    if (PROFILE_AVATAR_UPLOAD_PATH.equals(path) && "POST".equalsIgnoreCase(method))
      return "avatar-upload";
    if (path.startsWith(FRIEND_REQUEST_PREFIX) && "POST".equalsIgnoreCase(method)) {
      return "friend-request";
    }
    if (AUTH_LOGIN_PATHS.contains(path)) return "auth-login";
    if (AUTH_REGISTER_PATHS.contains(path)) return "auth-register";
    if (path.startsWith("/api/auth/oauth2/callback")) return "oauth-callback";
    if (path.startsWith("/api/auth")) return "auth";
    if (path.startsWith("/api/game")) return "game";
    if (path.startsWith("/api/rivers")) return "game";
    if (path.startsWith(BUDGET_PATH_PREFIX)) return "budget";
    if (path.startsWith("/api/admin")) return "admin";
    return "default";
  }

  private int getLimit(String path, String method) {
    if (isGuestSessionCreatePath(path)) return properties.getGuestSessionCreateRequestsPerMinute();
    if (path.startsWith(ASSET_PATH_PREFIX)) return properties.getAssetRequestsPerMinute();
    if (path.startsWith(SHARE_PATH_PREFIX)) return properties.getShareRequestsPerMinute();
    if (PROFILE_AVATAR_UPLOAD_PATH.equals(path) && "POST".equalsIgnoreCase(method)) {
      return properties.getAvatarUploadRequestsPerMinute();
    }
    if (path.startsWith(FRIEND_REQUEST_PREFIX) && "POST".equalsIgnoreCase(method)) {
      return properties.getFriendRequestsPerMinute();
    }
    if (AUTH_LOGIN_PATHS.contains(path)) return properties.getAuthLoginRequestsPerMinute();
    if (AUTH_REGISTER_PATHS.contains(path)) return properties.getAuthRegisterRequestsPerMinute();
    if (path.startsWith("/api/auth/oauth2/callback")) return OAUTH_CALLBACK_LIMIT;
    if (path.startsWith("/api/auth")) return properties.getAuthRequestsPerMinute();
    if (path.startsWith("/api/game")) return properties.getGameRequestsPerMinute();
    if (path.startsWith("/api/rivers")) return properties.getGameRequestsPerMinute();
    if (path.startsWith(BUDGET_PATH_PREFIX)) return properties.getBudgetRequestsPerMinute();
    if (path.startsWith("/api/admin")) return properties.getAdminRequestsPerMinute();
    return properties.getDefaultRequestsPerMinute();
  }

  private BucketConfiguration createConfiguration(String path, String method) {
    int requestsPerMinute = getLimit(path, method);

    Bandwidth limit = Bandwidth.builder()
            .capacity(requestsPerMinute)
            .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
            .build();

    return BucketConfiguration.builder().addLimit(limit).build();
  }

  private boolean isGuestSessionCreatePath(String path) {
    return GUEST_SESSION_CREATE_PATH.equals(path)
        || BUDGET_GUEST_SESSION_CREATE_PATH.equals(path)
        || RIVER_GUEST_SESSION_CREATE_PATH.equals(path)
        || SILHOUETTE_GUEST_SESSION_CREATE_PATH.equals(path)
        || FLAG_GUEST_SESSION_CREATE_PATH.equals(path)
        || QUIZ_GUEST_SESSION_CREATE_PATH.equals(path);
  }
}
