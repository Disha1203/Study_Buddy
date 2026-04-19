package com.ooad.study_buddy.focus.ui;

import com.ooad.study_buddy.model.SiteMetadata;
import com.ooad.study_buddy.service.BlockingService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * VIEW — Whitelist / Blacklist Manager
 *
 * NEW CLASS — addresses missing feature: "No UI to add whitelist/blacklist".
 *
 * Renders a floating panel (returned as a VBox) that can be injected into
 * any layout.  It shows two columns (Whitelist | Blacklist) with:
 *   • A live list of current entries from the repository
 *   • An input field + Add button for each column
 *   • A Remove button on each entry
 *
 * SRP  : Only responsible for rendering and mutating the rule lists.
 * DIP  : Depends on BlockingService; never touches the repository directly.
 * Low Coupling: Returns a VBox node; caller decides where to place it.
 */
public class WhitelistManagerView {

    private final BlockingService blockingService;

    // Live list views
    private final ListView<String> whitelistView = new ListView<>();
    private final ListView<String> blacklistView = new ListView<>();

    public WhitelistManagerView(BlockingService blockingService) {
        this.blockingService = blockingService;
    }

    /**
     * Builds the panel.  Call refresh() after any external rule changes.
     */
    public VBox getView() {
        // ── Whitelist column ──
        VBox whitelistCol = buildColumn(
                "✅ Whitelist",
                "#3ecfcf",
                whitelistView,
                SiteMetadata.RuleType.WHITELIST);

        // ── Blacklist column ──
        VBox blacklistCol = buildColumn(
                "🚫 Blacklist",
                "#ff6b6b",
                blacklistView,
                SiteMetadata.RuleType.BLACKLIST);

        HBox columns = new HBox(16, whitelistCol, blacklistCol);
        HBox.setHgrow(whitelistCol, Priority.ALWAYS);
        HBox.setHgrow(blacklistCol, Priority.ALWAYS);

        Label title = new Label("Site Rules");
        title.setStyle(
                "-fx-font-size: 16px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        VBox root = new VBox(14, title, columns);
        root.setPadding(new Insets(20));
        root.setStyle(
                "-fx-background-color: #161616;" +
                "-fx-background-radius: 16;" +
                "-fx-border-color: #2a2a2a;" +
                "-fx-border-radius: 16;" +
                "-fx-border-width: 1;");

        refresh();
        return root;
    }

    // ── Column builder ────────────────────────────────────────────────────────

    private VBox buildColumn(String title, String accent,
                             ListView<String> listView,
                             SiteMetadata.RuleType ruleType) {
        Label lbl = new Label(title);
        lbl.setStyle(
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: " + accent + ";" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        listView.setPrefHeight(180);
        listView.setStyle(
                "-fx-background-color: #1e1e1e;" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: #2e2e2e;" +
                "-fx-border-radius: 8;" +
                "-fx-border-width: 1;" +
                "-fx-control-inner-background: #1e1e1e;" +
                "-fx-text-fill: white;");

        // Remove on click
        listView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(null); setGraphic(null); return; }

                    Label domainLabel = new Label(item);
                    domainLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-family: 'Consolas', monospace;");

                    Button removeBtn = new Button("✕");
                    removeBtn.setStyle(
                            "-fx-background-color: transparent;" +
                            "-fx-text-fill: #666666;" +
                            "-fx-cursor: hand;" +
                            "-fx-font-size: 11px;");
                    removeBtn.setOnMouseEntered(e -> removeBtn.setStyle(removeBtn.getStyle().replace("#666666", "#ff6b6b")));
                    removeBtn.setOnMouseExited(e  -> removeBtn.setStyle(removeBtn.getStyle().replace("#ff6b6b", "#666666")));
                    removeBtn.setOnAction(e -> {
                        // Flip to opposite type briefly then delete — simplest approach
                        // is to just delete the entry from the repo.
                        blockingService.removeDomain(item);
                        refresh();
                    });

                    HBox row = new HBox(domainLabel, removeBtn);
                    HBox.setHgrow(domainLabel, Priority.ALWAYS);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setSpacing(6);
                    setGraphic(row);
                }
            };
            cell.setStyle("-fx-background-color: transparent;");
            return cell;
        });

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

        Button addBtn = new Button("Add");
        addBtn.setPrefHeight(36);
        addBtn.setStyle(
                "-fx-background-color: " + accent + "22;" +
                "-fx-text-fill: " + accent + ";" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: " + accent + "44;" +
                "-fx-border-radius: 8;" +
                "-fx-border-width: 1;" +
                "-fx-cursor: hand;" +
                "-fx-font-size: 12px;" +
                "-fx-padding: 6 14;");

        Runnable doAdd = () -> {
            String domain = addField.getText().trim().toLowerCase();
            if (domain.isEmpty()) return;
            if (ruleType == SiteMetadata.RuleType.WHITELIST)
                blockingService.whitelist(domain, "User-added");
            else
                blockingService.blacklist(domain, "User-added");
            addField.clear();
            refresh();
        };

        addBtn.setOnAction(e -> doAdd.run());
        addField.setOnAction(e -> doAdd.run());

        HBox addRow = new HBox(6, addField, addBtn);
        HBox.setHgrow(addField, Priority.ALWAYS);

        VBox col = new VBox(8, lbl, listView, addRow);
        col.setFillWidth(true);
        return col;
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    /**
     * Reloads both list views from the current repository state.
     * Call whenever rules change externally.
     */
    public void refresh() {
        List<String> wl = blockingService.getAllWhitelisted();
        List<String> bl = blockingService.getAllBlacklisted();
        whitelistView.getItems().setAll(wl);
        blacklistView.getItems().setAll(bl);
    }
}
