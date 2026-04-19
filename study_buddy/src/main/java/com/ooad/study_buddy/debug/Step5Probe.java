package com.ooad.study_buddy.debug;

import com.ooad.study_buddy.browser.InMemorySiteMetadataRepository;
import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.relevance.*;
import com.ooad.study_buddy.service.*;

public class Step5Probe {
    public static void main(String[] args) throws Exception {
        BlockingService blocking = new BlockingService(new InMemorySiteMetadataRepository());
        blocking.whitelist("wikipedia.org", "test");
        RelevanceService relevance = new RelevanceService();
        RelevanceChainFactory factory = new RelevanceChainFactory(blocking, relevance);
        RelevanceHandler chain = factory.buildChain();

        ContentData wiki = new ContentData("https://wikipedia.org/wiki/Java",
            "Java", null, null, null, null, "Java is a programming language.");
        ContentData insta = new ContentData("https://instagram.com/reel",
            "Instagram", null, null, null, null, "photos videos fun");

        System.out.println("wiki  : " + chain.handle("Java programming", wiki));
        System.out.println("insta : " + chain.handle("Java programming", insta));

        // Also test quickDecision
        var blockSvc = new BlockingService(new InMemorySiteMetadataRepository());
        System.out.println("yt/shorts : " + blockSvc.quickDecision("https://youtube.com/shorts/abc"));
        System.out.println("reddit /  : " + blockSvc.quickDecision("https://reddit.com/"));
    }
}