package com.ooad.study_buddy.debug;

import com.ooad.study_buddy.model.Topic;
import com.ooad.study_buddy.service.TopicValidationService;
import java.util.List;

public class Step1Probe {
    public static void main(String[] args) {
        TopicValidationService svc = new TopicValidationService();
        List.of("", "ai", "1234", "Binary Search Trees", "coding", "dkajhgaklsdg hfawadla", "malware", "gaming", "study on youtube")
            .forEach(input -> {
                Topic t = svc.validate(input);
                System.out.printf("input=%-30s valid=%-5b reason=%s%n",
                    "'" + input + "'", t.isValid(), t.getValidationError());
            });
    }
}