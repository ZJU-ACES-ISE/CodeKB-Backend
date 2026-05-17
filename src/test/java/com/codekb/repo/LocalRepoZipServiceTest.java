package com.codekb.repo;

import com.codekb.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class LocalRepoZipServiceTest {

    private final LocalRepoZipService service = new LocalRepoZipService();

    @TempDir
    Path tempDir;

    @Test
    void archivesRepositoryAndSkipsHeavyDirectories() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# demo", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src").resolve("main.ts"), "console.log('ok');", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("node_modules"));
        Files.writeString(tempDir.resolve("node_modules").resolve("skip.js"), "ignored", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve(".git").resolve("config"), "ignored", StandardCharsets.UTF_8);

        LocalRepoZipService.LocalRepoArchive archive = service.archive(tempDir.toString(), null);

        assertEquals(tempDir.getFileName().toString(), archive.repoName());
        assertEquals(archive.repoName() + ".zip", archive.filename());

        boolean readmeFound = false;
        boolean srcFound = false;
        boolean skippedNodeModules = true;
        boolean skippedGit = true;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive.bytes()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("README.md".equals(entry.getName())) {
                    readmeFound = true;
                }
                if ("src/main.ts".equals(entry.getName())) {
                    srcFound = true;
                }
                if (entry.getName().startsWith("node_modules/")) {
                    skippedNodeModules = false;
                }
                if (entry.getName().startsWith(".git/")) {
                    skippedGit = false;
                }
            }
        }

        assertTrue(readmeFound);
        assertTrue(srcFound);
        assertTrue(skippedNodeModules);
        assertTrue(skippedGit);
    }

    @Test
    void rejectsMissingDirectory() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.archive(tempDir.resolve("missing").toString(), null));
        assertEquals(400, ex.getCode());
    }
}
