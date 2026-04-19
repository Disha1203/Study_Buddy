package com.ooad.study_buddy.debug;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.relevance.URLCheckHandler;

public class Step2Probe {

    public static void main(String[] args) {

        URLCheckHandler handler = new URLCheckHandler();

        String[][] cases = {

            // ───── BLOCK: Distraction URLs ─────
            {"algorithms", "https://instagram.com/feed"},
            {"sorting", "https://www.tiktok.com/@funnyvideos"},
            {"ai", "https://twitter.com/home"},
            {"malware", "https://reddit.com/r/memes"},
            {"networks", "https://9gag.com/gag/abc123"},
            {"crypto", "https://casino.com/blackjack"},
            {"java", "https://netflix.com/watch/123"},

            // ───── ALLOW: Strong relevance (keyword match) ─────
            {"algorithms", "https://geeksforgeeks.org/dijkstra-algorithm"},
            {"binary search", "https://leetcode.com/problems/binary-search"},
            {"machine learning", "https://towardsdatascience.com/machine-learning-basics"},
            {"sorting", "https://wikipedia.org/wiki/Sorting_algorithm"},
            {"graphs", "https://cp-algorithms.com/graph/dijkstra.html"},

            // ───── PASS (Neutral → should go to next handler) ─────
            {"algorithms", "https://example.com/home"},
            {"ai", "https://randomblogsite.com/article123"},
            {"network security", "https://medium.com/some-article"},
            {"data structures", "https://myblog.dev/post1"},

            // ───── EDGE CASES ─────

            // distraction + keyword (should BLOCK — priority check)
            {"malware", "https://instagram.com/malware_analysis"},
            {"algorithms", "https://twitter.com/algorithms_daily"},

            // partial keyword (should NOT falsely match)
            {"java", "https://javascript.info"},
            {"net", "https://internet.com"},

            // keyword too short (ignored by your >=4 rule)
            {"ai", "https://ai.com"},   // should NOT auto-allow

            // tricky real-world URLs
            {"sorting", "https://youtube.com/watch?v=sorting_tutorial"},
            {"graphs", "https://reddit.com/r/graphs/comments/xyz"},

            // uppercase / mixed case
            {"Binary Search", "https://LeetCode.com/problems/Binary-Search"},

            // no URL (null safety)
            {"algorithms", null}
        };

        System.out.println("===== STEP 2 URL CHECK TESTS =====\n");

        for (String[] c : cases) {
            String topic = c[0];
            String url = c[1];

            ContentData cd = new ContentData(
                    url,
                    null, null, null, null, null, null
            );

            var result = handler.handle(topic, cd);

            System.out.printf(
                    "topic=%-18s url=%-55s verdict=%-10s reason=%s%n",
                    topic,
                    url,
                    result.getVerdict(),
                    result.getReason()
            );
        }
    }
}