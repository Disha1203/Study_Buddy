package com.ooad.study_buddy.debug;

import com.ooad.study_buddy.browser.InMemorySiteMetadataRepository;
import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.relevance.*;
import com.ooad.study_buddy.service.*;

public class Step5Probe {

    public static void main(String[] args) throws Exception {

        System.out.println("========== STEP 1: BlockingService ONLY ==========");

        BlockingService blocking = new BlockingService(new InMemorySiteMetadataRepository());

        test(blocking, "https://www.youtube.com/");
        test(blocking, "https://www.youtube.com/watch?v=abc");
        test(blocking, "https://www.youtube.com/results?search_query=ninja");
        test(blocking, "https://www.youtube.com/shorts/xyz");

        test(blocking, "https://reddit.com/");
        test(blocking, "https://reddit.com/r/java/comments/123");

        System.out.println("\n========== STEP 2: DEBUG DOMAIN + PATH ==========");

        debugUrl(blocking, "https://www.youtube.com/results?search_query=ninja");

        System.out.println("\n========== STEP 3: FULL RELEVANCE CHAIN ==========");

        blocking.whitelist("wikipedia.org", "test");

        RelevanceService relevance = new RelevanceService();
        RelevanceChainFactory factory = new RelevanceChainFactory(blocking, relevance);
        RelevanceHandler chain = factory.buildChain();

        ContentData wiki = new ContentData(
                "https://wikipedia.org/wiki/Java",
                "Java",
                null, null, null, null,
                "Java is a programming language.");

        ContentData youtubeSearch = new ContentData(
                "https://www.youtube.com/results?search_query=ninja",
                "Ninja gameplay",
                null, null, null, null,
                "fun gaming video streamer");

        System.out.println("wiki result      : " + chain.handle("Java programming", wiki));
        System.out.println("youtube result   : " + chain.handle("Java programming", youtubeSearch));

        System.out.println("\n========== DONE ==========");
    }

    private static void test(BlockingService service, String url) {
        var decision = service.quickDecision(url);
        System.out.println(url + "  -->  " + decision);
    }

    private static void debugUrl(BlockingService service, String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);

            String domain = uri.getHost();
            String path = uri.getPath();

            System.out.println("URL      : " + url);
            System.out.println("DOMAIN   : " + domain);
            System.out.println("PATH     : " + path);
            System.out.println("EXPECTED : /results");

            var decision = service.quickDecision(url);
            System.out.println("DECISION : " + decision);

        } catch (Exception e) {
            System.out.println("ERROR parsing URL: " + e.getMessage());
        }
    }
}