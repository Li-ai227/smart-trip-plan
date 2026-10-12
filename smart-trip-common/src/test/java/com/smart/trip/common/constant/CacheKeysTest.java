package com.smart.trip.common.constant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CacheKeys} 与 {@link CacheConstants} 的行为测试（C-003）。
 *
 * <p>校验对齐 数据模型.md §4 的键格式（Happy Path）、统一前缀与 TTL 约定，
 * 以及空白入参的防御式校验（边界情况）。</p>
 */
class CacheKeysTest {

    @Nested
    @DisplayName("键格式对齐 数据模型.md §4")
    class KeyFormat {

        @Test
        void planKeyMatchesSchema() {
            assertEquals("trip:plan:P-20260607-001", CacheKeys.planKey("P-20260607-001"));
        }

        @Test
        void planStatusKeyMatchesSchema() {
            assertEquals("trip:plan:P-1:status", CacheKeys.planStatusKey("P-1"));
        }

        @Test
        void sessionKeyMatchesSchema() {
            assertEquals("trip:session:S-1", CacheKeys.sessionKey("S-1"));
        }

        @Test
        void stepKeyMatchesSchema() {
            assertEquals("trip:step:P-1", CacheKeys.stepKey("P-1"));
        }

        @Test
        void prefKeyMatchesSchema() {
            assertEquals("trip:pref:U-1", CacheKeys.prefKey("U-1"));
        }

        @Test
        void xhsNoteKeyMatchesSchema() {
            assertEquals("trip:xhs:note:N-1", CacheKeys.xhsNoteKey("N-1"));
        }

        @Test
        void rateLimitKeyMatchesSchema() {
            assertEquals("trip:ratelimit:xhs:60s", CacheKeys.rateLimitKey("xhs", "60s"));
        }

        @Test
        void agentResultKeyMatchesSchema() {
            assertEquals("trip:agent:result:P-1:route", CacheKeys.agentResultKey("P-1", "route"));
        }

        @Test
        void allKeysCarryUnifiedPrefix() {
            assertTrue(CacheKeys.planKey("x").startsWith(CacheConstants.KEY_PREFIX));
            assertTrue(CacheKeys.sessionKey("x").startsWith(CacheConstants.KEY_PREFIX));
            assertTrue(CacheKeys.agentResultKey("x", "y").startsWith(CacheConstants.KEY_PREFIX));
        }

        @Test
        @DisplayName("planDraftKey = trip:plan:{planId}:draft（C-018）")
        void planDraftKeyMatchesSchema() {
            assertEquals("trip:plan:P-001:draft", CacheKeys.planDraftKey("P-001"));
        }
    }

    @Nested
    @DisplayName("TTL 约定对齐 ADR-006")
    class TtlContract {

        @Test
        void defaultTtlIs24Hours() {
            assertEquals(24L, CacheConstants.DEFAULT_TTL_HOURS);
            assertEquals(Duration.ofHours(24), CacheConstants.DEFAULT_TTL);
        }
    }

    @Nested
    @DisplayName("边界：空白入参 fail-fast")
    class BlankArgGuard {

        @Test
        void nullPlanIdRejected() {
            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> CacheKeys.planKey(null));
            assertTrue(ex.getMessage().contains("planId"));
        }

        @Test
        void blankPlanIdRejected() {
            assertThrows(IllegalArgumentException.class, () -> CacheKeys.planKey("   "));
        }

        @Test
        void blankRateLimitWindowRejected() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> CacheKeys.rateLimitKey("xhs", ""));
            assertTrue(ex.getMessage().contains("window"));
        }

        @Test
        void blankAgentRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> CacheKeys.agentResultKey("P-1", null));
        }
    }
}
