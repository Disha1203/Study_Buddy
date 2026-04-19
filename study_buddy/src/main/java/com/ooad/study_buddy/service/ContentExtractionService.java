package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.ContentData;
import javafx.scene.web.WebEngine;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

/**
 * SERVICE — Content Extraction
 *
 * SRP : Only responsible for pulling structured data out of a WebEngine page.
 * DIP : Returns a ContentData DTO; callers are not coupled to JS or DOM.
 *
 * ═══════════════════════════════════════════════════════════════
 *  BUG FIXES
 * ═══════════════════════════════════════════════════════════════
 *
 *  BUG 1 — Dead import: netscape.javascript.JSObject
 *  ──────────────────────────────────────────────────────────────
 *  JSObject was imported but never used. In Java 17 module mode,
 *  netscape.javascript is an internal JavaFX API not exported by
 *  default, which can cause a compile error. Removed entirely.
 *
 *  BUG 2 — YouTube title extracted unreliably via document.title
 *  ──────────────────────────────────────────────────────────────
 *  YouTube is a SPA. When Worker.State.SUCCEEDED fires, document.title
 *  may still read "YouTube" if JS hydration hasn't completed yet.
 *  Fix: for /watch URLs, try window.ytInitialData JSON first (embedded
 *  before any SPA code runs), then og:title, then document.title with
 *  " - YouTube" suffix stripped.
 *
 *  BUG 3 — h1 selector silent failure + innerText layout dependency
 *  ──────────────────────────────────────────────────────────────────
 *  querySelector('h1') returns null on LeetCode and YouTube (confirmed:
 *  firstH1: NULL in probe output). innerText requires browser layout —
 *  can return "" in WebEngine's partial rendering mode.
 *  Fix: selector changed to 'h1,h2'; text read as innerText||textContent.
 *
 *  BUG 4 — visibleText clone: innerText-only + aside not stripped
 *  ──────────────────────────────────────────────────────────────────
 *  Same innerText layout issue in the body clone. Cookie banners and
 *  sidebars (<aside>) added noise to the content blob.
 *  Fix: fallback to textContent; added 'aside' to the removal list.
 *
 *  BUG 5 — No fallback when all extractions fail (Reddit, about:blank)
 *  ──────────────────────────────────────────────────────────────────
 *  Probe shows Reddit combined text is 38 chars; about:blank is 12.
 *  toCombinedText() sends near-empty content to Python → HTTP 400 or
 *  arbitrary score. Fix: if title and ogTitle are both null after all
 *  extractions, derive a human-readable title from the URL path as a
 *  last-resort fallback so the embedding model gets something real.
 * ═══════════════════════════════════════════════════════════════
 */
@Service
public class ContentExtractionService {

    // BUG 1 FIX: removed unused `import netscape.javascript.JSObject`
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

        // ── Title ──────────────────────────────────────────────────────────────
        // BUG 2 FIX: YouTube /watch pages get a dedicated extraction path that
        // reads ytInitialData before falling back to og:title / document.title.
        String title;
        if (isYouTubeWatch(pageUrl)) {
            title = extractYouTubeTitle(engine);
        } else {
            title = clean(safeExecute(engine, "document.title || ''"));
        }

        // ── Meta description ───────────────────────────────────────────────────
        String metaDesc = clean(safeExecute(engine,
                "var m=document.querySelector('meta[name=\"description\"]');" +
                "m ? m.getAttribute('content') : ''"));

        // ── OpenGraph ──────────────────────────────────────────────────────────
        String ogTitle = clean(safeExecute(engine,
                "var m=document.querySelector('meta[property=\"og:title\"]');" +
                "m ? m.getAttribute('content') : ''"));

        String ogDesc = clean(safeExecute(engine,
                "var m=document.querySelector('meta[property=\"og:description\"]');" +
                "m ? m.getAttribute('content') : ''"));

        // ── First heading ──────────────────────────────────────────────────────
        // BUG 3 FIX: broadened selector to h1,h2; use innerText||textContent
        // so pages without h1 (LeetCode, YouTube) still yield a heading.
        String firstH1 = clean(safeExecute(engine,
                "var h=document.querySelector('h1,h2');" +
                "h ? (h.innerText||h.textContent||'') : ''"));

        // ── Visible body text (capped at 1200 chars) ───────────────────────────
        // BUG 4 FIX: fallback to textContent; added 'aside' to noise removal.
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

        // ── BUG 5 FIX: last-resort title from URL path ────────────────────────
        // If both title and ogTitle are null (e.g. Reddit bot-check, about:blank),
        // toCombinedText() would produce near-empty content and the Python API
        // would receive content:"" → HTTP 400. Derive something from the URL path.
        if (title == null && ogTitle == null) {
            title = extractPathAsTitle(pageUrl);
            LOG.fine("[EXTRACT] All title fields empty — using path-derived title: " + title);
        }

        LOG.fine(String.format(
                "[EXTRACT] url=%s title='%s' ogTitle='%s' metaLen=%d visibleLen=%d",
                pageUrl,
                title       != null ? title       : "(null)",
                ogTitle     != null ? ogTitle     : "(null)",
                metaDesc    != null ? metaDesc.length()    : 0,
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

    // ── YouTube-specific title extraction ──────────────────────────────────────

    private boolean isYouTubeWatch(String url) {
        return url != null && url.contains("youtube.com/watch");
    }

    /**
     * BUG 2 FIX — Extracts the YouTube video title reliably.
     *
     * Priority:
     *   1. window.ytInitialData JSON (embedded before SPA hydration — most reliable)
     *   2. og:title meta tag
     *   3. document.title with " - YouTube" suffix stripped
     */
    private String extractYouTubeTitle(WebEngine engine) {
        // Try 1: ytInitialData JSON blob (set by YouTube before any SPA code runs)
        String fromJson = safeExecute(engine,
                "(function(){" +
                "  try {" +
                "    var d=window.ytInitialData;" +
                "    if(!d) return '';" +
                "    var contents=d.contents" +
                "      &&d.contents.twoColumnWatchNextResults" +
                "      &&d.contents.twoColumnWatchNextResults.results" +
                "      &&d.contents.twoColumnWatchNextResults.results.results" +
                "      &&d.contents.twoColumnWatchNextResults.results.results.contents;" +
                "    if(!contents) return '';" +
                "    for(var i=0;i<contents.length;i++){" +
                "      var p=contents[i].videoPrimaryInfoRenderer;" +
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

        // Try 3: document.title with " - YouTube" suffix stripped
        String docTitle = safeExecute(engine, "document.title || ''");
        if (docTitle != null && !docTitle.isBlank()) {
            String stripped = docTitle.replaceAll("(?i)\\s*-\\s*YouTube\\s*$", "").trim();
            t = clean(stripped);
            LOG.fine("[EXTRACT] YouTube title from document.title: " + t);
            return t;
        }

        return null;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Executes JS and converts the result to a String.
     * Returns empty string on any error — prevents crash on paywall / CSP pages.
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
     * BUG 5 FIX — Derives a human-readable string from the URL path.
     * e.g. "https://example.com/intro-to-algorithms" → "intro to algorithms"
     * Used only when all other extraction fields are null.
     */
    private String extractPathAsTitle(String url) {
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null || path.isBlank() || path.equals("/")) return url;
            String[] parts = path.split("/");
            String last = parts[parts.length - 1];
            // Strip common file extensions (.html, .php, .aspx, etc.)
            last = last.replaceAll("\\.[a-zA-Z0-9]{1,5}$", "");
            return last.replace("-", " ").replace("_", " ").trim();
        } catch (Exception e) {
            return url;
        }
    }
}