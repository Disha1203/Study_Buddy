package com.ooad.study_buddy.browser;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class BrowserLauncher extends Application {

    @Override
    public void start(Stage stage) {

        BrowserView browser = new BrowserView();

        Scene scene = new Scene(browser.getView(), 1000, 600);

        stage.setTitle("Study Buddy - Browser");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}