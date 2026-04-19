package com.ooad.study_buddy.focus.ui;

import com.ooad.study_buddy.focus.controller.SessionController;
import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.strategy.Dev1010Strategy;
import com.ooad.study_buddy.focus.strategy.Extended5010Strategy;
import com.ooad.study_buddy.focus.strategy.PomodoroStrategy;
import com.ooad.study_buddy.focus.strategy.Standard2505Strategy;
import com.ooad.study_buddy.model.Topic;
import com.ooad.study_buddy.service.TopicValidationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.util.function.Consumer;

/**
 * VIEW — Homepage / Session Setup
 *
 * SRP: Only responsible for rendering the homepage setup form.
 * SOLID DIP: Delegates session logic to SessionController.
 * Low Coupling: Communicates back to BrowserLauncher via a Consumer callback.
 */
public class HomepageView {

    private final SessionController      controller;
    private final Consumer<FocusSession> onSessionStart;
    private final TopicValidationService validator;

    private static final PomodoroStrategy[] STRATEGIES = {
            new Standard2505Strategy(),
            new Extended5010Strategy(),
            new Dev1010Strategy()       // DEV ONLY — remove before shipping
    };

    // ── Constructors ──────────────────────────────────────────────────────────

    public HomepageView(SessionController controller,
                        Consumer<FocusSession> onSessionStart,
                        TopicValidationService validator) {
        this.controller     = controller;
        this.onSessionStart = onSessionStart;
        this.validator      = validator;
    }

    public HomepageView(SessionController controller,
                        Consumer<FocusSession> onSessionStart) {
        this(controller, onSessionStart, new TopicValidationService());
    }

    // ── View builder ──────────────────────────────────────────────────────────

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0f0f0f;");
        VBox center = buildCenterCard();
        root.setCenter(center);
        BorderPane.setAlignment(center, Pos.CENTER);
        return root;
    }

    // ── Card ──────────────────────────────────────────────────────────────────

    private VBox buildCenterCard() {
        Label appTitle = new Label("Study Buddy");
        appTitle.setStyle(
                "-fx-font-size: 13px;" +
                "-fx-text-fill: #7C6EFA;" +
                "-fx-font-family: 'Segoe UI', sans-serif;" +
                "-fx-font-weight: bold;" +
                "-fx-letter-spacing: 3;");

        Label heading = new Label("Start a Focus Session");
        heading.setStyle(
                "-fx-font-size: 30px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        Label subheading = new Label("Set your topic, duration and Pomodoro mode.");
        subheading.setStyle(
                "-fx-font-size: 14px;" +
                "-fx-text-fill: #888888;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        subheading.setTextAlignment(TextAlignment.CENTER);

        VBox headerBox = new VBox(6, appTitle, heading, subheading);
        headerBox.setAlignment(Pos.CENTER);

        Label topicLabel     = fieldLabel("Session Topic");
        TextField topicField = styledTextField("e.g. Operating Systems — Chapter 4");

        Label durationLabel     = fieldLabel("Total Duration (minutes)");
        TextField durationField = styledTextField("e.g. 60");

        Label modeLabel       = fieldLabel("Pomodoro Mode");
        ToggleGroup modeGroup = new ToggleGroup();

        HBox modeRow = new HBox(10);
        modeRow.setAlignment(Pos.CENTER_LEFT);
        for (PomodoroStrategy s : STRATEGIES) {
            modeRow.getChildren().add(buildToggleButton(s.getLabel(), modeGroup));
        }
        ((ToggleButton) modeGroup.getToggles().get(0)).setSelected(true);

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(360);

        Button startBtn = new Button("Start Session →");
        startBtn.setPrefWidth(320);
        startBtn.setPrefHeight(48);
        startBtn.setStyle(
                "-fx-background-color: #7C6EFA;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 15px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 12;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        startBtn.setOnMouseEntered(e ->
                startBtn.setStyle(startBtn.getStyle().replace("#7C6EFA", "#9A8FFF")));
        startBtn.setOnMouseExited(e ->
                startBtn.setStyle(startBtn.getStyle().replace("#9A8FFF", "#7C6EFA")));
        startBtn.setOnAction(e ->
                handleStart(topicField, durationField, modeGroup, errorLabel));

        VBox form = new VBox(10,
                topicLabel, topicField,
                durationLabel, durationField,
                modeLabel, modeRow,
                errorLabel);
        form.setMaxWidth(380);

        VBox card = new VBox(28, headerBox, form, startBtn);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(440);
        card.setPadding(new Insets(48, 44, 48, 44));
        card.setStyle(
                "-fx-background-color: #161616;" +
                "-fx-background-radius: 20;" +
                "-fx-border-color: #2a2a2a;" +
                "-fx-border-radius: 20;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 32, 0.2, 0, 8);");

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleStart(TextField topicField, TextField durationField,
                             ToggleGroup modeGroup, Label errorLabel) {
        String rawTopic = topicField.getText().trim();

        Topic topic = validator.validate(rawTopic);
        if (!topic.isValid()) {
            errorLabel.setText("⚠ " + topic.getValidationError());
            return;
        }

        int duration;
        try {
            duration = Integer.parseInt(durationField.getText().trim());
            if (duration <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            errorLabel.setText("⚠ Please enter a valid duration in minutes.");
            return;
        }

        Toggle selected = modeGroup.getSelectedToggle();
        if (selected == null) {
            errorLabel.setText("⚠ Please select a Pomodoro mode.");
            return;
        }

        int strategyIndex  = modeGroup.getToggles().indexOf(selected);
        PomodoroStrategy strategy = STRATEGIES[strategyIndex];

        errorLabel.setText("");
        onSessionStart.accept(new FocusSession(topic.getRawText(), duration, strategy));
    }

    // ── Style helpers ─────────────────────────────────────────────────────────

    private Label fieldLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle(
                "-fx-font-size: 12px;" +
                "-fx-text-fill: #aaaaaa;" +
                "-fx-font-weight: bold;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        return lbl;
    }

    private TextField styledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefHeight(42);
        tf.setStyle(
                "-fx-background-color: #1e1e1e;" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: #2e2e2e;" +
                "-fx-border-radius: 10;" +
                "-fx-border-width: 1;" +
                "-fx-text-fill: white;" +
                "-fx-prompt-text-fill: #555555;" +
                "-fx-padding: 10 14;" +
                "-fx-font-size: 13px;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        return tf;
    }

    private ToggleButton buildToggleButton(String label, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(group);
        btn.setStyle(unselectedStyle());
        btn.selectedProperty().addListener(
                (obs, was, is) -> btn.setStyle(is ? selectedStyle() : unselectedStyle()));
        return btn;
    }

    private String selectedStyle() {
        return "-fx-background-color: #7C6EFA;" +
               "-fx-text-fill: white;" +
               "-fx-font-size: 12px;" +
               "-fx-font-weight: bold;" +
               "-fx-background-radius: 8;" +
               "-fx-cursor: hand;" +
               "-fx-padding: 8 16;" +
               "-fx-font-family: 'Segoe UI', sans-serif;";
    }

    private String unselectedStyle() {
        return "-fx-background-color: #1e1e1e;" +
               "-fx-text-fill: #aaaaaa;" +
               "-fx-font-size: 12px;" +
               "-fx-background-radius: 8;" +
               "-fx-border-color: #2e2e2e;" +
               "-fx-border-radius: 8;" +
               "-fx-border-width: 1;" +
               "-fx-cursor: hand;" +
               "-fx-padding: 8 16;" +
               "-fx-font-family: 'Segoe UI', sans-serif;";
    }
}