package com.codekb.repo;

import com.codekb.common.BusinessException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class LocalRepoZipService {

    private static final long MAX_TOTAL_BYTES = 200L * 1024L * 1024L;
    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            ".git",
            ".idea",
            ".codex-runtime",
            "node_modules",
            "dist",
            "build",
            "target",
            "out",
            ".next",
            ".nuxt",
            ".turbo",
            ".cache",
            ".gradle"
    );
    private static final Set<String> SKIP_FILE_NAMES = Set.of(
            ".DS_Store"
    );

    public LocalRepoDescriptor describe(String rawPath, String requestedName) {
        Path root = resolveRoot(rawPath);
        String repoName = deriveRepoName(root, requestedName);
        return new LocalRepoDescriptor(root, repoName);
    }

    public LocalRepoArchive archive(String rawPath, String requestedName) {
        LocalRepoDescriptor descriptor = describe(rawPath, requestedName);
        Path root = descriptor.root();
        String repoName = descriptor.repoName();
        String filename = repoName + ".zip";

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                ArchiveState state = new ArchiveState();
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!root.equals(dir) && shouldSkipDirectory(dir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (!attrs.isRegularFile() || shouldSkipFile(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        Path rel = root.relativize(file);
                        String entryName = rel.toString().replace('\\', '/');
                        if (entryName.isBlank()) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (!state.entries.add(entryName)) {
                            return FileVisitResult.CONTINUE;
                        }

                        state.totalBytes += attrs.size();
                        if (state.totalBytes > MAX_TOTAL_BYTES) {
                            throw new BusinessException(400, "本地目录过大，压缩前文件总大小超过 200 MB");
                        }

                        ZipEntry entry = new ZipEntry(entryName);
                        entry.setTime(attrs.lastModifiedTime().toMillis());
                        zip.putNextEntry(entry);
                        try (InputStream in = Files.newInputStream(file)) {
                            in.transferTo(zip);
                        }
                        zip.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) {
                throw new BusinessException(400, "本地目录中没有可上传的代码文件");
            }
            return new LocalRepoArchive(root, repoName, filename, bytes);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(500, "打包本地目录失败: " + e.getMessage(), e);
        }
    }

    private Path resolveRoot(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new BusinessException(400, "本地目录路径不能为空");
        }
        try {
            Path path = Path.of(rawPath.trim()).normalize().toAbsolutePath();
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new BusinessException(400, "本地目录不存在: " + path);
            }
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new BusinessException(400, "本地路径不是目录: " + path);
            }
            if (!Files.isReadable(path)) {
                throw new BusinessException(400, "本地目录不可读: " + path);
            }
            return path;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "本地目录路径无效: " + rawPath, e);
        }
    }

    private String deriveRepoName(Path root, String requestedName) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        Path fileName = root.getFileName();
        if (fileName == null) {
            throw new BusinessException(400, "无法从本地目录推断仓库名: " + root);
        }
        String name = fileName.toString().trim();
        if (name.isBlank()) {
            throw new BusinessException(400, "无法从本地目录推断仓库名: " + root);
        }
        return name;
    }

    private boolean shouldSkipDirectory(Path dir) {
        Path fileName = dir.getFileName();
        if (fileName == null) {
            return false;
        }
        return SKIP_DIR_NAMES.contains(fileName.toString());
    }

    private boolean shouldSkipFile(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return false;
        }
        return SKIP_FILE_NAMES.contains(fileName.toString());
    }

    private static final class ArchiveState {
        private final Set<String> entries = new HashSet<>();
        private long totalBytes;
    }

    public record LocalRepoDescriptor(Path root, String repoName) {}

    public record LocalRepoArchive(Path root, String repoName, String filename, byte[] bytes) {}
}
