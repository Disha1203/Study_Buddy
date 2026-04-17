package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.ContentData;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import org.springframework.stereotype.Service;

/**
 * SERVICE — Content Extraction
 *
 * SRP : Only responsible for pulling structured data out of a WebEngine page.
 * DIP : Returns a ContentData DTO; callers are not coupled to JS or DOM.
 *
 * All extraction is done via JavaScript injection so no external HTTP library
 * is needed — we piggyback on the already-loaded WebView DOM.
 */
@Service
public class ContentExtractionService {

    /**
     * Extracts metadata and text from the currently loaded page in the engine.
     * Safe to call on pages that have partial content (paywalls, login walls).
     *
     * @param engine  an already-loaded WebEngine
     * @param pageUrl the URL string (used as fallback / passed directly)
     * @return ContentData with whatever the DOM exposes; fields may be null
     */
    public ContentData extract(WebEngine engine, String pageUrl) {

        String title           = safeExecute(engine, "document.title || ''");
        String metaDesc        = safeExecute(engine,
                "var m=document.querySelector('meta[name=\"description\"]');" +
                "m ? m.getAttribute('content') : ''");
        String ogTitle         = safeExecute(engine,
                "var m=document.querySelector('meta[property=\"og:title\"]');" +
                "m ? m.getAttribute('content') : ''");
        String ogDesc          = safeExecute(engine,
                "var m=document.querySelector('meta[property=\"og:description\"]');" +
                "m ? m.getAttribute('content') : ''");
        String firstH1         = safeExecute(engine,
                "var h=document.querySelector('h1'); h ? h.innerText : ''");
        String visibleText     = safeExecute(engine,
                "(function(){" +
                "  var el=document.body;" +
                "  if(!el) return '';" +
                "  var clone=el.cloneNode(true);" +
                "  ['script','style','nav','footer','header'].forEach(function(t){" +
                "    var nodes=clone.querySelectorAll(t);" +
                "    nodes.forEach(function(n){n.remove();});" +
                "  });" +
                "  return (clone.innerText||'').replace(/\\s+/g,' ').substring(0,1200);" +
                "})()");

        return new ContentData(
                pageUrl,
                clean(title),
                clean(metaDesc),
                clean(ogTitle),
                clean(ogDesc),
                clean(firstH1),
                clean(visibleText));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Executes JS and converts the result to a String.
     * Returns empty string on any error (prevents crash on login/paywall pages).
     */
    private String safeExecute(WebEngine engine, String js) {
        try {
            Object result = engine.executeScript(js);
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
