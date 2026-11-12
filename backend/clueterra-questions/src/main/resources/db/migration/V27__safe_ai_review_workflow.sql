ALTER TABLE cl_schema.hints
    ADD CONSTRAINT fk_hints_review_claim_run FOREIGN KEY (review_claim_run_id) REFERENCES cl_ai_schema.ai_run (id) ON DELETE SET NULL;

CREATE OR REPLACE FUNCTION cl_ai_schema.decide_change_items(
    p_change_set_id BIGINT,
    p_item_types TEXT[],
    p_decision TEXT
)
RETURNS TABLE (
    change_set_status VARCHAR,
    total_items INTEGER,
    applied_items INTEGER,
    rejected_items INTEGER,
    affected_items INTEGER
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_now TIMESTAMPTZ := NOW();
    v_affected INTEGER := 0;
    v_total INTEGER := 0;
    v_applied INTEGER := 0;
    v_rejected INTEGER := 0;
    v_status VARCHAR(20);
BEGIN
    IF p_decision NOT IN ('APPROVED', 'REJECTED') THEN
        RAISE EXCEPTION 'Unsupported AI item decision: %', p_decision;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM cl_ai_schema.ai_hint_change_set WHERE id = p_change_set_id FOR UPDATE
    ) THEN
        RAISE EXCEPTION 'AI change set not found: %', p_change_set_id;
    END IF;

    WITH affected AS (
        UPDATE cl_ai_schema.ai_hint_change_item item
        SET status = p_decision,
            decided_at = v_now
        WHERE item.change_set_id = p_change_set_id
          AND item.item_type = ANY(p_item_types)
          AND (
              (p_decision = 'APPROVED' AND item.status = 'PENDING')
              OR (p_decision = 'REJECTED' AND item.status = 'PENDING')
          )
        RETURNING item.id
    )
    SELECT COUNT(*) INTO v_affected FROM affected;

    IF p_decision = 'REJECTED' THEN
        UPDATE cl_ai_schema.ai_generated_hint_proposal proposal
        SET status = 'REJECTED'
        WHERE proposal.id IN (
            SELECT item.proposal_id
            FROM cl_ai_schema.ai_hint_change_item item
            WHERE item.change_set_id = p_change_set_id
              AND item.item_type = ANY(p_item_types)
              AND item.status = 'REJECTED'
              AND item.decided_at = v_now
              AND item.proposal_id IS NOT NULL
        );

        UPDATE cl_schema.hints hint
        SET review_status = 'REVIEWED',
            review_claim_run_id = NULL,
            last_reviewed_at = v_now,
            last_review_run_id = rejected.run_id,
            updated_at = v_now
        FROM (
            SELECT item.hint_id, MAX(item.run_id) AS run_id
            FROM cl_ai_schema.ai_hint_change_item item
            WHERE item.change_set_id = p_change_set_id
              AND item.item_type = ANY(p_item_types)
              AND item.status = 'REJECTED'
              AND item.decided_at = v_now
              AND item.hint_id IS NOT NULL
            GROUP BY item.hint_id
        ) rejected
        WHERE hint.id = rejected.hint_id;
    END IF;

    SELECT COUNT(*),
           COUNT(*) FILTER (WHERE status = 'APPLIED'),
           COUNT(*) FILTER (WHERE status = 'REJECTED')
    INTO v_total, v_applied, v_rejected
    FROM cl_ai_schema.ai_hint_change_item
    WHERE change_set_id = p_change_set_id;

    IF v_total = 0 OR EXISTS (
        SELECT 1
        FROM cl_ai_schema.ai_hint_change_item
        WHERE change_set_id = p_change_set_id
          AND status IN ('PENDING', 'APPROVED')
    ) THEN
        v_status := 'DRAFT';
    ELSIF v_applied = v_total THEN
        v_status := 'APPLIED';
    ELSIF v_rejected = v_total THEN
        v_status := 'REJECTED';
    ELSE
        v_status := 'PARTIALLY_APPLIED';
    END IF;

    UPDATE cl_ai_schema.ai_hint_change_set change_set
    SET status = v_status,
        total_items = v_total,
        applied_items = v_applied,
        rejected_items = v_rejected,
        rejected_at = CASE
            WHEN v_status = 'REJECTED' THEN COALESCE(change_set.rejected_at, v_now)
            ELSE change_set.rejected_at
        END
    WHERE change_set.id = p_change_set_id;

    RETURN QUERY SELECT v_status, v_total, v_applied, v_rejected, v_affected;
END;
$$;

COMMENT ON FUNCTION cl_ai_schema.decide_change_items IS 'Atomically approves or rejects selected AI change-item types and reconciles proposal, hint, and change-set state';
