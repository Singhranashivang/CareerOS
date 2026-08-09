-- "Analysed and found nothing to claim" was previously indistinguishable from
-- "never analysed": both showed as analyzed=false with zero achievements, so
-- pressing Analyze on a thin repository looked like it did nothing at all.
--
-- The outcome now lives on the repository rather than being inferred from the
-- existence of a repository_knowledge row, which the sufficiency floor short
-- circuits before writing.
--
-- Nullable: existing rows were analysed under the old code and their outcome is
-- genuinely unknown. Null means "never analysed", which is correct for them.

ALTER TABLE github_repositories
    ADD COLUMN last_analyzed_at  TIMESTAMPTZ,
    ADD COLUMN analysis_outcome  VARCHAR(32),
    ADD COLUMN analysis_reason   TEXT;
