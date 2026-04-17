package com.ooad.study_buddy.controller;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.service.ContentExtractionService;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

/**
 * GRASP Controller: Owns the navigation-interception lifecycle for one WebView.
 *
 * SRP : Only wires WebEngine events to the extraction + relevance pipeline.
 * DIP : Depends on ContentExtractionService and RelevanceController abstractions.
 *
 * Usage:
 *   browserController.attach(webEngine, topic, (url, result) -> { ... });
 */
@Component
public class BrowserController {

    private final ContentExtractionService extractor;
    private final RelevanceController      relevanceController;

    public BrowserController(ContentExtractionService extractor,
                             RelevanceController relevanceController) {
        this.extractor           = extractor;
        this.relevanceController = relevanceController;
    }

    /**
     * Attaches a load-state listener to the engine.
     * After each page finishes loading the callback receives the verdict.
     *
     * @param engine        WebEngine to monitor
     * @param topic         current study topic
     * @param onResult      (url, result) callback — always called on FX thread
     */
    public void attach(WebEngine engine, String topic,
                       BiConsumer<String, RelevanceResult> onResult) {

        ChangeListener<Worker.State> listener = (obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                String url = engine.getLocation();
                ContentData data = extractor.extract(engine, url);
                RelevanceResult result = relevanceController.evaluate(topic, data);
                onResult.accept(url, result);
            }
        };

        engine.getLoadWorker().stateProperty().addListener(listener);
    }
}
