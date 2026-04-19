package com.ooad.study_buddy.debug;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.service.RelevanceService;

public class Step4Probe {
    public static void main(String[] args) throws Exception {
        RelevanceService svc = new RelevanceService();
        ContentData cd = new ContentData(
            "https://www.geeksforgeeks.org/dsa/binary-tree-data-structure/", "Binary Trees", null, null, null, null,
            "A binary search tree is a node-based data structure.");
        var result = svc.check("Binary Search Trees", cd);
        System.out.println(result);  // should show ALLOWED with score >= 0.65
    }
}