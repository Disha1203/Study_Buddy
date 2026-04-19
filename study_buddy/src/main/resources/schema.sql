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