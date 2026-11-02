package com.winlator.cmod.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SteamNewsItem {
    final String title;
    final String url;
    final String published;
    final String summary;
    final String content;
    final String imageUrl;
    final List<Article> articles;

    SteamNewsItem(String title, String url, String published, String summary,
                  String content, String imageUrl) {
        this(title, url, published, summary, content, imageUrl,
                Collections.singletonList(new Article(url, published, content, imageUrl)));
    }

    private SteamNewsItem(String title, String url, String published, String summary,
                          String content, String imageUrl, List<Article> articles) {
        this.title = title;
        this.url = url;
        this.published = published;
        this.summary = summary;
        this.content = content;
        this.imageUrl = imageUrl;
        this.articles = Collections.unmodifiableList(new ArrayList<>(articles));
    }

    static SteamNewsItem group(List<SteamNewsItem> items) {
        SteamNewsItem newest = items.get(0);
        if (items.size() == 1) return newest;
        List<Article> articles = new ArrayList<>();
        for (SteamNewsItem item : items) articles.addAll(item.articles);
        String summary = items.size() + " updates · Latest: " + newest.summary;
        return new SteamNewsItem(newest.title, newest.url, newest.published, summary,
                newest.content, newest.imageUrl, articles);
    }

    static final class Article {
        final String url;
        final String published;
        final String content;
        final String imageUrl;

        Article(String url, String published, String content, String imageUrl) {
            this.url = url;
            this.published = published;
            this.content = content;
            this.imageUrl = imageUrl;
        }
    }
}
