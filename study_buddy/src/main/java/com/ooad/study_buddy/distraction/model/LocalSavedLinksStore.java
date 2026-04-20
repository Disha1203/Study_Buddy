package com.ooad.study_buddy.distraction.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalSavedLinksStore {

    private static final LocalSavedLinksStore instance = new LocalSavedLinksStore();

    private final List<String> links = new ArrayList<>();

    private LocalSavedLinksStore() {}

    public static LocalSavedLinksStore getInstance() {
        return instance;
    }

    public void addLink(String url) {
        if (url != null && !url.isBlank() && !links.contains(url)) {
            links.add(url);
        }
    }

    public List<String> getLinks() {
        return Collections.unmodifiableList(links);
    }

    public void clear() {
        links.clear();
    }
}