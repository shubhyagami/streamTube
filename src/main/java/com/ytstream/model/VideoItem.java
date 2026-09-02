package com.ytstream.model;

public class VideoItem {
    private String id;
    private String title;
    private String thumbnail;
    private String duration;
    private String channel;
    private String channelThumbnail;
    private String views;
    private String published;
    private String description;

    public VideoItem() {}

    public VideoItem(String id, String title, String thumbnail, String duration, 
                     String channel, String channelThumbnail, String views, 
                     String published, String description) {
        this.id = id;
        this.title = title;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.channel = channel;
        this.channelThumbnail = channelThumbnail;
        this.views = views;
        this.published = published;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getChannelThumbnail() { return channelThumbnail; }
    public void setChannelThumbnail(String channelThumbnail) { this.channelThumbnail = channelThumbnail; }

    public String getViews() { return views; }
    public void setViews(String views) { this.views = views; }

    public String getPublished() { return published; }
    public void setPublished(String published) { this.published = published; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
