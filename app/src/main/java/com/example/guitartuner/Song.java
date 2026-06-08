package com.example.guitartuner;

public class Song {
    private String title;
    private String artist;
    private String lyrics;
    private String path;
    private boolean isFolder;

    public Song(String title, String artist, String lyrics, String path, boolean isFolder) {
        this.title = title;
        this.artist = artist;
        this.lyrics = lyrics;
        this.path = path;
        this.isFolder = isFolder;
    }

    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getLyrics() { return lyrics; }
    public String getPath() { return path; }
    public boolean isFolder() { return isFolder; }
}
