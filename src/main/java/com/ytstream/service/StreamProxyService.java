package com.ytstream.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class StreamProxyService {

    private static final Logger log = LoggerFactory.getLogger(StreamProxyService.class);

    @Value("${ytdlp.path:}")
    private String configuredYtdlpPath;

    @Value("${ffmpeg.path:}")
    private String configuredFfmpegPath;

    private String resolvedYtdlp = null;
    private String resolvedFfmpeg = null;
    private boolean checkedFfmpeg = false;

    private static class CachedUrl {
        final String url;
        final long timestamp;

        CachedUrl(String url) {
            this.url = url;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            // 30 minutes TTL
            return (System.currentTimeMillis() - timestamp) > 30 * 60 * 1000L;
        }
    }

    private final ConcurrentHashMap<String, CachedUrl> urlCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> inFlightExtractions = new ConcurrentHashMap<>();

    /**
     * Dynamically resolves yt-dlp binary across Linux, Termux, and Windows
     */
    public synchronized String getYtDlpCommand() {
        if (resolvedYtdlp != null) return resolvedYtdlp;

        // 1. Explicit property check
        if (configuredYtdlpPath != null && !configuredYtdlpPath.isBlank()) {
            File f = new File(configuredYtdlpPath);
            if (f.exists()) {
                resolvedYtdlp = f.getAbsolutePath();
                log.info("Using configured yt-dlp path: {}", resolvedYtdlp);
                return resolvedYtdlp;
            }
        }

        // 2. Check system PATH (standard on Linux & Termux: pkg install yt-dlp or pip install yt-dlp)
        if (isExecutableAvailable("yt-dlp")) {
            resolvedYtdlp = "yt-dlp";
            log.info("Found yt-dlp in system PATH");
            return resolvedYtdlp;
        }
        if (isExecutableAvailable("yt-dlp.exe")) {
            resolvedYtdlp = "yt-dlp.exe";
            log.info("Found yt-dlp.exe in system PATH");
            return resolvedYtdlp;
        }

        // 3. Check local relative paths & developer directories
        String[] localCandidates = {
                "./yt-dlp",
                "./yt-dlp.exe",
                "./bin/yt-dlp",
                "./bin/yt-dlp.exe",
                "node_modules/youtube-dl-exec/bin/yt-dlp.exe",
                "D:/portfolio/yt stream/node_modules/youtube-dl-exec/bin/yt-dlp.exe"
        };
        for (String candidate : localCandidates) {
            File f = new File(candidate);
            if (f.exists()) {
                resolvedYtdlp = f.getAbsolutePath();
                log.info("Resolved local yt-dlp: {}", resolvedYtdlp);
                return resolvedYtdlp;
            }
        }

        // 4. Default fallback
        resolvedYtdlp = "yt-dlp";
        log.warn("yt-dlp not found locally; defaulting to 'yt-dlp' command from system PATH");
        return resolvedYtdlp;
    }

    /**
     * Dynamically resolves ffmpeg binary across Linux, Termux, and Windows
     */
    public synchronized String getFfmpegCommand() {
        if (checkedFfmpeg) return resolvedFfmpeg;
        checkedFfmpeg = true;

        // 1. Explicit property check
        if (configuredFfmpegPath != null && !configuredFfmpegPath.isBlank()) {
            File f = new File(configuredFfmpegPath);
            if (f.exists()) {
                resolvedFfmpeg = f.getAbsolutePath();
                log.info("Using configured ffmpeg path: {}", resolvedFfmpeg);
                return resolvedFfmpeg;
            }
        }

        // 2. Check system PATH (standard on Linux/Termux: pkg install ffmpeg / apt install ffmpeg)
        if (isExecutableAvailable("ffmpeg")) {
            resolvedFfmpeg = "ffmpeg";
            log.info("Found ffmpeg in system PATH");
            return resolvedFfmpeg;
        }

        // 3. Local relative paths & developer directories
        String[] localCandidates = {
                "./ffmpeg",
                "./ffmpeg.exe",
                "./bin/ffmpeg",
                "./bin/ffmpeg.exe",
                "node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe",
                "D:/portfolio/yt stream/node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe"
        };
        for (String candidate : localCandidates) {
            File f = new File(candidate);
            if (f.exists()) {
                resolvedFfmpeg = f.getAbsolutePath();
                log.info("Resolved local ffmpeg: {}", resolvedFfmpeg);
                return resolvedFfmpeg;
            }
        }

        log.info("ffmpeg not detected on system; progressive MP4 muxed streams will be used directly");
        return null;
    }

    private boolean isExecutableAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts direct GoogleVideo URL with caching & promise deduplication
     */
    public CompletableFuture<String> extractStreamUrl(String videoId) {
        CachedUrl cached = urlCache.get(videoId);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.url);
        }

        return inFlightExtractions.computeIfAbsent(videoId, id -> CompletableFuture.supplyAsync(() -> {
            try {
                String videoUrl = "https://www.youtube.com/watch?v=" + id;
                String ytdlp = getYtDlpCommand();
                String ffmpeg = getFfmpegCommand();

                List<String> command = new ArrayList<>();
                command.add(ytdlp);

                if (ffmpeg != null) {
                    command.add("--ffmpeg-location");
                    command.add(ffmpeg);
                }

                command.add("--extractor-args");
                command.add("youtube:player_client=android,ios,web");
                command.add("-f");
                command.add("22/18/b/best[ext=mp4]/best");
                command.add("-g");
                command.add("--no-warnings");
                command.add("--no-check-certificates");
                command.add("--no-playlist");
                command.add(videoUrl);

                log.info("Extracting stream URL for {} using [{}]", id, ytdlp);
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                String extractedUrl = null;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("http://") || line.startsWith("https://")) {
                            extractedUrl = line;
                            break;
                        }
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0 || extractedUrl == null) {
                    throw new RuntimeException("yt-dlp extraction failed (code " + exitCode + ")");
                }

                urlCache.put(id, new CachedUrl(extractedUrl));
                log.info("Successfully extracted stream URL for {}", id);
                return extractedUrl;
            } catch (Exception e) {
                log.error("Stream extraction error for {}: {}", id, e.getMessage());
                throw new RuntimeException("Could not extract stream URL: " + e.getMessage(), e);
            }
        })).whenComplete((res, err) -> inFlightExtractions.remove(videoId));
    }

    /**
     * Proxies media chunks supporting HTTP Range headers
     */
    public void proxyStream(String videoId, HttpServletRequest req, HttpServletResponse res) {
        try {
            String directUrl = extractStreamUrl(videoId).get();
            doProxyStream(videoId, directUrl, req, res, 0);
        } catch (Exception e) {
            log.error("Failed to proxy video {}: {}", videoId, e.getMessage());
            if (!res.isCommitted()) {
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private void doProxyStream(String videoId, String streamUrl, HttpServletRequest req, HttpServletResponse res, int redirectCount) {
        if (redirectCount > 5) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        HttpURLConnection connection = null;
        try {
            URL url = URI.create(streamUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);

            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Encoding", "identity");

            // Forward Range header if present
            String rangeHeader = req.getHeader("Range");
            if (rangeHeader != null && !rangeHeader.isBlank()) {
                connection.setRequestProperty("Range", rangeHeader);
            }

            int responseCode = connection.getResponseCode();

            // Handle redirect
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == 307 || responseCode == 308) {
                String location = connection.getHeaderField("Location");
                if (location != null) {
                    doProxyStream(videoId, location, req, res, redirectCount + 1);
                    return;
                }
            }

            // Handle 403 Forbidden (URL token expired)
            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                log.warn("Upstream 403 Forbidden for {}. Invalidating cached URL.", videoId);
                urlCache.remove(videoId);
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            res.setStatus(responseCode);

            // Copy response headers
            String contentType = connection.getHeaderField("Content-Type");
            res.setContentType(contentType != null ? contentType : "video/mp4");
            res.setHeader("Accept-Ranges", "bytes");
            res.setHeader("Access-Control-Allow-Origin", "*");
            res.setHeader("Access-Control-Allow-Headers", "Range");
            res.setHeader("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges");
            res.setHeader("Cache-Control", "no-cache");

            String contentLength = connection.getHeaderField("Content-Length");
            if (contentLength != null) {
                res.setHeader("Content-Length", contentLength);
            }

            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange != null) {
                res.setHeader("Content-Range", contentRange);
            }

            // Pipe stream chunks to client output
            try (InputStream in = connection.getInputStream();
                 OutputStream out = res.getOutputStream()) {
                byte[] buffer = new byte[65536]; // 64KB chunk buffer
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            } catch (Exception e) {
                // Client aborted connection (normal when scrubbing or pausing)
                log.debug("Stream piping closed by client: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Proxy streaming connection error for {}: {}", videoId, e.getMessage());
            if (!res.isCommitted()) {
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
