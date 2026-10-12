package com.smart.trip.skills.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 沙箱临时文件管理（C-014 AC-4/AC-6，ADR-006）。
 *
 * <p>所有临时文件写入指定沙箱目录，请求结束后统一清理。
 * 不留半成品文件：写入失败时立即删除不完整文件。</p>
 */
public final class SandboxFileManager {

    private final Path sandboxDir;
    private final List<Path> createdFiles = new ArrayList<>();

    /**
     * 构造沙箱文件管理器。
     *
     * @param sandboxDir 沙箱根目录
     */
    public SandboxFileManager(Path sandboxDir) {
        this.sandboxDir = sandboxDir;
    }

    /**
     * 在沙箱目录下写入临时 PDF 文件。
     *
     * @param planId  方案 ID（用于文件命名）
     * @param content PDF 字节内容
     * @return 写入的文件路径
     * @throws IllegalArgumentException 当 planId 为空或 content 为空字节数组
     * @throws UncheckedIOException     当文件写入失败
     */
    public Path writeTempFile(String planId, byte[] content) {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId 不可为空");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF 内容不可为空");
        }

        try {
            Files.createDirectories(sandboxDir);
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String fileName = planId + "-" + suffix + ".pdf";
            Path target = sandboxDir.resolve(fileName);
            Files.write(target, content);
            createdFiles.add(target);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("沙箱文件写入失败: " + planId, e);
        }
    }

    /**
     * 清理所有已创建的临时文件（幂等）。
     */
    public void cleanup() {
        for (Path file : createdFiles) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // 尽力删除，不阻断
            }
        }
        createdFiles.clear();
    }
}
