package com.ytstream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytstream.model.VideoDetail;
import com.ytstream.model.VideoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class YouTubeSearchService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeSearchService.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YouTubeSearchService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Search YouTube using internal Innertube API
     */
    public List<VideoItem> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            String requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB",
                            "clientVersion": "2.20240101.00.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "query": %s
                }
                """.formatted(objectMapper.writeValueAsString(query));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.youtube.com/youtubei/v1/search"))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseSearchResults(response.body());
            } else {
                log.warn("Search returned status: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("Error searching YouTube for query '{}': {}", query, e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Get autocomplete suggestions
     */
    public List<String> getSuggestions(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            String url = "https://suggestqueries-clients6.youtube.com/complete/search?client=firefox&ds=yt&q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.isArray() && root.size() > 1) {
                    JsonNode list = root.get(1);
                    List<String> suggestions = new ArrayList<>();
                    if (list.isArray()) {
                        for (JsonNode item : list) {
                            if (item.isTextual()) {
                                suggestions.add(item.asText());
                            }
                        }
                    }
                    return suggestions;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to fetch suggestions: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Get video metadata & related videos
     */
    public VideoDetail getVideoDetails(String videoId) {
        VideoDetail detail = new VideoDetail();
        detail.setId(videoId);

        try {
            // 1. Fetch player info (title, description, duration, views)
            String playerPayload = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB",
                            "clientVersion": "2.20240101.00.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "videoId": %s
                }
                """.formatted(objectMapper.writeValueAsString(videoId));

            HttpRequest playerReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.youtube.com/youtubei/v1/player"))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .POST(HttpRequest.BodyPublishers.ofString(playerPayload))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> playerRes = httpClient.send(playerReq, HttpResponse.BodyHandlers.ofString());
            if (playerRes.statusCode() == 200) {
                JsonNode pNode = objectMapper.readTree(playerRes.body());
                JsonNode vDetails = pNode.path("videoDetails");
                if (!vDetails.isMissingNode()) {
                    detail.setTitle(vDetails.path("title").asText("Untitled"));
                    detail.setDescription(vDetails.path("shortDescription").asText(""));
                    detail.setChannel(vDetails.path("author").asText("Unknown"));
                    detail.setDuration(formatDurationSeconds(vDetails.path("lengthSeconds").asLong(0)));
                    detail.setViews(formatViews(vDetails.path("viewCount").asText("0")));
                    
                    JsonNode thumbs = vDetails.path("thumbnail").path("thumbnails");
                    if (thumbs.isArray() && !thumbs.isEmpty()) {
                        detail.setThumbnail(thumbs.get(thumbs.size() - 1).path("url").asText(""));
                    }
                }
            }

            // 2. Fetch watch next info (related videos)
            HttpRequest nextReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.youtube.com/youtubei/v1/next"))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .POST(HttpRequest.BodyPublishers.ofString(playerPayload))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> nextRes = httpClient.send(nextReq, HttpResponse.BodyHandlers.ofString());
            if (nextRes.statusCode() == 200) {
                detail.setRelated(parseRelatedVideos(nextRes.body()));
            }

        } catch (Exception e) {
            log.error("Error retrieving video details for '{}': {}", videoId, e.getMessage());
            detail.setTitle("Video " + videoId);
        }

        return detail;
    }

    private List<VideoItem> parseSearchResults(String json) {
        List<VideoItem> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode contents = root.path("contents")
                    .path("twoColumnSearchResultsRenderer")
                    .path("primaryContents")
                    .path("sectionListRenderer")
                    .path("contents");

            if (!contents.isArray()) return results;

            for (JsonNode section : contents) {
                JsonNode items = section.path("itemSectionRenderer").path("contents");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        JsonNode vr = item.path("videoRenderer");
                        if (!vr.isMissingNode()) {
                            VideoItem v = extractVideoItem(vr);
                            if (v != null) {
                                results.add(v);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing search results: {}", e.getMessage());
        }
        return results;
    }

    private List<VideoItem> parseRelatedVideos(String json) {
        List<VideoItem> related = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode results = root.path("contents")
                    .path("twoColumnWatchNextResults")
                    .path("secondaryResults")
                    .path("secondaryResults")
                    .path("results");

            if (results.isArray()) {
                for (JsonNode item : results) {
                    JsonNode vr = item.path("compactVideoRenderer");
                    if (!vr.isMissingNode()) {
                        VideoItem v = extractCompactVideoItem(vr);
                        if (v != null) {
                            related.add(v);
                            if (related.size() >= 12) break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing related videos: {}", e.getMessage());
        }
        return related;
    }

    private VideoItem extractVideoItem(JsonNode vr) {
        String videoId = vr.path("videoId").asText(null);
        if (videoId == null || videoId.isBlank()) return null;

        String title = vr.path("title").path("runs").path(0).path("text").asText("Untitled");
        
        String thumbnail = "";
        JsonNode thumbs = vr.path("thumbnail").path("thumbnails");
        if (thumbs.isArray() && !thumbs.isEmpty()) {
            thumbnail = thumbs.get(thumbs.size() - 1).path("url").asText("");
        }

        String duration = vr.path("lengthText").path("simpleText").asText("");
        String channel = vr.path("ownerText").path("runs").path(0).path("text").asText("Unknown");
        
        String channelThumbnail = "";
        JsonNode chThumbs = vr.path("channelThumbnailSupportedRenderers")
                .path("channelThumbnailWithLinkRenderer")
                .path("thumbnail")
                .path("thumbnails");
        if (chThumbs.isArray() && !chThumbs.isEmpty()) {
            channelThumbnail = chThumbs.get(0).path("url").asText("");
        }

        String views = vr.path("viewCountText").path("simpleText").asText("");
        String published = vr.path("publishedTimeText").path("simpleText").asText("");
        String desc = vr.path("detailedMetadataSnippets").path(0).path("snippetText").path("runs").path(0).path("text").asText("");

        return new VideoItem(videoId, title, thumbnail, duration, channel, channelThumbnail, views, published, desc);
    }

    private VideoItem extractCompactVideoItem(JsonNode vr) {
        String videoId = vr.path("videoId").asText(null);
        if (videoId == null || videoId.isBlank()) return null;

        String title = vr.path("title").path("simpleText").asText(
                vr.path("title").path("runs").path(0).path("text").asText("Untitled"));

        String thumbnail = "";
        JsonNode thumbs = vr.path("thumbnail").path("thumbnails");
        if (thumbs.isArray() && !thumbs.isEmpty()) {
            thumbnail = thumbs.get(thumbs.size() - 1).path("url").asText("");
        }

        String duration = vr.path("lengthText").path("simpleText").asText("");
        String channel = vr.path("shortBylineText").path("runs").path(0).path("text").asText("");
        String views = vr.path("viewCountText").path("simpleText").asText("");

        return new VideoItem(videoId, title, thumbnail, duration, channel, "", views, "", "");
    }

    private String formatDurationSeconds(long seconds) {
        if (seconds <= 0) return "";
        long min = seconds / 60;
        long sec = seconds % 60;
        long hr = min / 60;
        min = min % 60;
        if (hr > 0) {
            return String.format("%d:%02d:%02d", hr, min, sec);
        }
        return String.format("%d:%02d", min, sec);
    }

    private String formatViews(String viewsStr) {
        try {
            long count = Long.parseLong(viewsStr.replaceAll("[^0-9]", ""));
            if (count >= 1_000_000_000) return String.format("%.1fB views", count / 1_000_000_000.0);
            if (count >= 1_000_000) return String.format("%.1fM views", count / 1_000_000.0);
            if (count >= 1_000) return String.format("%.1fK views", count / 1_000.0);
            return count + " views";
        } catch (Exception e) {
            return viewsStr;
        }
    }
}
