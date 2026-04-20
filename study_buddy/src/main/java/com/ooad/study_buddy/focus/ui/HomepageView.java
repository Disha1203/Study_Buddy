package com.ooad.study_buddy.focus.ui;

// import com.apple.laf.AquaButtonBorder.Toggle;
import com.ooad.study_buddy.focus.controller.SessionController;
import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.strategy.Extended5010Strategy;
import com.ooad.study_buddy.focus.strategy.PomodoroStrategy;
import com.ooad.study_buddy.focus.strategy.Standard2505Strategy;
import com.ooad.study_buddy.model.SiteMetadata;
import com.ooad.study_buddy.model.Topic;
import com.ooad.study_buddy.service.BlockingService;
import com.ooad.study_buddy.service.TopicValidationService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.util.function.Consumer;

// import javax.swing.text.html.ListView;

/**
 * VIEW — Homepage / Session Setup
 *
 * ADDED: Integrated Site Rules Manager panel (whitelist + blacklist).
 * - Users can add/remove domains before starting a session.
 * - Changes are persisted to MySQL via BlockingService.
 * - Panel sits to the right of the session card on the homepage.
 *
 * SRP: Only responsible for rendering the homepage setup form + rules panel.
 * SOLID DIP: Delegates session logic to SessionController, rule logic to BlockingService.
 * Low Coupling: Communicates back to BrowserLauncher via a Consumer callback.
 */
public class HomepageView {

    private final SessionController      controller;
    private final Consumer<FocusSession> onSessionStart;
    private final TopicValidationService validator;
    private final BlockingService        blockingService;

    // Live list views — kept as fields so refresh() can update them
    private ListView<String> whitelistView;
    private ListView<String> blacklistView;

    private static final PomodoroStrategy[] STRATEGIES = {
            new Standard2505Strategy(),
            new Extended5010Strategy()
    };

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Full constructor — use this one. */
    public HomepageView(SessionController controller,
                        Consumer<FocusSession> onSessionStart,
                        TopicValidationService validator,
                        BlockingService blockingService) {
        this.controller      = controller;
        this.onSessionStart  = onSessionStart;
        this.validator       = validator;
        this.blockingService = blockingService;
    }

    /** Backward-compat constructor (no blockingService — rules panel hidden). */
    public HomepageView(SessionController controller,
                        Consumer<FocusSession> onSessionStart,
                        TopicValidationService validator) {
        this(controller, onSessionStart, validator, null);
    }

    /** Backward-compat constructor (no validator, no blockingService). */
    public HomepageView(SessionController controller,
                        Consumer<FocusSession> onSessionStart) {
        this(controller, onSessionStart, new TopicValidationService(), null);
    }

    // ── View builder ──────────────────────────────────────────────────────────

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0f0f0f;");

        if (blockingService != null) {

            VBox sessionCard = buildCenterCard();
            VBox rulesPanel  = buildRulesPanel();

            // Initially hidden
            rulesPanel.setVisible(false);
            rulesPanel.setManaged(false);

            // Toggle button
            ToggleButton toggleRulesBtn = new ToggleButton("⚙ Focus Controls");
            toggleRulesBtn.setStyle(unselectedStyle());

            toggleRulesBtn.selectedProperty().addListener((obs, was, isSelected) -> {
                rulesPanel.setVisible(isSelected);
                rulesPanel.setManaged(isSelected);

                toggleRulesBtn.setStyle(isSelected ? selectedStyle() : unselectedStyle());
            });

            HBox contentRow = new HBox(20, sessionCard, rulesPanel);
            contentRow.setAlignment(Pos.CENTER);
            contentRow.setPadding(new Insets(32));

            HBox.setHgrow(rulesPanel, Priority.ALWAYS);

            VBox container = new VBox(10, toggleRulesBtn, contentRow);
            container.setAlignment(Pos.TOP_CENTER);

            ScrollPane scroll = new ScrollPane(container);
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background: #0f0f0f; -fx-background-color: #0f0f0f;");

            root.setCenter(scroll);
        }

        return root;
    }

    // ── Session card (unchanged from original) ────────────────────────────────

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

        Label modeLabel      = fieldLabel("Pomodoro Mode");
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
        startBtn.setOnMouseEntered(e -> startBtn.setStyle(startBtn.getStyle().replace("#7C6EFA", "#9A8FFF")));
        startBtn.setOnMouseExited(e  -> startBtn.setStyle(startBtn.getStyle().replace("#9A8FFF", "#7C6EFA")));
        startBtn.setOnAction(e -> handleStart(topicField, durationField, modeGroup, errorLabel));

        VBox form = new VBox(10,
                topicLabel, topicField,
                durationLabel, durationField,
                modeLabel, modeRow,
                errorLabel);
        form.setMaxWidth(380);

        VBox card = new VBox(28, headerBox, form, startBtn);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(440);
        card.setMinWidth(380);
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

    // ── Site Rules Manager panel ──────────────────────────────────────────────

    private VBox buildRulesPanel() {
        // Header
        Label title = new Label("⚙  Site Rules");
        title.setStyle(
                "-fx-font-size: 17px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        Label subtitle = new Label("Changes are saved to the database immediately.");
        subtitle.setStyle(
                "-fx-font-size: 11px;" +
                "-fx-text-fill: #555555;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        VBox header = new VBox(4, title, subtitle);

        // Two columns
        whitelistView = new ListView<>();
        blacklistView = new ListView<>();

        VBox whitelistCol = buildRuleColumn(
                "✅  Whitelist",
                "#3ecfcf",
                whitelistView,
                SiteMetadata.RuleType.WHITELIST);

        VBox blacklistCol = buildRuleColumn(
                "🚫  Blacklist",
                "#ff6b6b",
                blacklistView,
                SiteMetadata.RuleType.BLACKLIST);

        HBox columns = new HBox(14, whitelistCol, blacklistCol);
        HBox.setHgrow(whitelistCol, Priority.ALWAYS);
        HBox.setHgrow(blacklistCol, Priority.ALWAYS);
        columns.setFillHeight(true);

        VBox panel = new VBox(16, header, columns);
        panel.setPadding(new Insets(28, 28, 28, 28));
        panel.setMinWidth(440);
        panel.setMaxWidth(600);
        panel.setStyle(
                "-fx-background-color: #161616;" +
                "-fx-background-radius: 20;" +
                "-fx-border-color: #2a2a2a;" +
                "-fx-border-radius: 20;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 32, 0.2, 0, 8);");

        VBox.setVgrow(columns, Priority.ALWAYS);

        // Initial load
        refreshLists();

        return panel;
    }

    private VBox buildRuleColumn(String title,
                                  String accent,
                                  ListView<String> listView,
                                  SiteMetadata.RuleType ruleType) {
        Label lbl = new Label(title);
        lbl.setStyle(
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: " + accent + ";" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        // Style list
        listView.setPrefHeight(220);
        listView.setStyle(
                "-fx-background-color: #1e1e1e;" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: #2e2e2e;" +
                "-fx-border-radius: 10;" +
                "-fx-border-width: 1;" +
                "-fx-control-inner-background: #1e1e1e;");

        // Custom cell with remove button
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-background-color: transparent; -fx-padding: 2 4;");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label domainLabel = new Label(item);
                domainLabel.setStyle(
                        "-fx-text-fill: #cccccc;" +
                        "-fx-font-family: 'Consolas', monospace;" +
                        "-fx-font-size: 12px;");

                Button removeBtn = new Button("✕");
                removeBtn.setStyle(
                        "-fx-background-color: transparent;" +
                        "-fx-text-fill: #444444;" +
                        "-fx-cursor: hand;" +
                        "-fx-font-size: 11px;" +
                        "-fx-padding: 0 4;");
                removeBtn.setOnMouseEntered(e ->
                        removeBtn.setStyle(removeBtn.getStyle()
                                .replace("#444444", "#ff6b6b")));
                removeBtn.setOnMouseExited(e ->
                        removeBtn.setStyle(removeBtn.getStyle()
                                .replace("#ff6b6b", "#444444")));
                removeBtn.setOnAction(e -> {
                    blockingService.removeDomain(item);
                    refreshLists();
                });

                HBox row = new HBox(domainLabel, removeBtn);
                HBox.setHgrow(domainLabel, Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setSpacing(6);
                setGraphic(row);
            }
        });

        // Empty state label
        listView.setPlaceholder(buildPlaceholder("No entries yet"));

        // Add field + button
        TextField addField = new TextField();
        addField.setPromptText("e.g. example.com");
        addField.setPrefHeight(36);
        addField.setStyle(
                "-fx-background-color: #1e1e1e;" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: #2e2e2e;" +
                "-fx-border-radius: 8;" +
                "-fx-border-width: 1;" +
                "-fx-text-fill: white;" +
                "-fx-prompt-text-fill: #555555;" +
                "-fx-padding: 6 10;" +
                "-fx-font-size: 12px;" +
                "-fx-font-family: 'Consolas', monospace;");

        // Focus border colour on focus
        addField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            String base = "-fx-background-color: #1e1e1e;" +
                    "-fx-background-radius: 8;" +
                    "-fx-border-radius: 8;" +
                    "-fx-border-width: 1;" +
                    "-fx-text-fill: white;" +
                    "-fx-prompt-text-fill: #555555;" +
                    "-fx-padding: 6 10;" +
                    "-fx-font-size: 12px;" +
                    "-fx-font-family: 'Consolas', monospace;";
            addField.setStyle(base + (isFocused
                    ? "-fx-border-color: " + accent + "88;"
                    : "-fx-border-color: #2e2e2e;"));
        });

        Button addBtn = new Button("Add");
        addBtn.setPrefHeight(36);
        addBtn.setMinWidth(52);
        addBtn.setStyle(
                "-fx-background-color: " + accent + "22;" +
                "-fx-text-fill: " + accent + ";" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: " + accent + "55;" +
                "-fx-border-radius: 8;" +
                "-fx-border-width: 1;" +
                "-fx-cursor: hand;" +
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 6 14;");
        addBtn.setOnMouseEntered(e ->
                addBtn.setStyle(addBtn.getStyle().replace(accent + "22", accent + "44")));
        addBtn.setOnMouseExited(e ->
                addBtn.setStyle(addBtn.getStyle().replace(accent + "44", accent + "22")));

        // Feedback label (shows "Added!" or "Already exists")
        Label feedback = new Label("");
        feedback.setStyle("-fx-font-size: 10px; -fx-text-fill: " + accent + ";");
        feedback.setMinHeight(14);

        Runnable doAdd = () -> {
            String raw = addField.getText().trim().toLowerCase();
            if (raw.isEmpty()) return;
            // Strip protocol if accidentally pasted in
            raw = raw.replaceFirst("^https?://", "").replaceFirst("/.*", "");
            String domain = blockingService.normalizeDomain(raw);

            // Check for conflict
            if (ruleType == SiteMetadata.RuleType.WHITELIST && blockingService.isBlacklisted(domain)) {
                showFeedback(feedback, "⚠ Already blacklisted — remove it first.", "#ffaa44");
                return;
            }
            if (ruleType == SiteMetadata.RuleType.BLACKLIST && blockingService.isWhitelisted(domain)) {
                showFeedback(feedback, "⚠ Already whitelisted — remove it first.", "#ffaa44");
                return;
            }

            if (ruleType == SiteMetadata.RuleType.WHITELIST) {
                blockingService.whitelist(domain, "User-added");
            } else {
                blockingService.blacklist(domain, "User-added");
            }

            addField.clear();
            refreshLists();
            showFeedback(feedback, "✓ Saved to database.", accent);
        };

        addBtn.setOnAction(e -> doAdd.run());
        addField.setOnAction(e -> doAdd.run());

        HBox addRow = new HBox(6, addField, addBtn);
        HBox.setHgrow(addField, Priority.ALWAYS);
        addRow.setAlignment(Pos.CENTER_LEFT);

        VBox col = new VBox(8, lbl, listView, addRow, feedback);
        col.setFillWidth(true);
        VBox.setVgrow(listView, Priority.ALWAYS);
        return col;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Refreshes both list views from the current in-memory state. */
    private void refreshLists() {
        if (blockingService == null) return;
        if (whitelistView != null)
            whitelistView.getItems().setAll(blockingService.getAllWhitelisted());
        if (blacklistView != null)
            blacklistView.getItems().setAll(blockingService.getAllBlacklisted());
    }

    private void showFeedback(Label label, String text, String color) {
        label.setText(text);
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: " + color + ";");
        // Auto-clear after 3 s
        javafx.animation.PauseTransition pause =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
        pause.setOnFinished(e -> Platform.runLater(() -> label.setText("")));
        pause.play();
    }

    private Label buildPlaceholder(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #444444; -fx-font-size: 12px; -fx-font-style: italic;");
        return lbl;
    }

    // ── Session start handler ─────────────────────────────────────────────────

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

        ToggleButton selected = (ToggleButton) modeGroup.getSelectedToggle();
        if (selected == null) {
            errorLabel.setText("⚠ Please select a Pomodoro mode.");
            return;
        }

        int strategyIndex = modeGroup.getToggles().indexOf(selected);
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
