# Supabase Cache Schema

> SQL to create the cache tables in Supabase.
> Run these in the Supabase Dashboard → SQL Editor.
>
> Until these are created, the CacheManager gracefully falls back to
> Local + AniList (2-step cache instead of 3-step).

## homepage_cache table

```sql
CREATE TABLE IF NOT EXISTS homepage_cache (
    cache_key TEXT PRIMARY KEY,
    cache_value JSONB NOT NULL,
    ttl_ms BIGINT NOT NULL DEFAULT 1800000,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- RLS: allow public read, no public write (service_role only)
ALTER TABLE homepage_cache ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read" ON homepage_cache FOR SELECT USING (true);
-- No INSERT/UPDATE/DELETE policy = only service_role can write
```

## anime_cache table (for Phase 3 — detail page)

```sql
CREATE TABLE IF NOT EXISTS anime_cache (
    anilist_id INTEGER PRIMARY KEY,
    cache_value JSONB NOT NULL,
    ttl_ms BIGINT NOT NULL DEFAULT 86400000,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

ALTER TABLE anime_cache ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read" ON anime_cache FOR SELECT USING (true);
```

## How to run

1. Go to https://supabase.com/dashboard/project/jqdgdonunmqxyxmohcvj/sql/new
2. Paste the SQL above
3. Click "Run"
4. The CacheManager will automatically start using Supabase as the middle cache layer.
