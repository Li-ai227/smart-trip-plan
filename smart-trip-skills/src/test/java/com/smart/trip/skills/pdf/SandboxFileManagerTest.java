package com.smart.trip.skills.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SandboxFileManager} 沙箱文件管理单测（C-014 T-1 AC-4/AC-6）。
 */
class SandboxFileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("writeTempFile 在沙箱目录下创建 PDF 文件")
    void writeTempFile_createsFileInSandboxDir() throws IOException {
        SandboxFileManager mgr = new SandboxFileManager(tempDir);
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF

        Path file = mgr.writeTempFile("P-20260701-001", content);

        assertNotNull(file);
        assertTrue(Files.exists(file));
        assertTrue(file.startsWith(tempDir));
        assertTrue(file.getFileName().toString().contains("P-20260701-001"));
        assertTrue(file.getFileName().toString().endsWith(".pdf"));
    }

    @Test
    @DisplayName("writeTempFile 在子目录不存在时自动创建")
    void writeTempFile_createsSubdirectoryIfMissing() throws IOException {
        Path nested = tempDir.resolve("agentscope").resolve("test-agent");
        SandboxFileManager mgr = new SandboxFileManager(nested);
        byte[] content = new byte[]{1, 2, 3};

        Path file = mgr.writeTempFile("P-001", content);

        assertTrue(Files.exists(nested));
        assertTrue(Files.exists(file));
    }

    @Test
    @DisplayName("cleanup 删除已创建的临时文件")
    void cleanup_removesCreatedFile() throws IOException {
        SandboxFileManager mgr = new SandboxFileManager(tempDir);
        byte[] content = new byte[]{1, 2, 3, 4};
        Path file = mgr.writeTempFile("P-002", content);

        assertTrue(Files.exists(file));
        mgr.cleanup();
        assertFalse(Files.exists(file));
    }

    @Test
    @DisplayName("cleanup 多次调用幂等，不抛异常")
    void cleanup_idempotent() throws IOException {
        SandboxFileManager mgr = new SandboxFileManager(tempDir);
        mgr.writeTempFile("P-003", new byte[]{1});

        assertDoesNotThrow(() -> {
            mgr.cleanup();
            mgr.cleanup();
        });
    }

    @Test
    @DisplayName("writeTempFile 空字节数组抛异常")
    void writeTempFile_emptyContentThrows() {
        SandboxFileManager mgr = new SandboxFileManager(tempDir);
        assertThrows(IllegalArgumentException.class,
                () -> mgr.writeTempFile("P-004", new byte[0]));
    }

    @Test
    @DisplayName("writeTempFile null planId 抛异常")
    void writeTempFile_nullPlanIdThrows() {
        SandboxFileManager mgr = new SandboxFileManager(tempDir);
        assertThrows(IllegalArgumentException.class,
                () -> mgr.writeTempFile(null, new byte[]{1}));
    }

    @Test
    @DisplayName("writeTempFile blank planId 抛异常")
    void writeTempFile_blankPlanIdThrows() {
        SandboxFileManager mgr = new SandboxFileManager(tempDir);
        assertThrows(IllegalArgumentException.class,
                () -> mgr.writeTempFile("   ", new byte[]{1}));
    }

    @Test
    @DisplayName("writeTempFile null content 抛异常")
    void writeTempFile_nullContentThrows() {
        SandboxFileManager mgr = new SandboxFileManager(tempDir);
        assertThrows(IllegalArgumentException.class,
                () -> mgr.writeTempFile("P-001", null));
    }

    @Test
    @DisplayName("边界: 同一 planId 两次写入生成不同文件名（UUID 后缀隔离）")
    void writeTempFile_samePlanId_differentFileNames() throws IOException {
        SandboxFileManager mgr = new SandboxFileManager(tempDir);
        byte[] content = new byte[]{1, 2, 3, 4};

        Path file1 = mgr.writeTempFile("P-001", content);
        Path file2 = mgr.writeTempFile("P-001", content);

        assertNotEquals(file1.getFileName().toString(), file2.getFileName().toString(),
                "同一 planId 两次写入应生成不同文件名");
        assertTrue(Files.exists(file1));
        assertTrue(Files.exists(file2));
    }

    @Test
    @DisplayName("回归: cleanup 后再次 writeTempFile 正常工作")
    void cleanup_afterCleanup_canWriteAgain() throws IOException {
        SandboxFileManager mgr = new SandboxFileManager(tempDir);
        mgr.writeTempFile("P-001", new byte[]{1});
        mgr.cleanup();

        Path file2 = mgr.writeTempFile("P-002", new byte[]{2});
        assertTrue(Files.exists(file2), "cleanup 后可再次写入");
    }
}
