# Study Buddy — AI Relevance Checker Browser
## Architecture & Design Document

---

## Project Overview

Study Buddy is a focus browser that intercepts every page load, evaluates whether it is relevant to the user's study topic, and blocks distractions — all within a Pomodoro timer session.

---

## Directory Layout (new files only)

```
com.ooad.study_buddy
 ├── browser/
 │   ├── AiBrowserView.java            ← upgraded browser view with block-swap
 │   ├── BrowserLauncher.java          ← updated launcher (wires AI services)
 │   └── InMemorySiteMetadataRepository.java  ← standalone fallback repo
 │
 ├── controller/
 │   ├── BrowserController.java        ← attaches WebEngine listener
 │   └── RelevanceController.java      ← coordinates quick-check + chain
 │
 ├── model/
 │   ├── Topic.java
 │   ├── ContentData.java
 │   ├── RelevanceResult.java
 │   └── SiteMetadata.java             ← JPA entity (whitelist / blacklist)
 │
 ├── repository/
 │   └── SiteMetadataRepository.java
 │
 ├── relevance/                        ← Chain of Responsibility
 │   ├── RelevanceHandler.java         ← interface
 │   ├── AbstractRelevanceHandler.java ← base (chain linkage)
 │   ├── WhitelistHandler.java         ← link 1
 │   ├── BlacklistHandler.java         ← link 2
 │   ├── URLCheckHandler.java          ← link 3 (Phase 1 fast check)
 │   ├── ContentCheckHandler.java      ← link 4 (Phase 2 deep check)
 │   └── RelevanceChainFactory.java    ← assembles chain
 │
 ├── service/
 │   ├── TopicValidationService.java
 │   ├── ContentExtractionService.java
 │   ├── RelevanceService.java         ← calls Python API
 │   └── BlockingService.java
 │
 └── focus/ui/
     └── BlockPageView.java            ← blocked page UI

python/
 ├── relevance_api.py                  ← FastAPI + sentence-transformers
 └── requirements.txt
```

---

## Architecture: MVC

| Layer      | Classes                                                  |
|------------|----------------------------------------------------------|
| **View**   | HomepageView (unchanged), AiBrowserView, BlockPageView   |
| **Controller** | BrowserController, RelevanceController, SessionController (unchanged) |
| **Model**  | Topic, ContentData, RelevanceResult, SiteMetadata        |
| **Service**| TopicValidationService, ContentExtractionService, RelevanceService, BlockingService |

---

## Design Pattern: Chain of Responsibility

```
Request (topic + ContentData)
        │
        ▼
 WhitelistHandler  ──── domain whitelisted? ──► ALLOW (short-circuit)
        │ no
        ▼
 BlacklistHandler  ──── domain blacklisted? ──► BLOCK (short-circuit)
        │ no
        ▼
 URLCheckHandler   ──── distraction keyword in URL? ──► BLOCK
                   ──── topic keyword in URL? ──► ALLOW (fast path)
        │ neither
        ▼
 ContentCheckHandler ── calls Python API ──► ALLOW / BORDERLINE / BLOCKED
```

**Why Chain of Responsibility?**
- Each handler has a single concern (SRP).
- New rules = new handler class, zero edits to existing ones (OCP).
- The chain can be reordered or extended in `RelevanceChainFactory` without touching any handler.

---

## SOLID Principles Applied

| Principle | Where |
|-----------|-------|
| **SRP** | Every class has exactly one job: `TopicValidationService` only validates; `ContentExtractionService` only extracts; `BlockPageView` only renders the block screen. |
| **OCP** | Add a new Pomodoro strategy → implement `PomodoroStrategy`. Add a new relevance rule → implement `RelevanceHandler`. Zero modifications to existing code. |
| **LSP** | All `RelevanceHandler` implementations are fully substitutable; any handler can stand in for the interface. |
| **ISP** | `TimerObserver` is a small 3-method interface. `RelevanceHandler` is a single-method interface. Neither forces implementors to stub unused methods. |
| **DIP** | `BrowserController` depends on `ContentExtractionService` (concrete Spring bean, but its callers depend on the interface). `RelevanceController` depends on `RelevanceChainFactory` and `BlockingService` abstractions. `SessionController` accepts `TimerObserver`, not `TimerOverlay`. |

---

## GRASP Principles Applied

| Principle | Where |
|-----------|-------|
| **Information Expert** | `ContentData.toCombinedText()` — only ContentData knows how to assemble its own fields into a text blob. `FocusSession` knows its own duration/strategy. |
| **Controller** | `BrowserController` and `RelevanceController` are the single entry points for their respective subsystems. `BrowserLauncher` owns the Stage lifecycle. |
| **Creator** | `RelevanceChainFactory` creates the chain because it has the data (services) needed to initialise each link. |
| **Low Coupling** | `AiBrowserView` does not import any service. It receives a `BrowserController` callback. `BlockPageView` receives a `Runnable` for the back action. |
| **High Cohesion** | `BlockingService` only handles whitelist/blacklist/platform decisions. `RelevanceService` only handles HTTP ↔ Python. |

---

## Functional Flows

### 1. Topic Validation (Part 1)

```
HomepageView.handleStart()
  → TopicValidationService.validate(rawTopic)
      Rule 1: too short?    → Topic.invalid(...)
      Rule 2: too few alpha chars?
      Rule 3: too many words?
      Rule 4: known vague term?
      all pass → Topic.valid(...)
  → if invalid: show errorLabel in UI, stop
  → if valid: create FocusSession, call onSessionStart
```

### 2. Content Extraction (Part 2)

```
WebEngine fires SUCCEEDED state
  → ContentExtractionService.extract(engine, url)
      executes JS: document.title
      executes JS: meta[name="description"]
      executes JS: meta[property="og:title"]
      executes JS: meta[property="og:description"]
      executes JS: h1.innerText
      executes JS: body innerText (stripped, first 1200 chars)
      all wrapped in try/catch → safe on paywalls / login walls
  → returns ContentData
```

**YouTube extraction:**
- `/watch` → title from `<title>`, description from og:description
- `/shorts` → blocked before extraction (URLCheckHandler / BlockingService)
- `/results` → title = "YouTube", og:description = search context

**Reddit extraction:**
- `/r/topic/comments/id/slug` → title = post title, og:description = preview
- homepage `/` → blocked before extraction

### 3. Relevance Check (Part 3)

```
RelevanceController.evaluate(topic, contentData)
  1. BlockingService.quickDecision(url)
       → ALLOW  (platform rule)  → return immediately
       → BLOCK  (platform rule)  → return immediately
       → CHECK_RELEVANCE         → enter chain

  2. Chain:
       WhitelistHandler → skip if not whitelisted
       BlacklistHandler → block if blacklisted
       URLCheckHandler  → block on distraction keywords
                        → allow if topic keyword in URL
       ContentCheckHandler →
           RelevanceService.check(topic, contentData)
               POST http://localhost:8001/relevance
               { "topic": "...", "content": "..." }
               ← { "score": 0.72 }
           classify:
               >= 0.65 → ALLOWED
               0.40–0.65 → BORDERLINE
               < 0.40  → BLOCKED
```

### 4. Blocking (Part 4)

```
AiBrowserView.handleRelevanceResult(url, result, root)
  if result.isBlocked():
    → root.setCenter(BlockPageView)
    → random sarcastic message shown
    → "Go Back" button calls browser.goBack()
  else:
    → root.setCenter(contentPane)  // ensure WebView is shown
```

---

## Python Service

```
POST /relevance
Body: { "topic": "Binary Search Trees", "content": "page text..." }
Response: { "score": 0.83 }

GET /health
Response: { "status": "ok", "model": "all-MiniLM-L6-v2" }
```

**Setup:**
```bash
cd python/
pip install -r requirements.txt
uvicorn relevance_api:app --host 0.0.0.0 --port 8001
```

**Model:** `all-MiniLM-L6-v2` — 80 MB, CPU-friendly, 384-dim embeddings.  
Cosine similarity is normalised from `[-1, 1]` → `[0, 1]`.

---

## Java ↔ Python Integration

`RelevanceService.java` uses `java.net.http.HttpClient` (built into JDK 11+):

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8001/relevance"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
    .timeout(Duration.ofSeconds(10))
    .build();

HttpResponse<String> response =
    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

double score = objectMapper.readTree(response.body()).get("score").asDouble();
```

**Resilience:** If the Python service is down, `RelevanceService` catches the exception and returns `BORDERLINE (0.5)` — the user is never hard-blocked by a network error.

---

## Running the Full System

```bash
# Terminal 1 — Python relevance service
cd python
pip install -r requirements.txt
uvicorn relevance_api:app --port 8001

# Terminal 2 — Spring Boot + JavaFX
./mvnw javafx:run
# or
./mvnw spring-boot:run
```

---

## Pre-seeded Whitelist

The following domains are always allowed regardless of topic:

| Domain | Reason |
|--------|--------|
| scholar.google.com | Google Scholar |
| arxiv.org | Academic preprints |
| wikipedia.org | Reference |
| stackoverflow.com | Dev Q&A |
| docs.oracle.com | Java docs |
| docs.spring.io | Spring docs |
| developer.mozilla.org | MDN Web Docs |

Add more via `BlockingService.whitelist(domain, notes)`.

---

## Extending the System

**Add a new Pomodoro mode:**
```java
public class DeepWork9020Strategy implements PomodoroStrategy { ... }
// Add to HomepageView.STRATEGIES[] — done.
```

**Add a new relevance rule:**
```java
public class LoginPageHandler extends AbstractRelevanceHandler {
    @Override public RelevanceResult handle(String topic, ContentData c) {
        if (c.getTitle() != null && c.getTitle().toLowerCase().contains("sign in"))
            return RelevanceResult.borderline(0.5, "Login page detected");
        return passToNext(topic, c);
    }
}
// Insert into RelevanceChainFactory.buildChain() — done.
```

**Persist whitelist/blacklist across restarts:**  
Switch `InMemorySiteMetadataRepository` for the Spring Data JPA bean (already defined in `SiteMetadataRepository.java`) and point the H2 URL at a file: `jdbc:h2:file:./studybuddy`.
