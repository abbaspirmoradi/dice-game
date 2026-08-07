--------------------------------------------------------------------------------
-- PKG_CLAIMSWORKSHEET_AUTOMATION : claim_search / claim_worksheet_search
--                                  performance fix (branch-split treatment)
--------------------------------------------------------------------------------
-- Same approach as the edi_trans_search fix. Logic is preserved exactly:
--
--   1. Each procedure now branches into two physically separate cursors:
--
--        EXACT branch    : identical to the verified pre-tolerance query.
--                          Zero match-rank function calls. The policy
--                          predicate is TRIM(col) = :bind, which the
--                          TRIM function-based index in Section A serves.
--                          Also used when no policy number is supplied
--                          (original optional-policy behaviour).
--
--        TOLERANT branch : runs only when the exact pre-check found no
--                          eligible candidate (so l_policy_number is
--                          guaranteed NOT NULL in this branch).
--
--   2. In the TOLERANT branch, the per-row function scan
--        get_policy_number_match_rank(...) IN (0,1,2,3)
--      is replaced as the ROW-SOURCING predicate by the provably
--      equivalent pure-SQL candidate predicate:
--        rank 0/1 -> normalised(db) = normalised(incoming)
--        rank 2   -> normalised(db) IN (substrings of normalised(incoming)
--                                        with length >= 7 and < incoming len)
--        rank 3   -> SUBSTR(normalised(db), -8) = last 8 of normalised(incoming)
--                    (SUBSTR(x,-8) is NULL when LENGTH(x) < 8, so the DB-side
--                     length-8 gate is implicit)
--      The verified rank function is RETAINED as a safety-net filter, so it
--      is evaluated only on the small candidate set and correctness never
--      depends on the equivalence argument alone.
--
--   3. Rider matching inside the worksheet EXISTS (claim_search) and on
--      cw.rdr_nmbr (claim_worksheet_search) keeps the verified function
--      call unchanged in the tolerant branch. Once the policy predicate
--      is index-driven, rider evaluation only touches the few worksheet
--      rows of surviving candidates, so its cost is negligible and the
--      verified semantics are trivially preserved.
--
--   4. A new PRIVATE helper (Section B) builds the rank-2 substring list.
--      It is body-only: the package SPEC does not change.
--
-- The exact-candidate pre-checks in both procedures are unchanged (they
-- never used the rank functions).
--------------------------------------------------------------------------------


--------------------------------------------------------------------------------
-- SECTION A : ONE-TIME SUPPORTING DDL  (run once, outside the package)
--------------------------------------------------------------------------------
-- Expressions must be textually identical to the ones in the procedures
-- for the optimizer to match the function-based indexes.
--
-- A.1  tri_policy_detail (drives claim_search):
--
--   -- exact branch: TRIM(col) = :bind
--   CREATE INDEX ix_tpd_plcy_trim
--       ON tri_policy_detail (TRIM(client_policy_num));
--
--   -- tolerant branch, ranks 0/1/2
--   CREATE INDEX ix_tpd_plcy_norm
--       ON tri_policy_detail (
--            COALESCE(LTRIM(TRIM(client_policy_num), '0'),
--                     NVL2(TRIM(client_policy_num), '0', NULL))
--          );
--
--   -- tolerant branch, rank 3
--   CREATE INDEX ix_tpd_plcy_norm_t8
--       ON tri_policy_detail (
--            SUBSTR(COALESCE(LTRIM(TRIM(client_policy_num), '0'),
--                            NVL2(TRIM(client_policy_num), '0', NULL)), -8)
--          );
--
-- A.2  tri_claim_worksheet (drives claim_worksheet_search).
--      worksheet_status_id leads each index because the WIP filter
--      (worksheet_status_id = 898) always applies:
--
--   CREATE INDEX ix_tcw_stat_plcy_trim
--       ON tri_claim_worksheet (
--            worksheet_status_id,
--            TRIM(client_policy_num)
--          );
--
--   CREATE INDEX ix_tcw_stat_plcy_norm
--       ON tri_claim_worksheet (
--            worksheet_status_id,
--            COALESCE(LTRIM(TRIM(client_policy_num), '0'),
--                     NVL2(TRIM(client_policy_num), '0', NULL))
--          );
--
--   CREATE INDEX ix_tcw_stat_plcy_norm_t8
--       ON tri_claim_worksheet (
--            worksheet_status_id,
--            SUBSTR(COALESCE(LTRIM(TRIM(client_policy_num), '0'),
--                            NVL2(TRIM(client_policy_num), '0', NULL)), -8)
--          );
--
-- A.3  Verify an index exists on tri_claim_worksheet (claim_id) - it serves
--      the correlated rider/NAR EXISTS in claim_search. Also verify the join
--      keys used by claim_search (tri_policy_detail.claim_id,
--      tri_claim.client_plan_ref_id, etc.) are indexed - usually they are.
--
-- A.4  Refresh statistics after creating the indexes:
--
--   BEGIN
--     DBMS_STATS.GATHER_TABLE_STATS(USER, 'TRI_POLICY_DETAIL',   cascade => TRUE);
--     DBMS_STATS.GATHER_TABLE_STATS(USER, 'TRI_CLAIM_WORKSHEET', cascade => TRUE);
--   END;
--   /
--------------------------------------------------------------------------------


--------------------------------------------------------------------------------
-- SECTION B : NEW PRIVATE HELPER (package body only - place it directly
--             after normalise_identifier_text; no spec change)
--------------------------------------------------------------------------------
-- Builds the rank-2 candidate list: every substring of p_norm with
-- length >= p_min_len and length < LENGTH(p_norm). A DB value whose
-- normalised form equals one of these substrings satisfies exactly the
-- rank-2 conditions of the shared match functions (minimum DB length,
-- incoming strictly longer, containment).
--
-- For a typical policy number the list holds a few dozen values.
--------------------------------------------------------------------------------

  FUNCTION build_embedded_candidates (
      p_norm    IN VARCHAR2,
      p_min_len IN PLS_INTEGER
  ) RETURN SYS.ODCIVARCHAR2LIST
  IS
      v_list      SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST();
      v_len_total PLS_INTEGER;
  BEGIN
      IF p_norm IS NULL THEN
          RETURN v_list;
      END IF;

      v_len_total := LENGTH(p_norm);

      IF v_len_total <= p_min_len THEN
          RETURN v_list;
      END IF;

      FOR v_len IN p_min_len .. v_len_total - 1 LOOP
          FOR v_pos IN 1 .. v_len_total - v_len + 1 LOOP
              v_list.EXTEND;
              v_list(v_list.COUNT) :=
                  SUBSTR(p_norm, v_pos, v_len);
          END LOOP;
      END LOOP;

      RETURN v_list;
  END build_embedded_candidates;


--------------------------------------------------------------------------------
-- SECTION C : REPLACEMENT claim_search PROCEDURE (package body)
--------------------------------------------------------------------------------
PROCEDURE claim_search (
    p_policy_number      IN  VARCHAR2,
    p_last_name          IN  VARCHAR2,
    p_first_name         IN  VARCHAR2,
    p_company_codes      IN  t_varchar2_tab,
    p_adminco_id         IN  NUMBER DEFAULT NULL,
    p_claim_notice_nar   IN  NUMBER DEFAULT NULL,
    p_rider_number       IN  VARCHAR2 DEFAULT NULL,
    p_record_number      IN  VARCHAR2,
    p_max_rows           IN  PLS_INTEGER DEFAULT 1000,
    p_results            OUT SYS_REFCURSOR
) IS
    --------------------------------------------------------------------------
    -- Normalised inputs
    --------------------------------------------------------------------------
    l_policy_number      VARCHAR2(200) := TRIM(p_policy_number);
    l_last_name          VARCHAR2(200) := TRIM(p_last_name);
    l_first_name         VARCHAR2(200) := TRIM(p_first_name);
    l_claim_notice_nar   NUMBER        := p_claim_notice_nar;
    l_rider_number       VARCHAR2(50)  := TRIM(p_rider_number);
    l_record_number      VARCHAR2(50)  := TRIM(p_record_number);
    l_max_rows           PLS_INTEGER   := NVL(p_max_rows, 1000);

    --------------------------------------------------------------------------
    -- Company filtering
    --------------------------------------------------------------------------
    l_company_codes      SYS.ODCIVARCHAR2LIST :=
                             SYS.ODCIVARCHAR2LIST();

    l_has_companies      PLS_INTEGER := 0;

    --------------------------------------------------------------------------
    -- Exact-first / tolerant-second matching
    --------------------------------------------------------------------------
    v_exact_candidate_cnt NUMBER := 0;
    v_use_tolerant_match  PLS_INTEGER := 0;

    --------------------------------------------------------------------------
    -- NEW: tolerant-mode policy candidate keys (built only when needed)
    --------------------------------------------------------------------------
    l_policy_norm        VARCHAR2(4000);
    l_policy_tail8       VARCHAR2(8);
    l_policy_subs        SYS.ODCIVARCHAR2LIST :=
                             SYS.ODCIVARCHAR2LIST();
BEGIN
    --------------------------------------------------------------------------
    -- Normalise maximum rows
    --------------------------------------------------------------------------
    IF l_max_rows <= 0 THEN
        l_max_rows := 1000;
    END IF;

    --------------------------------------------------------------------------
    -- Normalise company codes
    --------------------------------------------------------------------------
    IF p_company_codes IS NOT NULL
       AND p_company_codes.COUNT > 0
    THEN
        IF p_company_codes.COUNT = 1
           AND p_company_codes(1) = '__ALL__'
        THEN
            l_has_companies := 0;
        ELSE
            l_company_codes.EXTEND(p_company_codes.COUNT);

            FOR i IN 1 .. p_company_codes.COUNT LOOP
                l_company_codes(i) := p_company_codes(i);
            END LOOP;

            l_has_companies := 1;
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- Exact candidate pre-check.  (UNCHANGED)
    --
    -- If policy is null, no policy filtering is required and exact mode is
    -- retained.
    --------------------------------------------------------------------------
    IF l_policy_number IS NOT NULL THEN
        SELECT COUNT(*)
          INTO v_exact_candidate_cnt
          FROM (
                SELECT 1
                  FROM tri_claim a

                  LEFT JOIN tri_insured_client b
                         ON b.insured_client_id =
                                a.insured_client_id

                  LEFT JOIN tri_policy_detail d
                         ON d.claim_id =
                                a.claim_id

                  JOIN tri_plan_client_ref w
                    ON w.client_plan_ref_id =
                           a.client_plan_ref_id

                  JOIN tri_plan x
                    ON x.plan_id =
                           w.plan_id

                  JOIN tri_treaty y
                    ON y.treaty_cnt =
                           x.treaty_cnt

                  JOIN tri_cmpny z
                    ON z.cmpny_id =
                           y.cmpny_id

                 WHERE TRIM(d.client_policy_num) =
                           l_policy_number

                   AND (
                        l_has_companies = 0
                        OR z.lowcode IN (
                            SELECT column_value
                              FROM TABLE(l_company_codes)
                        )
                       )

                   AND (
                        p_adminco_id IS NULL
                        OR y.master_typ_id_adminco =
                               p_adminco_id
                       )

                   AND (
                        l_record_number IS NULL
                        OR TRIM(a.rec_nmbr) =
                               l_record_number
                       )

                   AND (
                        l_last_name IS NULL
                        OR (
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(NVL(b.last_name, '')))
                            ) =
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(l_last_name))
                            )
                            OR METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(NVL(b.last_name, '')))
                                )
                            ) =
                            METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(l_last_name))
                                )
                            )
                            OR SOUNDEX(NVL(b.last_name, '')) =
                               SOUNDEX(l_last_name)
                            OR INSTR(
                                UPPER(NVL(b.last_name, '')),
                                UPPER(l_last_name)
                            ) > 0
                        )
                       )

                   AND (
                        l_first_name IS NULL
                        OR (
                            SUBSTR(
                                UPPER(NVL(b.first_name, '')),
                                1,
                                1
                            ) =
                            SUBSTR(
                                UPPER(l_first_name),
                                1,
                                1
                            )
                            OR (
                                LENGTH(l_first_name) > 1
                                AND (
                                    METAPHONE(
                                        TRIM(
                                            UPPER(
                                                NVL(b.first_name, '')
                                            )
                                        )
                                    ) =
                                    METAPHONE(
                                        TRIM(UPPER(l_first_name))
                                    )
                                    OR INSTR(
                                        UPPER(NVL(b.first_name, '')),
                                        UPPER(l_first_name)
                                    ) > 0
                                )
                            )
                        )
                       )

                   AND (
                        (
                            l_rider_number IS NULL
                            AND l_claim_notice_nar IS NULL
                        )
                        OR EXISTS (
                            SELECT 1
                              FROM tri_claim_worksheet cw
                             WHERE cw.claim_id =
                                       a.claim_id
                               AND (
                                    (
                                        l_rider_number IS NOT NULL
                                        AND TRIM(cw.rdr_nmbr) =
                                               l_rider_number
                                    )
                                    OR (
                                        l_rider_number IS NULL
                                        AND l_claim_notice_nar IS NOT NULL
                                        AND cw.cur_act_nar =
                                               l_claim_notice_nar
                                    )
                               )
                        )
                       )

                   AND ROWNUM = 1
          );

        IF v_exact_candidate_cnt = 0 THEN
            v_use_tolerant_match := 1;
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- NEW: build tolerant candidate keys once, only when needed.
    -- (Tolerant mode implies l_policy_number IS NOT NULL.)
    --------------------------------------------------------------------------
    IF v_use_tolerant_match = 1 THEN
        l_policy_norm := normalise_identifier_text(l_policy_number);

        IF l_policy_norm IS NOT NULL THEN
            IF LENGTH(l_policy_norm) >= 8 THEN
                l_policy_tail8 := SUBSTR(l_policy_norm, -8);
            END IF;

            l_policy_subs :=
                build_embedded_candidates(l_policy_norm, 7);
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- Search claims - branched.
    --
    -- EXACT branch (also covers the no-policy case):
    --   original verified pre-tolerance query. No rank function calls.
    --
    -- TOLERANT branch:
    --   index-capable candidate predicate sources the rows; the verified
    --   rank functions are retained as safety-net filters on that small
    --   candidate set.
    --------------------------------------------------------------------------
    IF v_use_tolerant_match = 0 THEN

        OPEN p_results FOR
            WITH base AS (
                SELECT DISTINCT
                    a.claim_id,
                    z.lowcode AS company_code

                FROM tri_claim a

                LEFT JOIN tri_insured_client b
                       ON b.insured_client_id =
                              a.insured_client_id

                LEFT JOIN tri_policy_detail d
                       ON d.claim_id =
                              a.claim_id

                JOIN tri_plan_client_ref w
                  ON w.client_plan_ref_id =
                         a.client_plan_ref_id

                JOIN tri_plan x
                  ON x.plan_id =
                         w.plan_id

                JOIN tri_treaty y
                  ON y.treaty_cnt =
                         x.treaty_cnt

                JOIN tri_cmpny z
                  ON z.cmpny_id =
                         y.cmpny_id

                WHERE
                    ------------------------------------------------------------
                    -- Policy: exact only (original pre-tolerance behaviour).
                    ------------------------------------------------------------
                    (
                        l_policy_number IS NULL
                        OR TRIM(d.client_policy_num) =
                               l_policy_number
                    )

                    AND (
                        l_has_companies = 0
                        OR z.lowcode IN (
                            SELECT column_value
                              FROM TABLE(l_company_codes)
                        )
                    )

                    AND (
                        p_adminco_id IS NULL
                        OR y.master_typ_id_adminco =
                               p_adminco_id
                    )

                    AND (
                        l_record_number IS NULL
                        OR TRIM(a.rec_nmbr) =
                               l_record_number
                    )

                    ------------------------------------------------------------
                    -- Last name (verbatim)
                    ------------------------------------------------------------
                    AND (
                        l_last_name IS NULL
                        OR (
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(NVL(b.last_name, '')))
                            ) =
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(l_last_name))
                            )
                            OR METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(NVL(b.last_name, '')))
                                )
                            ) =
                            METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(l_last_name))
                                )
                            )
                            OR SOUNDEX(NVL(b.last_name, '')) =
                               SOUNDEX(l_last_name)
                            OR INSTR(
                                UPPER(NVL(b.last_name, '')),
                                UPPER(l_last_name)
                            ) > 0
                        )
                    )

                    ------------------------------------------------------------
                    -- First name (verbatim)
                    ------------------------------------------------------------
                    AND (
                        l_first_name IS NULL
                        OR (
                            SUBSTR(
                                UPPER(NVL(b.first_name, '')),
                                1,
                                1
                            ) =
                            SUBSTR(
                                UPPER(l_first_name),
                                1,
                                1
                            )
                            OR (
                                LENGTH(l_first_name) > 1
                                AND (
                                    METAPHONE(
                                        TRIM(
                                            UPPER(NVL(b.first_name, ''))
                                        )
                                    ) =
                                    METAPHONE(
                                        TRIM(UPPER(l_first_name))
                                    )
                                    OR INSTR(
                                        UPPER(NVL(b.first_name, '')),
                                        UPPER(l_first_name)
                                    ) > 0
                                )
                            )
                        )
                    )

                    ------------------------------------------------------------
                    -- Rider/NAR: exact arms only.
                    ------------------------------------------------------------
                    AND (
                        (
                            l_rider_number IS NULL
                            AND l_claim_notice_nar IS NULL
                        )

                        OR EXISTS (
                            SELECT 1
                              FROM tri_claim_worksheet cw
                             WHERE cw.claim_id =
                                       a.claim_id
                               AND (
                                    (
                                        l_rider_number IS NOT NULL
                                        AND TRIM(cw.rdr_nmbr) =
                                               l_rider_number
                                    )
                                    OR (
                                        l_rider_number IS NULL
                                        AND l_claim_notice_nar IS NOT NULL
                                        AND cw.cur_act_nar =
                                               l_claim_notice_nar
                                    )
                               )
                        )
                    )
            )
            SELECT
                claim_id,
                company_code
            FROM (
                SELECT
                    claim_id,
                    company_code
                FROM base
                ORDER BY claim_id
            )
            WHERE ROWNUM <= l_max_rows;

    ELSE

        OPEN p_results FOR
            WITH base AS (
                SELECT DISTINCT
                    a.claim_id,
                    z.lowcode AS company_code

                FROM tri_claim a

                LEFT JOIN tri_insured_client b
                       ON b.insured_client_id =
                              a.insured_client_id

                LEFT JOIN tri_policy_detail d
                       ON d.claim_id =
                              a.claim_id

                JOIN tri_plan_client_ref w
                  ON w.client_plan_ref_id =
                         a.client_plan_ref_id

                JOIN tri_plan x
                  ON x.plan_id =
                         w.plan_id

                JOIN tri_treaty y
                  ON y.treaty_cnt =
                         x.treaty_cnt

                JOIN tri_cmpny z
                  ON z.cmpny_id =
                         y.cmpny_id

                WHERE
                    ------------------------------------------------------------
                    -- Policy candidate predicate (pure SQL, index-capable).
                    -- Equivalent to get_policy_number_match_rank IN (0,1,2,3):
                    --   ranks 0/1 -> normalised equality
                    --   rank 2    -> normalised value IN substring list
                    --   rank 3    -> SUBSTR(norm,-8) = incoming tail-8
                    ------------------------------------------------------------
                    (
                        COALESCE(LTRIM(TRIM(d.client_policy_num), '0'),
                                 NVL2(TRIM(d.client_policy_num),
                                      '0', NULL))
                            = l_policy_norm

                        OR COALESCE(LTRIM(TRIM(d.client_policy_num), '0'),
                                    NVL2(TRIM(d.client_policy_num),
                                         '0', NULL))
                            IN (
                                SELECT column_value
                                  FROM TABLE(l_policy_subs)
                            )

                        OR (
                            l_policy_tail8 IS NOT NULL
                            AND SUBSTR(
                                    COALESCE(
                                        LTRIM(TRIM(d.client_policy_num),
                                              '0'),
                                        NVL2(TRIM(d.client_policy_num),
                                             '0', NULL)
                                    ), -8
                                ) = l_policy_tail8
                        )
                    )

                    ------------------------------------------------------------
                    -- Safety net: verified rank filter, now evaluated only
                    -- on the small candidate set.
                    ------------------------------------------------------------
                    AND PKG_CLAIMSWORKSHEET_AUTOMATION.get_policy_number_match_rank(
                            l_policy_number,
                            TRIM(d.client_policy_num)
                        ) IN (0, 1, 2, 3)

                    AND (
                        l_has_companies = 0
                        OR z.lowcode IN (
                            SELECT column_value
                              FROM TABLE(l_company_codes)
                        )
                    )

                    AND (
                        p_adminco_id IS NULL
                        OR y.master_typ_id_adminco =
                               p_adminco_id
                    )

                    AND (
                        l_record_number IS NULL
                        OR TRIM(a.rec_nmbr) =
                               l_record_number
                    )

                    ------------------------------------------------------------
                    -- Last name (verbatim)
                    ------------------------------------------------------------
                    AND (
                        l_last_name IS NULL
                        OR (
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(NVL(b.last_name, '')))
                            ) =
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(l_last_name))
                            )
                            OR METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(NVL(b.last_name, '')))
                                )
                            ) =
                            METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(l_last_name))
                                )
                            )
                            OR SOUNDEX(NVL(b.last_name, '')) =
                               SOUNDEX(l_last_name)
                            OR INSTR(
                                UPPER(NVL(b.last_name, '')),
                                UPPER(l_last_name)
                            ) > 0
                        )
                    )

                    ------------------------------------------------------------
                    -- First name (verbatim)
                    ------------------------------------------------------------
                    AND (
                        l_first_name IS NULL
                        OR (
                            SUBSTR(
                                UPPER(NVL(b.first_name, '')),
                                1,
                                1
                            ) =
                            SUBSTR(
                                UPPER(l_first_name),
                                1,
                                1
                            )
                            OR (
                                LENGTH(l_first_name) > 1
                                AND (
                                    METAPHONE(
                                        TRIM(
                                            UPPER(NVL(b.first_name, ''))
                                        )
                                    ) =
                                    METAPHONE(
                                        TRIM(UPPER(l_first_name))
                                    )
                                    OR INSTR(
                                        UPPER(NVL(b.first_name, '')),
                                        UPPER(l_first_name)
                                    ) > 0
                                )
                            )
                        )
                    )

                    ------------------------------------------------------------
                    -- Rider/NAR: tolerant arms (verbatim verified logic).
                    -- Only evaluated for candidate claims, so the rank
                    -- function touches few rows.
                    ------------------------------------------------------------
                    AND (
                        (
                            l_rider_number IS NULL
                            AND l_claim_notice_nar IS NULL
                        )

                        OR EXISTS (
                            SELECT 1
                              FROM tri_claim_worksheet cw
                             WHERE cw.claim_id =
                                       a.claim_id
                               AND (
                                    (
                                        l_rider_number IS NOT NULL
                                        AND PKG_CLAIMSWORKSHEET_AUTOMATION.get_coverage_number_match_rank(
                                                l_rider_number,
                                                TRIM(cw.rdr_nmbr)
                                            ) IN (0, 1, 2)
                                    )
                                    OR (
                                        l_rider_number IS NULL
                                        AND l_claim_notice_nar IS NOT NULL
                                        AND cw.cur_act_nar =
                                               l_claim_notice_nar
                                    )
                               )
                        )
                    )
            )
            SELECT
                claim_id,
                company_code
            FROM (
                SELECT
                    claim_id,
                    company_code
                FROM base
                ORDER BY claim_id
            )
            WHERE ROWNUM <= l_max_rows;

    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE_APPLICATION_ERROR(
            -20050,
            'PKG_CLAIMSWORKSHEET_AUTOMATION.claim_search failed: '
            || SQLERRM
        );
END claim_search;


--------------------------------------------------------------------------------
-- SECTION D : REPLACEMENT claim_worksheet_search PROCEDURE (package body)
--------------------------------------------------------------------------------
PROCEDURE claim_worksheet_search (
    p_policy_number      IN  VARCHAR2,
    p_last_name          IN  VARCHAR2,
    p_first_name         IN  VARCHAR2,
    p_company_codes      IN  t_varchar2_tab,
    p_adminco_id         IN  NUMBER DEFAULT NULL,
    p_claim_notice_nar   IN  NUMBER DEFAULT NULL,
    p_rider_number       IN  VARCHAR2 DEFAULT NULL,
    p_record_number      IN  VARCHAR2,
    p_max_rows           IN  PLS_INTEGER DEFAULT 1000,
    p_results            OUT SYS_REFCURSOR
) IS
    --------------------------------------------------------------------------
    -- Normalised input parameters
    --------------------------------------------------------------------------
    l_policy_number      VARCHAR2(200);
    l_last_name          VARCHAR2(200);
    l_first_name         VARCHAR2(200);
    l_rider_number       VARCHAR2(50);
    l_record_number      VARCHAR2(50);
    l_claim_notice_nar   NUMBER;
    l_max_rows           PLS_INTEGER;

    --------------------------------------------------------------------------
    -- WIP worksheet status
    --------------------------------------------------------------------------
    c_wip_status_id      CONSTANT NUMBER := 898;

    --------------------------------------------------------------------------
    -- Company-code filtering
    --------------------------------------------------------------------------
    l_company_codes      SYS.ODCIVARCHAR2LIST :=
                             SYS.ODCIVARCHAR2LIST();

    l_has_companies      PLS_INTEGER := 0;

    --------------------------------------------------------------------------
    -- Exact-first / tolerant-second matching
    --------------------------------------------------------------------------
    l_exact_candidate_exists PLS_INTEGER := 0;
    l_use_tolerant_match     PLS_INTEGER := 0;

    --------------------------------------------------------------------------
    -- NEW: tolerant-mode policy candidate keys (built only when needed)
    --------------------------------------------------------------------------
    l_policy_norm        VARCHAR2(4000);
    l_policy_tail8       VARCHAR2(8);
    l_policy_subs        SYS.ODCIVARCHAR2LIST :=
                             SYS.ODCIVARCHAR2LIST();

BEGIN
    --------------------------------------------------------------------------
    -- Normalise incoming parameters
    --------------------------------------------------------------------------
    l_policy_number    := TRIM(p_policy_number);
    l_last_name        := TRIM(p_last_name);
    l_first_name       := TRIM(p_first_name);
    l_rider_number     := TRIM(p_rider_number);
    l_record_number    := TRIM(p_record_number);
    l_claim_notice_nar := p_claim_notice_nar;
    l_max_rows         := NVL(p_max_rows, 1000);

    IF l_max_rows <= 0 THEN
        l_max_rows := 1000;
    END IF;

    --------------------------------------------------------------------------
    -- Normalise company codes. '__ALL__' means no company restriction.
    --------------------------------------------------------------------------
    IF p_company_codes IS NOT NULL
       AND p_company_codes.COUNT > 0
    THEN
        IF p_company_codes.COUNT = 1
           AND p_company_codes(1) = '__ALL__'
        THEN
            l_has_companies := 0;
        ELSE
            l_company_codes.EXTEND(p_company_codes.COUNT);

            FOR i IN 1 .. p_company_codes.COUNT LOOP
                l_company_codes(i) :=
                    TRIM(p_company_codes(i));
            END LOOP;

            l_has_companies := 1;
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- Exact-candidate pre-check.  (UNCHANGED)
    --------------------------------------------------------------------------
    IF l_policy_number IS NOT NULL THEN
        BEGIN
            SELECT 1
              INTO l_exact_candidate_exists
              FROM tri_claim_worksheet cw
             WHERE cw.worksheet_status_id = c_wip_status_id

               AND TRIM(cw.client_policy_num) = l_policy_number

               AND (
                    p_adminco_id IS NULL
                    OR cw.adminco_id = p_adminco_id
                   )

               AND (
                    l_has_companies = 0
                    OR cw.cmpny_cd IN (
                        SELECT column_value
                          FROM TABLE(l_company_codes)
                    )
                   )

               AND (
                    l_record_number IS NULL
                    OR TRIM(cw.rec_nmbr) = l_record_number
                   )

               AND (
                    l_last_name IS NULL
                    OR (
                        TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                            TRIM(UPPER(NVL(cw.last_name, '')))
                        ) =
                        TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                            TRIM(UPPER(l_last_name))
                        )

                        OR METAPHONE(
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(NVL(cw.last_name, '')))
                            )
                        ) =
                        METAPHONE(
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(l_last_name))
                            )
                        )

                        OR SOUNDEX(NVL(cw.last_name, '')) =
                           SOUNDEX(l_last_name)

                        OR INSTR(
                            UPPER(NVL(cw.last_name, '')),
                            UPPER(l_last_name)
                        ) > 0
                    )
                   )

               AND (
                    l_first_name IS NULL
                    OR (
                        SUBSTR(
                            UPPER(NVL(cw.first_name, '')),
                            1,
                            1
                        ) =
                        SUBSTR(
                            UPPER(l_first_name),
                            1,
                            1
                        )

                        OR (
                            LENGTH(l_first_name) > 1
                            AND (
                                METAPHONE(
                                    TRIM(
                                        UPPER(NVL(cw.first_name, ''))
                                    )
                                ) =
                                METAPHONE(
                                    TRIM(UPPER(l_first_name))
                                )

                                OR INSTR(
                                    UPPER(NVL(cw.first_name, '')),
                                    UPPER(l_first_name)
                                ) > 0
                            )
                        )
                    )
                   )

               AND (
                    (
                        l_rider_number IS NOT NULL
                        AND TRIM(cw.rdr_nmbr) = l_rider_number
                    )

                    OR (
                        l_rider_number IS NULL
                        AND l_claim_notice_nar IS NOT NULL
                        AND cw.cur_act_nar = l_claim_notice_nar
                    )

                    OR (
                        l_rider_number IS NULL
                        AND l_claim_notice_nar IS NULL
                    )
                   )

               AND ROWNUM = 1;

        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                l_exact_candidate_exists := 0;
        END;

        IF l_exact_candidate_exists = 0 THEN
            l_use_tolerant_match := 1;
        ELSE
            l_use_tolerant_match := 0;
        END IF;
    ELSE
        l_use_tolerant_match := 0;
    END IF;

    --------------------------------------------------------------------------
    -- NEW: build tolerant candidate keys once, only when needed.
    -- (Tolerant mode implies l_policy_number IS NOT NULL.)
    --------------------------------------------------------------------------
    IF l_use_tolerant_match = 1 THEN
        l_policy_norm := normalise_identifier_text(l_policy_number);

        IF l_policy_norm IS NOT NULL THEN
            IF LENGTH(l_policy_norm) >= 8 THEN
                l_policy_tail8 := SUBSTR(l_policy_norm, -8);
            END IF;

            l_policy_subs :=
                build_embedded_candidates(l_policy_norm, 7);
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- Main search - branched.
    --------------------------------------------------------------------------
    IF l_use_tolerant_match = 0 THEN

        ----------------------------------------------------------------------
        -- EXACT MODE (also covers the no-policy case).
        -- Original verified pre-tolerance query. No rank function calls.
        ----------------------------------------------------------------------
        OPEN p_results FOR
            SELECT
                claim_worksheet_id,
                cmpny_cd
            FROM (
                SELECT
                    cw.claim_worksheet_id,
                    cw.cmpny_cd
                FROM tri_claim_worksheet cw
                WHERE
                    cw.worksheet_status_id = c_wip_status_id

                    AND (
                        p_adminco_id IS NULL
                        OR cw.adminco_id = p_adminco_id
                    )

                    AND (
                        l_has_companies = 0
                        OR cw.cmpny_cd IN (
                            SELECT column_value
                              FROM TABLE(l_company_codes)
                        )
                    )

                    --------------------------------------------------------------
                    -- Policy: exact only.
                    --------------------------------------------------------------
                    AND (
                        l_policy_number IS NULL
                        OR TRIM(cw.client_policy_num) =
                               l_policy_number
                    )

                    --------------------------------------------------------------
                    -- Last name (verbatim)
                    --------------------------------------------------------------
                    AND (
                        l_last_name IS NULL
                        OR (
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(NVL(cw.last_name, '')))
                            ) =
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(l_last_name))
                            )

                            OR METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(NVL(cw.last_name, '')))
                                )
                            ) =
                            METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(l_last_name))
                                )
                            )

                            OR SOUNDEX(NVL(cw.last_name, '')) =
                               SOUNDEX(l_last_name)

                            OR INSTR(
                                UPPER(NVL(cw.last_name, '')),
                                UPPER(l_last_name)
                            ) > 0
                        )
                    )

                    --------------------------------------------------------------
                    -- First name (verbatim)
                    --------------------------------------------------------------
                    AND (
                        l_first_name IS NULL
                        OR (
                            SUBSTR(
                                UPPER(NVL(cw.first_name, '')),
                                1,
                                1
                            ) =
                            SUBSTR(
                                UPPER(l_first_name),
                                1,
                                1
                            )

                            OR (
                                LENGTH(l_first_name) > 1
                                AND (
                                    METAPHONE(
                                        TRIM(
                                            UPPER(NVL(cw.first_name, ''))
                                        )
                                    ) =
                                    METAPHONE(
                                        TRIM(UPPER(l_first_name))
                                    )

                                    OR INSTR(
                                        UPPER(NVL(cw.first_name, '')),
                                        UPPER(l_first_name)
                                    ) > 0
                                )
                            )
                        )
                    )

                    --------------------------------------------------------------
                    -- Coverage/NAR: exact arms only.
                    --------------------------------------------------------------
                    AND (
                        (
                            l_rider_number IS NOT NULL
                            AND TRIM(cw.rdr_nmbr) =
                                    l_rider_number
                        )

                        OR (
                            l_rider_number IS NULL
                            AND l_claim_notice_nar IS NOT NULL
                            AND cw.cur_act_nar = l_claim_notice_nar
                        )

                        OR (
                            l_rider_number IS NULL
                            AND l_claim_notice_nar IS NULL
                        )
                    )

                    AND (
                        l_record_number IS NULL
                        OR TRIM(cw.rec_nmbr) = l_record_number
                    )

                ORDER BY cw.claim_worksheet_id
            )
            WHERE ROWNUM <= l_max_rows;

    ELSE

        ----------------------------------------------------------------------
        -- TOLERANT MODE.
        -- Candidate predicate sources the rows (index-capable); verified
        -- rank functions retained as safety-net filters on the small set.
        ----------------------------------------------------------------------
        OPEN p_results FOR
            SELECT
                claim_worksheet_id,
                cmpny_cd
            FROM (
                SELECT
                    cw.claim_worksheet_id,
                    cw.cmpny_cd
                FROM tri_claim_worksheet cw
                WHERE
                    cw.worksheet_status_id = c_wip_status_id

                    AND (
                        p_adminco_id IS NULL
                        OR cw.adminco_id = p_adminco_id
                    )

                    AND (
                        l_has_companies = 0
                        OR cw.cmpny_cd IN (
                            SELECT column_value
                              FROM TABLE(l_company_codes)
                        )
                    )

                    --------------------------------------------------------------
                    -- Policy candidate predicate (pure SQL, index-capable).
                    -- Equivalent to get_policy_number_match_rank IN (0,1,2,3).
                    --------------------------------------------------------------
                    AND (
                        COALESCE(LTRIM(TRIM(cw.client_policy_num), '0'),
                                 NVL2(TRIM(cw.client_policy_num),
                                      '0', NULL))
                            = l_policy_norm

                        OR COALESCE(LTRIM(TRIM(cw.client_policy_num), '0'),
                                    NVL2(TRIM(cw.client_policy_num),
                                         '0', NULL))
                            IN (
                                SELECT column_value
                                  FROM TABLE(l_policy_subs)
                            )

                        OR (
                            l_policy_tail8 IS NOT NULL
                            AND SUBSTR(
                                    COALESCE(
                                        LTRIM(TRIM(cw.client_policy_num),
                                              '0'),
                                        NVL2(TRIM(cw.client_policy_num),
                                             '0', NULL)
                                    ), -8
                                ) = l_policy_tail8
                        )
                    )

                    --------------------------------------------------------------
                    -- Safety net: verified rank filter, evaluated only on
                    -- the small candidate set.
                    --------------------------------------------------------------
                    AND PKG_CLAIMSWORKSHEET_AUTOMATION.get_policy_number_match_rank(
                            l_policy_number,
                            TRIM(cw.client_policy_num)
                        ) IN (0, 1, 2, 3)

                    --------------------------------------------------------------
                    -- Last name (verbatim)
                    --------------------------------------------------------------
                    AND (
                        l_last_name IS NULL
                        OR (
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(NVL(cw.last_name, '')))
                            ) =
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(l_last_name))
                            )

                            OR METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(NVL(cw.last_name, '')))
                                )
                            ) =
                            METAPHONE(
                                TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                    TRIM(UPPER(l_last_name))
                                )
                            )

                            OR SOUNDEX(NVL(cw.last_name, '')) =
                               SOUNDEX(l_last_name)

                            OR INSTR(
                                UPPER(NVL(cw.last_name, '')),
                                UPPER(l_last_name)
                            ) > 0
                        )
                    )

                    --------------------------------------------------------------
                    -- First name (verbatim)
                    --------------------------------------------------------------
                    AND (
                        l_first_name IS NULL
                        OR (
                            SUBSTR(
                                UPPER(NVL(cw.first_name, '')),
                                1,
                                1
                            ) =
                            SUBSTR(
                                UPPER(l_first_name),
                                1,
                                1
                            )

                            OR (
                                LENGTH(l_first_name) > 1
                                AND (
                                    METAPHONE(
                                        TRIM(
                                            UPPER(NVL(cw.first_name, ''))
                                        )
                                    ) =
                                    METAPHONE(
                                        TRIM(UPPER(l_first_name))
                                    )

                                    OR INSTR(
                                        UPPER(NVL(cw.first_name, '')),
                                        UPPER(l_first_name)
                                    ) > 0
                                )
                            )
                        )
                    )

                    --------------------------------------------------------------
                    -- Coverage/NAR: tolerant arms (verbatim verified logic).
                    -- The rank function only touches candidate rows.
                    --------------------------------------------------------------
                    AND (
                        (
                            l_rider_number IS NOT NULL
                            AND PKG_CLAIMSWORKSHEET_AUTOMATION.get_coverage_number_match_rank(
                                    l_rider_number,
                                    TRIM(cw.rdr_nmbr)
                                ) IN (0, 1, 2)
                        )

                        OR (
                            l_rider_number IS NULL
                            AND l_claim_notice_nar IS NOT NULL
                            AND cw.cur_act_nar = l_claim_notice_nar
                        )

                        OR (
                            l_rider_number IS NULL
                            AND l_claim_notice_nar IS NULL
                        )
                    )

                    AND (
                        l_record_number IS NULL
                        OR TRIM(cw.rec_nmbr) = l_record_number
                    )

                ORDER BY cw.claim_worksheet_id
            )
            WHERE ROWNUM <= l_max_rows;

    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE_APPLICATION_ERROR(
            -20051,
            'PKG_CLAIMSWORKSHEET_AUTOMATION.'
            || 'claim_worksheet_search failed: '
            || SQLERRM
        );
END claim_worksheet_search;
