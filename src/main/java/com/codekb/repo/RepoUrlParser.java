package com.codekb.repo;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RepoUrlParser {

    private static final Pattern SCP_LIKE_PATTERN =
            Pattern.compile("^(?:[^@]+@)?([^:/]+):/?(.+?)/*$");

    private RepoUrlParser() {}

    public static RepoUrlParts parse(String rawUrl, String providerHint) {
        RepoProvider hinted = RepoProvider.fromNullable(providerHint);
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isBlank()) {
            return new RepoUrlParts(resolveProvider(null, hinted), null, null, null, "", null);
        }

        if (url.startsWith("upload://")) {
            String name = fallbackName(url.substring("upload://".length()), "uploaded-repo");
            return new RepoUrlParts(RepoProvider.ZIP, null, null, null, name, null);
        }
        if (url.startsWith("local://")) {
            String name = fallbackName(url.substring("local://".length()), "local-repo");
            return new RepoUrlParts(RepoProvider.LOCAL, null, null, null, name, null);
        }

        ParsedRemote parsed = parseRemote(url);
        RepoProvider provider = resolveProvider(parsed.host(), hinted);
        String cleanedPath = sanitizePath(parsed.path(), provider);
        if (cleanedPath == null || cleanedPath.isBlank()) {
            return new RepoUrlParts(provider, parsed.host(), null, null, fallbackName(url, "repo"), null);
        }

        String[] segments = Arrays.stream(cleanedPath.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (segments.length < 2) {
            return new RepoUrlParts(provider, parsed.host(), null, null,
                    fallbackName(cleanedPath, fallbackName(url, "repo")), null);
        }

        String repo = segments[segments.length - 1];
        String owner = Arrays.stream(segments, 0, segments.length - 1)
                .collect(Collectors.joining("/"));
        return new RepoUrlParts(provider, parsed.host(), owner, repo, repo, cleanedPath);
    }

    private static ParsedRemote parseRemote(String url) {
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("ssh://")) {
            URI uri = URI.create(url);
            return new ParsedRemote(
                    uri.getHost() == null ? null : uri.getHost().toLowerCase(),
                    uri.getPath() == null ? "" : uri.getPath()
            );
        }

        Matcher matcher = SCP_LIKE_PATTERN.matcher(url);
        if (matcher.matches()) {
            return new ParsedRemote(matcher.group(1).toLowerCase(), matcher.group(2));
        }

        return new ParsedRemote(null, url);
    }

    private static RepoProvider resolveProvider(String host, RepoProvider hinted) {
        if (hinted != RepoProvider.OTHER) {
            return hinted;
        }
        if (host == null || host.isBlank()) {
            return RepoProvider.OTHER;
        }
        String lowerHost = host.toLowerCase();
        if (lowerHost.contains("github.com")) return RepoProvider.GITHUB;
        if (lowerHost.contains("gitee.com")) return RepoProvider.GITEE;
        if (lowerHost.contains("gitlab")) return RepoProvider.GITLAB;
        return RepoProvider.OTHER;
    }

    private static String sanitizePath(String rawPath, RepoProvider provider) {
        if (rawPath == null) return "";
        String path = rawPath.trim();
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        path = path.replaceAll("(?i)\\.git$", "");

        int queryMarker = path.indexOf('?');
        if (queryMarker >= 0) path = path.substring(0, queryMarker);
        int fragmentMarker = path.indexOf('#');
        if (fragmentMarker >= 0) path = path.substring(0, fragmentMarker);

        if (provider == RepoProvider.GITLAB) {
            int idx = path.indexOf("/-/");
            if (idx >= 0) {
                path = path.substring(0, idx);
            }
        }

        String[] markers = {"/tree/", "/blob/", "/commit/", "/commits/", "/pull/", "/pulls/"};
        for (String marker : markers) {
            int idx = path.indexOf(marker);
            if (idx >= 0) {
                path = path.substring(0, idx);
                break;
            }
        }
        return path;
    }

    private static String fallbackName(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.replace('\\', '/');
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        int idx = normalized.lastIndexOf('/');
        String candidate = idx >= 0 ? normalized.substring(idx + 1) : normalized;
        return candidate.isBlank() ? fallback : candidate;
    }

    private record ParsedRemote(String host, String path) {}
}
