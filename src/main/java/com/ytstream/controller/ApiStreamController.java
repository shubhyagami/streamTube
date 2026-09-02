package com.ytstream.controller;

import com.ytstream.model.VideoItem;
import com.ytstream.service.StreamProxyService;
import com.ytstream.service.YouTubeSearchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiStreamController {

    private final StreamProxyService streamProxyService;
    private final YouTubeSearchService searchService;

    public ApiStreamController(StreamProxyService streamProxyService, YouTubeSearchService searchService) {
        this.streamProxyService = streamProxyService;
        this.searchService = searchService;
    }

    /**
     * Stream chunk endpoint with HTTP Range support
     */
    @GetMapping("/stream/{id}")
    public void streamVideo(@PathVariable("id") String id, HttpServletRequest request, HttpServletResponse response) {
        streamProxyService.proxyStream(id, request, response);
    }

    /**
     * Search suggestions API
     */
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions(@RequestParam(name = "q", defaultValue = "") String query) {
        List<String> suggestions = searchService.getSuggestions(query);
        return ResponseEntity.ok(Map.of("suggestions", suggestions));
    }

    /**
     * Search API for JSON clients
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchVideos(@RequestParam(name = "q", defaultValue = "") String query) {
        List<VideoItem> results = searchService.search(query);
        return ResponseEntity.ok(Map.of("results", results));
    }
}
