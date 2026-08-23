package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.AuthSessionResponse;
import com.ravan.SpringBootLab.model.RefreshToken;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);

        service = new RefreshTokenService(
                repository,
                30
        );

        user = mock(User.class);

        // User ID is Integer in this project.
        when(user.getId()).thenReturn(1);
    }

    @Test
    void issueWithDefaultsCreatesRefreshToken() {
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = service.issue(user);

        assertThat(rawToken).isNotBlank();

        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void issueWithDeviceAndIpNormalizesValues() {
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = service.issue(
                user,
                "  Chrome Browser  ",
                " 127.0.0.1 "
        );

        assertThat(rawToken).isNotBlank();

        verify(repository).save(argThat(token ->
                "Chrome Browser".equals(token.getDeviceName())
                        && "127.0.0.1".equals(token.getIpAddress())
        ));
    }

    @Test
    void issueWithBlankDeviceAndIpUsesDefaults() {
        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.issue(
                user,
                "   ",
                "   "
        );

        verify(repository).save(argThat(token ->
                "Unknown device".equals(token.getDeviceName())
                        && token.getIpAddress() == null
        ));
    }

    @Test
    void rotateValidTokenCreatesReplacementAndRevokesOldToken() {
        RefreshToken currentToken = mock(RefreshToken.class);

        UUID sessionId = UUID.randomUUID();

        when(currentToken.isActive(any(Instant.class)))
                .thenReturn(true);

        when(currentToken.getUser())
                .thenReturn(user);

        when(currentToken.getSessionId())
                .thenReturn(sessionId);

        when(currentToken.getDeviceName())
                .thenReturn("Old Device");

        when(currentToken.getIpAddress())
                .thenReturn("10.0.0.1");

        when(repository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(currentToken));

        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RefreshTokenRotation rotation =
                service.rotate(
                        "old-token",
                        "New Device",
                        "10.0.0.2"
                );

        assertThat(rotation.user())
                .isSameAs(user);

        assertThat(rotation.refreshToken())
                .isNotBlank();

        verify(currentToken).markUsed();

        verify(currentToken).revoke(any(UUID.class));

        verify(repository, times(2))
                .save(any(RefreshToken.class));
    }

    @Test
    void rotateWithoutDeviceAndIpKeepsExistingMetadata() {
        RefreshToken currentToken = mock(RefreshToken.class);

        UUID sessionId = UUID.randomUUID();

        when(currentToken.isActive(any(Instant.class)))
                .thenReturn(true);

        when(currentToken.getUser())
                .thenReturn(user);

        when(currentToken.getSessionId())
                .thenReturn(sessionId);

        when(currentToken.getDeviceName())
                .thenReturn("Existing Device");

        when(currentToken.getIpAddress())
                .thenReturn("192.168.1.10");

        when(repository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(currentToken));

        when(repository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RefreshTokenRotation rotation =
                service.rotate(
                        "token",
                        "   ",
                        "   "
                );

        assertThat(rotation.refreshToken())
                .isNotBlank();

        verify(repository, times(2)).save(argThat(token ->
                "Existing Device".equals(token.getDeviceName())
                        && "192.168.1.10".equals(token.getIpAddress())
        ));
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(repository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.rotate("invalid")
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(
                        "Invalid or expired refresh token"
                );

        verify(repository, never())
                .save(any());
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshToken token = mock(RefreshToken.class);

        when(repository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(token));

        when(token.isActive(any(Instant.class)))
                .thenReturn(false);

        assertThatThrownBy(() ->
                service.rotate("expired")
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(
                        "Invalid or expired refresh token"
                );

        verify(repository, never())
                .save(any());
    }

    @Test
    void revokeActiveTokenRevokesIt() {
        RefreshToken token = mock(RefreshToken.class);

        when(repository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(token));

        when(token.getUser())
                .thenReturn(user);

        when(token.getRevokedAt())
                .thenReturn(null);

        User result = service.revoke("token");

        assertThat(result)
                .isSameAs(user);

        verify(token)
                .revoke(null);

        verify(repository)
                .save(token);
    }

    @Test
    void revokeAlreadyRevokedTokenDoesNotSaveAgain() {
        RefreshToken token = mock(RefreshToken.class);

        when(repository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(token));

        when(token.getUser())
                .thenReturn(user);

        when(token.getRevokedAt())
                .thenReturn(Instant.now());

        User result = service.revoke("token");

        assertThat(result)
                .isSameAs(user);

        verify(token, never())
                .revoke(any());

        verify(repository, never())
                .save(any());
    }

    @Test
    void revokeRejectsUnknownToken() {
        when(repository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.revoke("invalid")
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(
                        "Invalid or expired refresh token"
                );

        verify(repository, never())
                .save(any());
    }

    @Test
    void listActiveSessionsMapsTokens() {
        RefreshToken token = mock(RefreshToken.class);

        UUID sessionId = UUID.randomUUID();

        Instant created =
                Instant.now().minusSeconds(100);

        Instant lastUsed =
                Instant.now().minusSeconds(50);

        Instant expires =
                Instant.now().plusSeconds(1000);

        when(token.getSessionId())
                .thenReturn(sessionId);

        when(token.getDeviceName())
                .thenReturn("Chrome");

        when(token.getIpAddress())
                .thenReturn("127.0.0.1");

        when(token.getCreatedAt())
                .thenReturn(created);

        when(token.getLastUsedAt())
                .thenReturn(lastUsed);

        when(token.getExpiresAt())
                .thenReturn(expires);

        when(repository.findActiveByUserId(1))
                .thenReturn(List.of(token));

        List<AuthSessionResponse> sessions =
                service.listActiveSessions(user);

        assertThat(sessions)
                .hasSize(1);

        AuthSessionResponse session =
                sessions.get(0);

        assertThat(session.sessionId())
                .isEqualTo(sessionId);

        assertThat(session.deviceName())
                .isEqualTo("Chrome");

        assertThat(session.ipAddress())
                .isEqualTo("127.0.0.1");

        assertThat(session.createdAt())
                .isEqualTo(created);

        assertThat(session.lastUsedAt())
                .isEqualTo(lastUsed);

        assertThat(session.expiresAt())
                .isEqualTo(expires);
    }

    @Test
    void listActiveSessionsReturnsEmptyListWhenNoSessionsExist() {
        when(repository.findActiveByUserId(1))
                .thenReturn(List.of());

        List<AuthSessionResponse> sessions =
                service.listActiveSessions(user);

        assertThat(sessions)
                .isEmpty();
    }

    @Test
    void revokeSessionSucceedsWhenSessionExists() {
        UUID sessionId = UUID.randomUUID();

        when(repository.revokeActiveSession(
                eq(1),
                eq(sessionId),
                any(Instant.class)
        ))
                .thenReturn(1);

        service.revokeSession(
                user,
                sessionId
        );

        verify(repository)
                .revokeActiveSession(
                        eq(1),
                        eq(sessionId),
                        any(Instant.class)
                );
    }

    @Test
    void revokeSessionThrowsWhenSessionDoesNotExist() {
        UUID sessionId = UUID.randomUUID();

        when(repository.revokeActiveSession(
                eq(1),
                eq(sessionId),
                any(Instant.class)
        ))
                .thenReturn(0);

        assertThatThrownBy(() ->
                service.revokeSession(
                        user,
                        sessionId
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(
                        "Active session not found"
                );
    }

    @Test
    void revokeAllSessionsReturnsRepositoryCount() {
        when(repository.revokeAllActiveSessions(
                eq(1),
                any(Instant.class)
        ))
                .thenReturn(3);

        int revoked =
                service.revokeAllSessions(user);

        assertThat(revoked)
                .isEqualTo(3);

        verify(repository)
                .revokeAllActiveSessions(
                        eq(1),
                        any(Instant.class)
                );
    }

    @Test
    void revokeAllSessionsReturnsZeroWhenNothingWasRevoked() {
        when(repository.revokeAllActiveSessions(
                eq(1),
                any(Instant.class)
        ))
                .thenReturn(0);

        int revoked =
                service.revokeAllSessions(user);

        assertThat(revoked)
                .isZero();
    }
}