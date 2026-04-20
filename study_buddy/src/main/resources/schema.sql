-- ── Table ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS blocking_rules (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain    VARCHAR(255) NOT NULL UNIQUE,
    rule_type ENUM('WHITELIST', 'BLACKLIST') NOT NULL,
    notes     VARCHAR(500)
);

-- ── Whitelist: Academic & reference sites ─────────────────────────────────────
INSERT IGNORE INTO blocking_rules (domain, rule_type, notes) VALUES
('scholar.google.com',   'WHITELIST', 'Google Scholar'),
('arxiv.org',            'WHITELIST', 'arXiv preprints'),
('stackoverflow.com',    'WHITELIST', 'Stack Overflow'),
('docs.oracle.com',      'WHITELIST', 'Java docs'),
('docs.spring.io',       'WHITELIST', 'Spring docs'),
('developer.mozilla.org','WHITELIST', 'MDN Web Docs'),
('khanacademy.org',      'WHITELIST', 'Free education'),
('coursera.org',         'WHITELIST', 'Online courses'),
('udemy.com',            'WHITELIST', 'Online courses'),
('edx.org',              'WHITELIST', 'Online courses'),

-- ── Whitelist: Search engines ─────────────────────────────────────────────────
('google.com',           'WHITELIST', 'Google Search'),
('bing.com',             'WHITELIST', 'Bing Search'),
('duckduckgo.com',       'WHITELIST', 'DuckDuckGo'),
('yahoo.com',            'WHITELIST', 'Yahoo Search'),
('ecosia.org',           'WHITELIST', 'Ecosia Search'),

-- ── Blacklist: Social media ───────────────────────────────────────────────────
('instagram.com',        'BLACKLIST', 'Social media'),
('facebook.com',         'BLACKLIST', 'Social media'),
('twitter.com',          'BLACKLIST', 'Social media'),
('x.com',                'BLACKLIST', 'Social media'),
('tiktok.com',           'BLACKLIST', 'Short video / distraction'),
('snapchat.com',         'BLACKLIST', 'Social media'),
('pinterest.com',        'BLACKLIST', 'Social media'),
('tumblr.com',           'BLACKLIST', 'Social media'),

-- ── Blacklist: Entertainment ──────────────────────────────────────────────────
('netflix.com',          'BLACKLIST', 'Streaming'),
('hulu.com',             'BLACKLIST', 'Streaming'),
('disneyplus.com',       'BLACKLIST', 'Streaming'),
('primevideo.com',       'BLACKLIST', 'Streaming'),
('twitch.tv',            'BLACKLIST', 'Game streaming'),
('9gag.com',             'BLACKLIST', 'Memes'),
('buzzfeed.com',         'BLACKLIST', 'Entertainment'),
('dailymotion.com',      'BLACKLIST', 'Video entertainment'),

-- ── Blacklist: Gaming ─────────────────────────────────────────────────────────
('store.steampowered.com','BLACKLIST','Gaming platform'),
('epicgames.com',        'BLACKLIST', 'Gaming platform'),
('ign.com',              'BLACKLIST', 'Gaming news'),

-- ── Blacklist: Shopping ───────────────────────────────────────────────────────
('amazon.com',           'BLACKLIST', 'Shopping'),
('flipkart.com',         'BLACKLIST', 'Shopping'),
('ebay.com',             'BLACKLIST', 'Shopping');

-- ── Session tracking ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS study_sessions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic        VARCHAR(255) NOT NULL,
    strategy     VARCHAR(50)  NOT NULL,
    started_at   DATETIME     NOT NULL,
    ended_at     DATETIME,
    duration_minutes INT      NOT NULL
);

CREATE TABLE IF NOT EXISTS session_events (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id   BIGINT       NOT NULL,
    url          VARCHAR(2048) NOT NULL,
    verdict      VARCHAR(20)  NOT NULL,   -- ALLOW / BLOCK / CHECK_RELEVANCE
    score        DOUBLE,                  -- NULL for ALLOW/BLOCK platform decisions
    reason       VARCHAR(500),
    occurred_at  DATETIME     NOT NULL,
    CONSTRAINT fk_session FOREIGN KEY (session_id)
        REFERENCES study_sessions(id) ON DELETE CASCADE
);