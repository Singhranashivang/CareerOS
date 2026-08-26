-- The model's text as it stood when the post was scheduled, kept alongside the
-- (possibly edited) body in scheduled_posts.body. A snapshot on purpose:
-- regenerating a LinkedIn post overwrites linkedin_posts.post, which would
-- otherwise retroactively rewrite the "generated" half of an existing pair.
--
-- Null for every pre-existing row, and for any post not generated from a
-- LinkedIn achievement post — see ScheduledPostService.create. Null means
-- "no pair", never "unedited".
ALTER TABLE scheduled_posts ADD COLUMN generated_body TEXT;
