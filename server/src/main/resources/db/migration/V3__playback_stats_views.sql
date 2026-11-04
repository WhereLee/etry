CREATE OR REPLACE VIEW v_playback_daily AS
SELECT DATE(created_at)  AS day,
       source,
       event_type,
       video_id,
       cache_hit,
       COUNT(*)          AS event_count
FROM t_playback_log
GROUP BY DATE(created_at), source, event_type, video_id, cache_hit;

CREATE OR REPLACE VIEW v_playback_url_cache_daily AS
SELECT DATE(created_at) AS day,
       SUM(CASE WHEN cache_hit = 1 THEN 1 ELSE 0 END) AS cache_hits,
       SUM(CASE WHEN cache_hit = 0 THEN 1 ELSE 0 END) AS cache_misses,
       COUNT(*) AS requests
FROM t_playback_log
WHERE source = 'BACKEND'
  AND event_type = 'URL_REQUEST'
GROUP BY DATE(created_at);