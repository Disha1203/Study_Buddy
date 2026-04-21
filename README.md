# 📚 Study Buddy — AI-Powered Focus Browser

> A distraction-blocking browser that uses AI to evaluate whether websites are relevant to your study topic — powered by a Pomodoro timer, semantic embeddings, and a Chain of Responsibility pipeline.

---

## 🧠 What Is Study Buddy?

Study Buddy is a JavaFX desktop application that intercepts every web page you visit during a study session and decides — in real time — whether it's relevant to what you're supposed to be learning.

You set a topic (e.g., *"Binary Search Trees"*), choose a Pomodoro timer mode, and start a session. From that point, every URL is evaluated through a multi-step pipeline: a fast structural check, a URL keyword check, and finally a deep semantic similarity check powered by a local Python embedding API. Irrelevant or distracting pages are blocked with a sarcastic message and a score.

Built as part of an Object-Oriented Analysis & Design course, the project demonstrates real-world application of design patterns (Strategy, Observer, Chain of Responsibility), SOLID principles, and GRASP guidelines throughout.

---

## ✨ Features

- 🔒 **AI-Powered Page Blocking** — semantic similarity scoring via sentence-transformers (`mxbai-embed-large-v1`) blocks irrelevant content automatically
- ⏱️ **Pomodoro Timer** — Standard (25/5), Extended (50/10), and a Dev (15/15) mode; floating overlay stays visible while browsing
- 🔗 **Chain of Responsibility** — modular pipeline: Whitelist → Blacklist → URL Check → Content Check
- 📋 **Whitelist & Blacklist Manager** — add/remove domains via UI; changes persist to MySQL instantly
- 💾 **Save for Later** — blocked pages can be bookmarked and reviewed in the post-session summary
- ⏳ **2-Minute Buffer** — temporarily unlocks browsing when you genuinely need to visit a blocked page
- 🗂️ **Multi-Tab Browser** — full tab management with per-tab history and WebEngine instances
- 📊 **Session Tracking** — every navigation decision (URL, verdict, score, reason) is logged to MySQL
- 🛑 **Break-Mode Awareness** — blocking is automatically suspended during Pomodoro break intervals
- 🎭 **Sarcastic Block Page** — 60+ rotating roast messages shown when a page is blocked

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **UI / Frontend** | JavaFX 21 |
| **Backend / App** | Java 17, Spring Boot 4.0 |
| **AI / Embeddings** | Python 3.10+, FastAPI, sentence-transformers |
| **Database** | MySQL 8+ |
| **ORM** | Spring Data JPA (Hibernate) |
| **HTTP** | `java.net.HttpURLConnection` (Java → Python) |
| **Build Tool** | Maven (via `mvnw`) |

---

## 📁 Project Structure

```
study_buddy/
├── python/
│   ├── relevance_api.py          # FastAPI server — semantic similarity scoring
│   └── requirements.txt          # Python dependencies
│
├── src/main/java/com/ooad/study_buddy/
│   ├── browser/
│   │   ├── AiBrowserView.java            # Multi-tab browser UI with block-swap logic
│   │   ├── BrowserLauncher.java          # JavaFX Application entry point; wires all services
│   │   └── InMemorySiteMetadataRepository.java  # In-memory repo (no Spring context needed)
│   │
│   ├── controller/
│   │   ├── BrowserController.java        # Attaches WebEngine listeners; LRU cache; cooldowns
│   │   └── RelevanceController.java      # Coordinates quickDecision + chain; promotes BORDERLINE → BLOCKED
│   │
│   ├── focus/
│   │   ├── FocusStateHolder.java         # Shared focus/break flag (thread-safe AtomicBoolean)
│   │   ├── controller/SessionController.java   # Starts/stops PomodoroTimer
│   │   ├── model/FocusSession.java       # Data: topic, duration, strategy
│   │   ├── observer/TimerObserver.java   # Observer interface (onTick, onModeChange, onSessionEnd)
│   │   ├── strategy/                     # Pomodoro strategy implementations (Standard, Extended, Dev)
│   │   ├── timer/PomodoroTimer.java      # JavaFX Timeline ticker; notifies observers
│   │   └── ui/
│   │       ├── BlockPageView.java        # "You're blocked" screen with sarcasm + score
│   │       ├── HomepageView.java         # Session setup form + whitelist/blacklist panel
│   │       ├── OptionsView.java          # "Save for Later" / "2 Min Buffer" / "Go Back" screen
│   │       ├── TimerOverlay.java         # Floating timer widget (Observer concrete class)
│   │       └── WhitelistManagerView.java # Reusable whitelist/blacklist manager panel
│   │
│   ├── model/
│   │   ├── ContentData.java              # Page metadata DTO (title, og:title, visible text, etc.)
│   │   ├── LocalSavedLinksStore.java     # In-memory singleton for "Save for Later" links
│   │   ├── RelevanceResult.java          # Immutable result: verdict (ALLOWED/BLOCKED/BORDERLINE) + score
│   │   ├── SessionEvent.java             # JPA entity: one row per navigation event
│   │   ├── SiteMetadata.java             # JPA entity: whitelist/blacklist rules
│   │   ├── StudySession.java             # JPA entity: one row per focus session
│   │   └── Topic.java                    # Validated topic data holder
│   │
│   ├── relevance/                        # Chain of Responsibility
│   │   ├── RelevanceHandler.java         # Interface: handle() + setNext()
│   │   ├── AbstractRelevanceHandler.java # Base: manages chain linkage
│   │   ├── WhitelistHandler.java         # Link 1: short-circuit ALLOW if whitelisted
│   │   ├── BlacklistHandler.java         # Link 2: short-circuit BLOCK if blacklisted
│   │   ├── URLCheckHandler.java          # Link 3: keyword-based fast check
│   │   ├── ContentCheckHandler.java      # Link 4: calls Python API for semantic check
│   │   └── RelevanceChainFactory.java    # Assembles and returns the chain head
│   │
│   ├── repository/
│   │   ├── SiteMetadataRepository.java   # JPA repo for blocking_rules table
│   │   ├── StudySessionRepository.java   # JPA repo for study_sessions table
│   │   └── SessionEventRepository.java   # JPA repo for session_events table
│   │
│   └── service/
│       ├── BlockingService.java          # Platform rules + whitelist/blacklist CRUD (MySQL-backed)
│       ├── ContentExtractionService.java # JS execution on WebEngine to extract page metadata
│       ├── DatabaseSeedService.java      # Loads MySQL blocking_rules into in-memory repo on startup
│       ├── RelevanceService.java         # HTTP POST to Python API; classifies score → verdict
│       ├── SessionTrackingService.java   # Logs session open/close/events to MySQL via JDBC
│       └── TopicValidationService.java   # Validates topic input (gibberish detection, vague terms)
│
└── src/main/resources/
    ├── application.properties            # DB URL, JPA config, relevance API URL
    └── schema.sql                        # Auto-creates tables + seeds default whitelist/blacklist
```

---

## ⚙️ Prerequisites

| Requirement | Version |
|---|---|
| Java JDK | 17+ |
| Maven | Bundled via `mvnw` |
| Python | 3.10+ |
| MySQL | 8.0+ |
| pip | Latest |

---

## 🗄️ Database Setup

1. Create the database:

```sql
CREATE DATABASE studybuddy;
```

2. Update credentials in `src/main/resources/application.properties` if needed:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/studybuddy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=//yourusername
spring.datasource.password=//yourpassword
```

> Tables (`blocking_rules`, `study_sessions`, `session_events`) are auto-created and seeded on first startup via `schema.sql`.

---

## 🐍 Python Service Setup

The semantic relevance scoring runs as a local FastAPI server.

```bash
cd python/
pip install -r requirements.txt
uvicorn relevance_api:app --host 0.0.0.0 --port 8001
```

**Model:** `mixedbread-ai/mxbai-embed-large-v1` (~500 MB, CPU-friendly, downloads automatically on first run)

Verify it's running:

```bash
curl http://localhost:8001/health
# {"status":"ok","model":"mixedbread-ai/mxbai-embed-large-v1","version":"1.5.0"}
```

> ⚠️ The Java app **must** be able to reach `http://localhost:8001`. If the Python service is down, blocked pages default to BLOCKED (fail-safe behaviour).

---

## ▶️ Running the Application

Start both services in separate terminals:

**Terminal 1 — Python Relevance API:**
```bash
cd python/
uvicorn relevance_api:app --port 8001
```

**Terminal 2 — Java/JavaFX App:**
```bash
cd study_buddy/
./mvnw javafx:run
```

Or alternatively using Spring Boot:
```bash
./mvnw spring-boot:run
```

---

## 🧭 Usage

### 1. Set Up Your Session
On the homepage, enter:
- **Session Topic** — be specific (e.g., *"Dijkstra's Shortest Path Algorithm"*, not *"coding"*)
- **Duration** — total session length in minutes (e.g., `60`)
- **Pomodoro Mode** — Standard (25/5), Extended (50/10), or DEV (15/15 for testing)

Optionally click **⚙ Focus Controls** to pre-configure your whitelist and blacklist.

### 2. Browse During Focus
The browser works like a normal browser. Every page you load is evaluated:

| Decision | Meaning |
|---|---|
| ✅ **ALLOWED** | Relevance score ≥ 65% — page loads normally |
| 🚫 **BLOCKED** | Score < 65% or known distraction — block page shown |
| ⚡ **Instant ALLOW** | Domain is whitelisted (e.g., `stackoverflow.com`) |
| ⛔ **Instant BLOCK** | Domain is blacklisted (e.g., `instagram.com`) |

### 3. When Blocked
The block page shows:
- A sarcastic message
- The relevance score
- Three options via **Other Options**:
  - 📌 **Save for Later** — bookmark it for after the session
  - ⏱️ **2 Min Buffer** — temporarily unlock browsing for 120 seconds
  - ← **Go Back** — return to the previous page

### 4. Break Time
During Pomodoro breaks, blocking is automatically suspended. The floating timer overlay switches from `FOCUS` to `BREAK` and all pages are allowed through.

### 5. Session End
When the timer finishes, a summary screen displays all links you saved for later during the session. Click **Start New Session** to reset and go again.

---

## 🔗 Pre-Seeded Rules

The following domains are whitelisted by default (always allowed regardless of topic):

| Domain | Category |
|---|---|
| `scholar.google.com` | Google Scholar |
| `arxiv.org` | Academic preprints |
| `stackoverflow.com` | Developer Q&A |
| `docs.oracle.com` | Java docs |
| `docs.spring.io` | Spring docs |
| `developer.mozilla.org` | MDN Web Docs |
| `khanacademy.org`, `coursera.org`, `udemy.com`, `edx.org` | Online courses |
| `google.com`, `bing.com`, `duckduckgo.com` | Search engines |

Social media, streaming, and gaming platforms are blacklisted by default.

---

## 🧩 Design Patterns Used

| Pattern | Where |
|---|---|
| **Strategy** | `PomodoroStrategy` — Standard, Extended, Dev modes are interchangeable |
| **Observer** | `TimerObserver` / `PomodoroTimer` — UI overlay reacts to timer ticks and mode changes |
| **Chain of Responsibility** | `RelevanceHandler` chain — Whitelist → Blacklist → URL → Content |
| **MVC** | Separated View (`*View.java`), Controller (`*Controller.java`), Model (`model/`) |
| **Factory (GRASP Creator)** | `RelevanceChainFactory` — assembles the handler chain |

---

## 🖼️ Screenshots

> _Screenshots below — replace paths with actual images_

**Homepage / Session Setup**
![Homepage](<img width="1196" height="679" alt="image" src="https://github.com/user-attachments/assets/45babcea-4c29-4e95-aebe-7652e3f0f3a8" />)

**Active Browser with Timer Overlay**
![Browser](docs/screenshots/browser.png)

**Block Page**
![Block Page](docs/screenshots/block_page.png)

**Options Screen (Save for Later / 2 Min Buffer)**
![Options](docs/screenshots/options.png)

**Session Summary**
![Summary](docs/screenshots/summary.png)

---

## 🔮 Future Improvements

- [ ] **Browser History Panel** — show all visited/blocked URLs per session
- [ ] **Analytics Dashboard** — visualise focus time, block rate, and topic alignment over multiple sessions
- [ ] **Custom Threshold Slider** — let users tune the relevance score cutoff (currently fixed at 65%)
- [ ] **Cloud Sync** — sync whitelist/blacklist rules and saved links across devices
- [ ] **Extension Mode** — run as a Chrome/Firefox extension instead of a standalone JavaFX app
- [ ] **Scheduled Sessions** — calendar integration to auto-start sessions at set times
- [ ] **Better YouTube Handling** — use YouTube Data API for richer metadata extraction on video pages


## 📄 License

This project is licensed under the **MIT License**.

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software.
```

---

<div align="center">
  <sub>Built with ☕ Java, 🐍 Python, and a lot of blocked distractions.</sub>
</div>
