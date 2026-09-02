package com.ytstream.model;

import java.util.ArrayList;
import java.util.List;

public class VideoDetail {
    private String id;
    private String title;
    private String description;
    private String channel;
    private String channelThumbnail;
    private String duration;
    private String views;
    private String likes;
    private String thumbnail;
    private List<VideoItem> related = new ArrayList<>();

    public VideoDetail() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getChannelThumbnail() { return channelThumbnail; }
    public void setChannelThumbnail(String channelThumbnail) { this.channelThumbnail = channelThumbnail; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getViews() { return views; }
    public void setViews(String views) { this.views = views; }

    public String getLikes() { return likes; }
    public void setLikes(String likes) { this.likes = likes; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public List<VideoItem> getRelated() { return related; }
    public void setRelated(List<VideoItem> related) { this.related = related; }
}
