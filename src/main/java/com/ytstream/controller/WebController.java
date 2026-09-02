package com.ytstream.controller;

import com.ytstream.model.VideoDetail;
import com.ytstream.model.VideoItem;
import com.ytstream.service.YouTubeSearchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class WebController {

    private final YouTubeSearchService searchService;

    public WebController(YouTubeSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/")
    public String index(Model model) {
        // Fetch trending / popular music & videos for homepage showcase
        List<VideoItem> trending = searchService.search("trending music 2026");
        if (trending.isEmpty()) {
            trending = searchService.search("lofi beats");
        }
        model.addAttribute("trending", trending);
        return "index";
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String query, Model model) {
        if (query != null && !query.isBlank()) {
            List<VideoItem> results = searchService.search(query);
            model.addAttribute("query", query);
            model.addAttribute("results", results);
        } else {
            model.addAttribute("query", "");
            model.addAttribute("results", List.of());
        }
        return "search";
    }

    @GetMapping("/watch")
    public String watch(@RequestParam(name = "v") String videoId, Model model) {
        VideoDetail detail = searchService.getVideoDetails(videoId);
        model.addAttribute("video", detail);
        return "watch";
    }
}
