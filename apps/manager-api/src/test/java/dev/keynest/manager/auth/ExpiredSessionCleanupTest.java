package dev.keynest.manager.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import dev.keynest.manager.config.SessionCleanupProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ExpiredSessionCleanupTest {
  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void usesOneCutoffAndBoundedFirstPageWithoutLoadingDigests() {
    var properties = new SessionCleanupProperties();
    properties.setBatchSize(2);
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(sessions.findExpiredIds(NOW, PageRequest.of(0, 2))).thenReturn(ids);
    when(sessions.deleteExpiredIds(ids, NOW)).thenReturn(1);
    assertThat(new ExpiredSessionCleanup(sessions, clock, properties).cleanBatch()).isEqualTo(1);
    verify(sessions).findExpiredIds(NOW, PageRequest.of(0, 2));
    verify(sessions).deleteExpiredIds(ids, NOW);
    verifyNoMoreInteractions(sessions);
  }

  @Test
  void emptyPageDoesNotIssueDeleteOrLoop() {
    when(sessions.findExpiredIds(NOW, PageRequest.of(0, 500))).thenReturn(List.of());
    assertThat(new ExpiredSessionCleanup(sessions, clock, new SessionCleanupProperties()).cleanBatch()).isZero();
    verify(sessions).findExpiredIds(NOW, PageRequest.of(0, 500));
    verifyNoMoreInteractions(sessions);
  }
}
