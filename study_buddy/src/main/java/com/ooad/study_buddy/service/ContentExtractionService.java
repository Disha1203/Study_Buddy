package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.ContentData;
import javafx.scene.web.WebEngine;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

/**
 * SERVICE — Content Extraction
 *
 * ═══════════════════════════════════════════════════════════════
 *  BUG FIXES (vs previous version)
 * ═══════════════════════════════════════════════════════════════
 *
 *  BUG 1 — YouTube title extracted as "YouTube" instead of video title
 *  ──────────────────────────────────────────────────────────────────────
 *  ROOT CAUSE: document.title for a YouTube watch page returns the full
 *  title including " - YouTube" suffix. The og:title meta tag was the
 *  correct field but was queried with a generic CSS selector that didn't
 *  always fire because YouTube is a SPA and og:title is set dynamically.
 *
 *  FIX: For YouTube /watch pages, prefer ytInitialData (the JSON blob
 *  YouTube embeds in its own page) which contains the canonical video
 *  title before any SPA manipulation. Falls back to og:title, then
 *  document.title (with " - YouTube" stripped).
 *
 *  BUG 2 — Empty content causes Python API to get useless input
 *  ───────────────────────────────────────────────────────────────
 *  ROOT CAUSE: On pages that block scripting (CSP) or are behind a
 *  paywall, all JS extractions returned "" → toCombinedText() was blank
 *  → RelevanceService sent an empty "content" field → Python returned an
 *  arbitrary score → result was unpredictable.
 *
 *  FIX: Extract() now builds a best-effort text blob from whatever fields
 *  are non-null. If ALL fields are null (total extraction failure), the
 *  returned ContentData will have the URL as the only non-null field.
 *  RelevanceService handles this by returning BORDERLINE (now reclassified
 *  to BLOCKED by RelevanceController) with a clear reason string.
 *
 *  BUG 3 — Extraction crashes on SPA navigations (WebEngine not ready)
 *  ──────────────────────────────────────────────────────────────────────
 *  ROOT CAUSE: engine.executeScript() throws if the engine is in a bad
 *  state (Worker.State.FAILED, or mid-cancel). The generic try/catch in
 *  safeExecute caught it but the returned "" was not logged.
 *
 *  FIX: safeExecute() now logs at FINE level when JS execution fails, so
 *  extraction failures are visible during debugging without being noisy.
 *
 * ═══════════════════════════════════════════════════════════════
 */
@Service
public class ContentExtractionService {

    private static final Logger LOG = Logger.getLogger(ContentExtractionService.class.getName());

    /**
     * Extracts metadata and text from the currently loaded page.
     * Safe to call on partial pages (paywalls, login walls, CSP-locked pages).
     *
     * @param engine  an already-loaded WebEngine (Worker.State.SUCCEEDED)
     * @param pageUrl the URL string (always non-null in the returned ContentData)
     * @return ContentData — url is always set; other fields may be null
     */
    public ContentData extract(WebEngine engine, String pageUrl) {

        // ── Title ─────────────────────────────────────────────────────────────
        // For YouTube: try ytInitialData first (most accurate for video titles)
        // then og:title, then document.title stripped of " - YouTube"
        String title;
        if (isYouTubeWatch(pageUrl)) {
            title = extractYouTubeTitle(engine);
        } else {
            title = clean(safeExecute(engine, "document.title || ''"));
        }

        // ── Meta description ──────────────────────────────────────────────────
        String metaDesc = clean(safeExecute(engine,
                "var m=document.querySelector('meta[name=\"description\"]');" +
                "m ? m.getAttribute('content') : ''"));

        // ── OpenGraph ─────────────────────────────────────────────────────────
        String ogTitle = clean(safeExecute(engine,
                "var m=document.querySelector('meta[property=\"og:title\"]');" +
                "m ? m.getAttribute('content') : ''"));

        String ogDesc = clean(safeExecute(engine,
                "var m=document.querySelector('meta[property=\"og:description\"]');" +
                "m ? m.getAttribute('content') : ''"));

        // ── First heading ─────────────────────────────────────────────────────
        String firstH1 = clean(safeExecute(engine,
                "var h=document.querySelector('h1,h2'); h ? (h.innerText||h.textContent||'') : ''"));

        // ── Visible body text (capped at 1200 chars) ──────────────────────────
        String visibleText = clean(safeExecute(engine,
                "(function(){" +
                "  try {" +
                "    var el=document.body;" +
                "    if(!el) return '';" +
                "    var clone=el.cloneNode(true);" +
                "    ['script','style','nav','footer','header','aside'].forEach(function(t){" +
                "      clone.querySelectorAll(t).forEach(function(n){n.remove();});" +
                "    });" +
                "    var txt=(clone.innerText||clone.textContent||'').replace(/\\s+/g,' ').trim();" +
                "    return txt.substring(0,1200);" +
                "  } catch(e){ return ''; }" +
                "})()"));

        // ── Fallback: if title and og:title are BOTH empty, use page URL path ──
        // This gives the relevance checker at least something to work with for
        // pages that block all metadata extraction.
        if (title == null && ogTitle == null) {
            title = extractPathAsTitle(pageUrl);
            LOG.fine("[EXTRACT] All title fields empty — using path-derived title: " + title);
        }

        LOG.fine(String.format(
                "[EXTRACT] url=%s title='%s' ogTitle='%s' metaLen=%d visibleLen=%d",
                pageUrl,
                title != null ? title : "(null)",
                ogTitle != null ? ogTitle : "(null)",
                metaDesc != null ? metaDesc.length() : 0,
                visibleText != null ? visibleText.length() : 0));

        return new ContentData(
                pageUrl,
                title,
                metaDesc,
                ogTitle,
                ogDesc,
                firstH1,
                visibleText);
    }

    // ── YouTube-specific title extraction ─────────────────────────────────────

    private boolean isYouTubeWatch(String url) {
        return url != null && url.contains("youtube.com/watch");
    }

    /**
     * Extracts the YouTube video title.
     *
     * Priority order:
     *   1. ytInitialData JSON blob (most reliable — set before SPA hydration)
     *   2. og:title meta tag
     *   3. document.title with " - YouTube" stripped
     */
    private String extractYouTubeTitle(WebEngine engine) {
        // Try 1: ytInitialData JSON (available in YouTube's page source)
        String fromJson = safeExecute(engine,
                "(function(){" +
                "  try {" +
                "    var d=window.ytInitialData;" +
                "    if(!d) return '';" +
                "    var v=d.contents&&d.contents.twoColumnWatchNextResults&&" +
                "           d.contents.twoColumnWatchNextResults.results&&" +
                "           d.contents.twoColumnWatchNextResults.results.results&&" +
                "           d.contents.twoColumnWatchNextResults.results.results.contents;" +
                "    if(!v) return '';" +
                "    for(var i=0;i<v.length;i++){" +
                "      var p=v[i].videoPrimaryInfoRenderer;" +
                "      if(p&&p.title&&p.title.runs&&p.title.runs[0])" +
                "        return p.title.runs[0].text;" +
                "    }" +
                "    return '';" +
                "  } catch(e){ return ''; }" +
                "})()");

        String t = clean(fromJson);
        if (t != null) {
            LOG.fine("[EXTRACT] YouTube title from ytInitialData: " + t);
            return t;
        }

        // Try 2: og:title
        String ogTitle = clean(safeExecute(engine,
                "var m=document.querySelector('meta[property=\"og:title\"]');" +
                "m ? m.getAttribute('content') : ''"));
        if (ogTitle != null) {
            LOG.fine("[EXTRACT] YouTube title from og:title: " + ogTitle);
            return ogTitle;
        }

        // Try 3: document.title, strip " - YouTube" suffix
        String docTitle = safeExecute(engine, "document.title || ''");
        if (docTitle != null && !docTitle.isBlank()) {
            String stripped = docTitle.replaceAll("(?i)\\s*-\\s*YouTube\\s*$", "").trim();
            t = clean(stripped);
            LOG.fine("[EXTRACT] YouTube title from document.title: " + t);
            return t;
        }

        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Executes JS and converts the result to a String.
     * Returns empty string on any error.
     */
    private String safeExecute(WebEngine engine, String js) {
        try {
            Object result = engine.executeScript(js);
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            LOG.fine("[EXTRACT] JS error (ignored): " + e.getMessage());
            return "";
        }
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    /**
     * Derives a rough human-readable title from the URL path.
     * e.g. "https://example.com/intro-to-algorithms" → "intro to algorithms"
     */
    private String extractPathAsTitle(String url) {
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null || path.isBlank() || path.equals("/")) return url;
            // Take last segment, replace hyphens/underscores with spaces
            String[] parts = path.split("/");
            String last = parts[parts.length - 1];
            return last.replace("-", " ").replace("_", " ").trim();
        } catch (Exception e) {
            return url;
        }
    }
}