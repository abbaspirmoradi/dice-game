--------------------------------------------------------------------------------
-- PACKAGE SPEC
--------------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE PKG_CLAIMSWORKSHEET_AUTOMATION AS

  ------------------------------------------------------------------------------
  -- PL/SQL associative array type
  -- Used to safely pass company codes from ODP.NET
  ------------------------------------------------------------------------------
  TYPE t_varchar2_tab IS TABLE OF VARCHAR2(400) INDEX BY PLS_INTEGER;

    ------------------------------------------------------------------
    -- Shared policy-number match function.
    --
    -- Return values:
    --   0 = raw exact
    --   1 = exact after removing leading zeroes
    --   2 = normalised DB value appears inside normalised incoming value
    --   3 = significant trailing overlap
    --   9 = no match
    ------------------------------------------------------------------
    FUNCTION get_policy_number_match_rank (
        p_incoming_policy_number IN VARCHAR2,
        p_db_policy_number       IN VARCHAR2
    ) RETURN NUMBER DETERMINISTIC;

    ------------------------------------------------------------------
    -- Shared coverage/rider match function.
    --
    -- Return values:
    --   0 = raw exact, or incoming coverage/rider is not supplied
    --   1 = exact after removing leading zeroes
    --   2 = normalised DB value appears inside normalised incoming value
    --   9 = no match
    ------------------------------------------------------------------
    FUNCTION get_coverage_number_match_rank (
        p_incoming_coverage_number IN VARCHAR2,
        p_db_coverage_number       IN VARCHAR2
    ) RETURN NUMBER DETERMINISTIC;

  ------------------------------------------------------------------------------
  -- Claim search procedure
  -- Returns 0..N claim IDs with their company codes
  ------------------------------------------------------------------------------
  PROCEDURE claim_search (
      p_policy_number   IN  VARCHAR2,
      p_last_name       IN  VARCHAR2,
      p_first_name      IN  VARCHAR2,
      p_company_codes   IN  t_varchar2_tab,
      p_adminco_id      IN  NUMBER DEFAULT NULL,
      p_claim_notice_nar   IN  NUMBER DEFAULT NULL,
      p_rider_number       IN  VARCHAR2 DEFAULT NULL,
	  p_record_number      IN  VARCHAR2,
      p_max_rows        IN  PLS_INTEGER DEFAULT 1000,
      p_results         OUT SYS_REFCURSOR
  );


  -- Claim worksheet search (WIP only)
  ------------------------------------------------------------------------------
  PROCEDURE claim_worksheet_search (
      p_policy_number   IN  VARCHAR2,
      p_last_name       IN  VARCHAR2,
      p_first_name      IN  VARCHAR2,
      p_company_codes   IN  t_varchar2_tab,
      p_adminco_id      IN  NUMBER DEFAULT NULL,
      p_claim_notice_nar   IN  NUMBER DEFAULT NULL,
      p_rider_number       IN  VARCHAR2 DEFAULT NULL,
	  p_record_number      IN  VARCHAR2,
      p_max_rows        IN  PLS_INTEGER DEFAULT 1000,
      p_results         OUT SYS_REFCURSOR
  );

PROCEDURE inforcealpha_search (
    p_admin_company_code IN  VARCHAR2,
    p_company_codes      IN  t_varchar2_tab,
    p_policy_number      IN  VARCHAR2,
    p_last_name          IN  VARCHAR2,
    p_first_name         IN  VARCHAR2,
    p_dob                IN  DATE,
    p_rider_number       IN  VARCHAR2,
	p_record_number      IN  VARCHAR2,
    p_nar                IN  NUMBER,
    o_match_status       OUT VARCHAR2,
    o_result             OUT SYS_REFCURSOR
);

PROCEDURE edi_trans_search (
    p_use_inforce_only     IN CHAR,     -- 'Y' or 'N'
    p_admin_company_code   IN VARCHAR2,
    p_company_codes        IN t_varchar2_tab,  -- MULTI company codes
    p_policy_number        IN VARCHAR2,
    p_record_number        IN VARCHAR2,
    p_rider_number         IN VARCHAR2,
    p_last_name            IN VARCHAR2,
    p_first_name_initial   IN VARCHAR2,
    p_dob                  IN DATE,
    p_date_of_death        IN DATE,
    p_claim_notice_nar     IN NUMBER,
    o_match_status         OUT VARCHAR2,
    o_result               OUT SYS_REFCURSOR
);

END PKG_CLAIMSWORKSHEET_AUTOMATION;
/

--------------------------------------------------------------------------------
-- PACKAGE BODY
--------------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE BODY PKG_CLAIMSWORKSHEET_AUTOMATION AS

  ------------------------------------------------------------------------------
  -- Private helper.
  --
  -- Removes leading zeroes only.
  -- Does not remove embedded zeroes.
  -- Does not remove non-numeric characters.
  --
  -- NULL      -> NULL
  -- '000123' -> '123'
  -- '000000' -> '0'
  ------------------------------------------------------------------------------
  FUNCTION normalise_identifier_text (
      p_value IN VARCHAR2
  ) RETURN VARCHAR2 DETERMINISTIC
  IS
      v_trimmed VARCHAR2(4000);
      v_norm    VARCHAR2(4000);
  BEGIN
      v_trimmed := TRIM(p_value);

      IF v_trimmed IS NULL THEN
          RETURN NULL;
      END IF;

      v_norm := LTRIM(v_trimmed, '0');

      IF v_norm IS NULL THEN
          RETURN '0';
      END IF;

      RETURN v_norm;
  END normalise_identifier_text;


  ------------------------------------------------------------------------------
  -- Shared policy-number matching.
  ------------------------------------------------------------------------------
  FUNCTION get_policy_number_match_rank (
      p_incoming_policy_number IN VARCHAR2,
      p_db_policy_number       IN VARCHAR2
  ) RETURN NUMBER DETERMINISTIC
  IS
      c_min_policy_embedded_len CONSTANT PLS_INTEGER := 7;
      c_min_policy_overlap_len  CONSTANT PLS_INTEGER := 8;

      v_incoming_raw  VARCHAR2(4000);
      v_db_raw        VARCHAR2(4000);
      v_incoming_norm VARCHAR2(4000);
      v_db_norm       VARCHAR2(4000);
  BEGIN
      v_incoming_raw := TRIM(p_incoming_policy_number);
      v_db_raw       := TRIM(p_db_policy_number);

      IF v_incoming_raw IS NULL OR v_db_raw IS NULL THEN
          RETURN 9;
      END IF;

      --------------------------------------------------------------------------
      -- 0 = raw exact.
      --------------------------------------------------------------------------
      IF v_db_raw = v_incoming_raw THEN
          RETURN 0;
      END IF;

      v_incoming_norm := normalise_identifier_text(v_incoming_raw);
      v_db_norm       := normalise_identifier_text(v_db_raw);

      IF v_incoming_norm IS NULL OR v_db_norm IS NULL THEN
          RETURN 9;
      END IF;

      --------------------------------------------------------------------------
      -- 1 = exact after removing leading zeroes.
      --------------------------------------------------------------------------
      IF v_db_norm = v_incoming_norm THEN
          RETURN 1;
      END IF;

      --------------------------------------------------------------------------
      -- 2 = controlled embedded match.
      --------------------------------------------------------------------------
      IF LENGTH(v_db_norm) >= c_min_policy_embedded_len
         AND LENGTH(v_incoming_norm) > LENGTH(v_db_norm)
         AND INSTR(v_incoming_norm, v_db_norm) > 0
      THEN
          RETURN 2;
      END IF;

      --------------------------------------------------------------------------
      -- 3 = significant trailing overlap.
      --------------------------------------------------------------------------
      IF LENGTH(v_db_norm) >= c_min_policy_overlap_len
         AND LENGTH(v_incoming_norm) >= c_min_policy_overlap_len
         AND SUBSTR(v_db_norm, -c_min_policy_overlap_len) =
             SUBSTR(v_incoming_norm, -c_min_policy_overlap_len)
      THEN
          RETURN 3;
      END IF;

      RETURN 9;
  END get_policy_number_match_rank;


  ------------------------------------------------------------------------------
  -- Shared coverage/rider matching.
  ------------------------------------------------------------------------------
  FUNCTION get_coverage_number_match_rank (
      p_incoming_coverage_number IN VARCHAR2,
      p_db_coverage_number       IN VARCHAR2
  ) RETURN NUMBER DETERMINISTIC
  IS
      c_min_coverage_embedded_len CONSTANT PLS_INTEGER := 3;

      v_incoming_raw  VARCHAR2(4000);
      v_db_raw        VARCHAR2(4000);
      v_incoming_norm VARCHAR2(4000);
      v_db_norm       VARCHAR2(4000);
  BEGIN
      v_incoming_raw := TRIM(p_incoming_coverage_number);
      v_db_raw       := TRIM(p_db_coverage_number);

      --------------------------------------------------------------------------
      -- Existing behaviour:
      -- If incoming rider/coverage is not supplied, do not restrict by it.
      --------------------------------------------------------------------------
      IF v_incoming_raw IS NULL THEN
          RETURN 0;
      END IF;

      IF v_db_raw IS NULL THEN
          RETURN 9;
      END IF;

      --------------------------------------------------------------------------
      -- 0 = raw exact.
      --------------------------------------------------------------------------
      IF v_db_raw = v_incoming_raw THEN
          RETURN 0;
      END IF;

      v_incoming_norm := normalise_identifier_text(v_incoming_raw);
      v_db_norm       := normalise_identifier_text(v_db_raw);

      IF v_incoming_norm IS NULL OR v_db_norm IS NULL THEN
          RETURN 9;
      END IF;

      --------------------------------------------------------------------------
      -- 1 = exact after removing leading zeroes.
      --------------------------------------------------------------------------
      IF v_db_norm = v_incoming_norm THEN
          RETURN 1;
      END IF;

      --------------------------------------------------------------------------
      -- 2 = controlled embedded match.
      --------------------------------------------------------------------------
      IF LENGTH(v_db_norm) >= c_min_coverage_embedded_len
         AND LENGTH(v_incoming_norm) > LENGTH(v_db_norm)
         AND INSTR(v_incoming_norm, v_db_norm) > 0
      THEN
          RETURN 2;
      END IF;

      RETURN 9;
  END get_coverage_number_match_rank;




  ------------------------------------------------------------------------------
  -- Claim search.
  ------------------------------------------------------------------------------
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
    -- Exact candidate pre-check.
    --
    -- If policy is null, no policy filtering is required and exact mode is
    -- retained.
    --
    -- If rider is supplied, an exact worksheet rider must exist.
    -- If rider is not supplied but NAR is supplied, the existing NAR rule is
    -- used.
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
    -- Search claims.
    --
    -- Exact mode:
    --   exact policy and exact rider.
    --
    -- Tolerant mode:
    --   policy ranks 0-3 and coverage ranks 0-2.
    --------------------------------------------------------------------------
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
                ----------------------------------------------------------------
                -- Policy number:
                -- exact first, tolerant only if no exact candidate exists.
                ----------------------------------------------------------------
                (
                    l_policy_number IS NULL

                    OR (
                        v_use_tolerant_match = 0
                        AND TRIM(d.client_policy_num) =
                                l_policy_number
                    )

                    OR (
                        v_use_tolerant_match = 1
                        AND PKG_CLAIMSWORKSHEET_AUTOMATION.get_policy_number_match_rank(
                                l_policy_number,
                                TRIM(d.client_policy_num)
                            ) IN (0, 1, 2, 3)
                    )
                )

                ----------------------------------------------------------------
                -- Company codes
                ----------------------------------------------------------------
                AND (
                    l_has_companies = 0
                    OR z.lowcode IN (
                        SELECT column_value
                          FROM TABLE(l_company_codes)
                    )
                )

                ----------------------------------------------------------------
                -- Admin company
                ----------------------------------------------------------------
                AND (
                    p_adminco_id IS NULL
                    OR y.master_typ_id_adminco =
                           p_adminco_id
                )

                ----------------------------------------------------------------
                -- Record number remains exact
                ----------------------------------------------------------------
                AND (
                    l_record_number IS NULL
                    OR TRIM(a.rec_nmbr) =
                           l_record_number
                )

                ----------------------------------------------------------------
                -- Last name
                ----------------------------------------------------------------
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

                ----------------------------------------------------------------
                -- First name
                ----------------------------------------------------------------
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

                ----------------------------------------------------------------
                -- Rider/NAR:
                --
                -- If rider supplied:
                --   exact rider in exact mode;
                --   tolerant rider in tolerant mode.
                --
                -- If rider not supplied and NAR supplied:
                --   retain the existing exact-NAR behaviour.
                ----------------------------------------------------------------
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
                                    AND (
                                        (
                                            v_use_tolerant_match = 0
                                            AND TRIM(cw.rdr_nmbr) =
                                                   l_rider_number
                                        )
                                        OR (
                                            v_use_tolerant_match = 1
                                            AND PKG_CLAIMSWORKSHEET_AUTOMATION.get_coverage_number_match_rank(
                                                    l_rider_number,
                                                    TRIM(cw.rdr_nmbr)
                                                ) IN (0, 1, 2)
                                        )
                                    )
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

EXCEPTION
    WHEN OTHERS THEN
        RAISE_APPLICATION_ERROR(
            -20050,
            'PKG_CLAIMSWORKSHEET_AUTOMATION.claim_search failed: '
            || SQLERRM
        );
END claim_search;



  ------------------------------------------------------------------------------
  -- Claim worksheet search.
  ------------------------------------------------------------------------------
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
    --
    -- 0 = exact policy/coverage mode
    -- 1 = tolerant policy/coverage mode
    --------------------------------------------------------------------------
    l_exact_candidate_exists PLS_INTEGER := 0;
    l_use_tolerant_match     PLS_INTEGER := 0;

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

    --------------------------------------------------------------------------
    -- Protect against invalid maximum-row values
    --------------------------------------------------------------------------
    IF l_max_rows <= 0 THEN
        l_max_rows := 1000;
    END IF;

    --------------------------------------------------------------------------
    -- Normalise company codes.
    --
    -- '__ALL__' means no company restriction.
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
    -- Exact-candidate pre-check.
    --
    -- This query intentionally includes all eligibility filters from the
    -- main search. Tolerant mode is enabled only when no eligible exact
    -- policy/coverage candidate exists.
    --
    -- Policy:
    --   Exact policy is required when policy was supplied.
    --
    -- Coverage/NAR:
    --   1. If coverage was supplied, exact coverage is required.
    --   2. Otherwise, if NAR was supplied, exact NAR is required.
    --   3. Otherwise, neither coverage nor NAR restricts the search.
    --------------------------------------------------------------------------
    IF l_policy_number IS NOT NULL THEN
        BEGIN
            SELECT 1
              INTO l_exact_candidate_exists
              FROM tri_claim_worksheet cw
             WHERE cw.worksheet_status_id = c_wip_status_id

               ---------------------------------------------------------------
               -- Exact policy
               ---------------------------------------------------------------
               AND TRIM(cw.client_policy_num) = l_policy_number

               ---------------------------------------------------------------
               -- Optional admin company
               ---------------------------------------------------------------
               AND (
                    p_adminco_id IS NULL
                    OR cw.adminco_id = p_adminco_id
                   )

               ---------------------------------------------------------------
               -- Optional company-code collection
               ---------------------------------------------------------------
               AND (
                    l_has_companies = 0
                    OR cw.cmpny_cd IN (
                        SELECT column_value
                          FROM TABLE(l_company_codes)
                    )
                   )

               ---------------------------------------------------------------
               -- Optional record number remains exact
               ---------------------------------------------------------------
               AND (
                    l_record_number IS NULL
                    OR TRIM(cw.rec_nmbr) = l_record_number
                   )

               ---------------------------------------------------------------
               -- Existing last-name matching
               ---------------------------------------------------------------
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

               ---------------------------------------------------------------
               -- Existing first-name matching
               ---------------------------------------------------------------
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

               ---------------------------------------------------------------
               -- Exact coverage or existing NAR fallback
               ---------------------------------------------------------------
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
        ----------------------------------------------------------------------
        -- No incoming policy means there is no policy tolerance decision.
        -- Preserve the original optional-policy behaviour.
        ----------------------------------------------------------------------
        l_use_tolerant_match := 0;
    END IF;

    --------------------------------------------------------------------------
    -- Main search
    --------------------------------------------------------------------------
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
                --------------------------------------------------------------
                -- WIP worksheets only
                --------------------------------------------------------------
                cw.worksheet_status_id = c_wip_status_id

                --------------------------------------------------------------
                -- Optional admin company
                --------------------------------------------------------------
                AND (
                    p_adminco_id IS NULL
                    OR cw.adminco_id = p_adminco_id
                )

                --------------------------------------------------------------
                -- Optional company-code collection
                --------------------------------------------------------------
                AND (
                    l_has_companies = 0
                    OR cw.cmpny_cd IN (
                        SELECT column_value
                          FROM TABLE(l_company_codes)
                    )
                )

                --------------------------------------------------------------
                -- Policy matching
                --
                -- Exact mode:
                --   raw exact policy only.
                --
                -- Tolerant mode:
                --   accepted shared policy ranks 0, 1, 2 and 3.
                --------------------------------------------------------------
                AND (
                    l_policy_number IS NULL

                    OR (
                        l_use_tolerant_match = 0
                        AND TRIM(cw.client_policy_num) =
                                l_policy_number
                    )

                    OR (
                        l_use_tolerant_match = 1
                        AND PKG_CLAIMSWORKSHEET_AUTOMATION.get_policy_number_match_rank(
                                l_policy_number,
                                TRIM(cw.client_policy_num)
                            ) IN (0, 1, 2, 3)
                    )
                )

                --------------------------------------------------------------
                -- Optional last name
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
                -- Optional first name
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
                -- Coverage/NAR matching
                --
                -- If coverage is supplied:
                --   exact mode uses exact coverage;
                --   tolerant mode uses shared ranks 0, 1 and 2.
                --
                -- If coverage is not supplied but NAR is supplied:
                --   preserve existing exact NAR behaviour.
                --
                -- If neither is supplied:
                --   do not restrict by coverage or NAR.
                --------------------------------------------------------------
                AND (
                    (
                        l_rider_number IS NOT NULL
                        AND (
                            (
                                l_use_tolerant_match = 0
                                AND TRIM(cw.rdr_nmbr) =
                                        l_rider_number
                            )

                            OR (
                                l_use_tolerant_match = 1
                                AND PKG_CLAIMSWORKSHEET_AUTOMATION.get_coverage_number_match_rank(
                                        l_rider_number,
                                        TRIM(cw.rdr_nmbr)
                                    ) IN (0, 1, 2)
                            )
                        )
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

                --------------------------------------------------------------
                -- Record number remains exact
                --------------------------------------------------------------
                AND (
                    l_record_number IS NULL
                    OR TRIM(cw.rec_nmbr) = l_record_number
                )

            ORDER BY cw.claim_worksheet_id
        )
        WHERE ROWNUM <= l_max_rows;

EXCEPTION
    WHEN OTHERS THEN
        RAISE_APPLICATION_ERROR(
            -20051,
            'PKG_CLAIMSWORKSHEET_AUTOMATION.'
            || 'claim_worksheet_search failed: '
            || SQLERRM
        );
END claim_worksheet_search;

  ------------------------------------------------------------------------------
  -- Inforce Alpha search.
  ------------------------------------------------------------------------------
PROCEDURE inforcealpha_search (
    p_admin_company_code IN  VARCHAR2,
    p_company_codes      IN  t_varchar2_tab,
    p_policy_number      IN  VARCHAR2,
    p_last_name          IN  VARCHAR2,
    p_first_name         IN  VARCHAR2,
    p_dob                IN  DATE,
    p_rider_number       IN  VARCHAR2,
    p_record_number      IN  VARCHAR2,
    p_nar                IN  NUMBER,
    o_match_status       OUT VARCHAR2,
    o_result             OUT SYS_REFCURSOR
) IS
    ------------------------------------------------------------------
    -- Normalised inputs
    ------------------------------------------------------------------
    v_first_initial   CHAR(1);
    v_proc_last_name  VARCHAR2(200);

    ------------------------------------------------------------------
    -- Multi-company support
    ------------------------------------------------------------------
    l_company_codes   SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST();
    l_has_companies   PLS_INTEGER := 0;

    ------------------------------------------------------------------
    -- Recommended record
    ------------------------------------------------------------------
    v_best_infrc_alpha_id NUMBER := NULL;
    v_best_match_type     VARCHAR2(30) := NULL;

    ------------------------------------------------------------------
    -- Review flags
    ------------------------------------------------------------------
    v_has_dob_tolerance   NUMBER := 0;
    v_has_name_tolerance  NUMBER := 0;

BEGIN
    o_match_status := 'NO_MATCH';

    ------------------------------------------------------------------
    -- Defensive initialisation
    ------------------------------------------------------------------
    v_first_initial :=
        CASE
            WHEN p_first_name IS NOT NULL
            THEN SUBSTR(UPPER(TRIM(p_first_name)), 1, 1)
            ELSE NULL
        END;

    v_proc_last_name :=
        CASE
            WHEN p_last_name IS NOT NULL
            THEN TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                     TRIM(UPPER(p_last_name))
                 )
            ELSE NULL
        END;

    ------------------------------------------------------------------
    -- Copy company codes.
    -- '__ALL__' means no company restriction.
    ------------------------------------------------------------------
    IF p_company_codes IS NOT NULL
       AND p_company_codes.COUNT > 0
    THEN
        IF p_company_codes.COUNT = 1
           AND p_company_codes(1) = '__ALL__'
        THEN
            l_has_companies := 0;
        ELSE
            l_company_codes := SYS.ODCIVARCHAR2LIST();
            l_company_codes.EXTEND(p_company_codes.COUNT);

            FOR i IN 1 .. p_company_codes.COUNT LOOP
                l_company_codes(i) := p_company_codes(i);
            END LOOP;

            l_has_companies := 1;
        END IF;
    END IF;

    ------------------------------------------------------------------
    -- STEP 2: Determine recommended match from the associated set.
    ------------------------------------------------------------------
    BEGIN
        WITH base_source AS (
            SELECT
                a.infrc_alpha_id      AS infrc_alpha_id,
                a.admn_cmpny_cd       AS admn_cmpny_cd,
                a.rnsrnc_cmpny_cd     AS company_code,
                a.plcy_nmbr           AS policy_number,
                a.trunc_plcy_nmbr     AS trunc_policy_number,
                a.rdr_nmbr            AS coverage_number,
                a.rec_nmbr            AS record_number,
                a.lst_nm              AS last_name,
                a.frst_nm             AS first_name,
                a.dob                 AS date_of_birth,
                a.jnt_insrd           AS secondary_insured,
                t.rv_reins_nar_1      AS nar,
                a.src_cd              AS src,
                a.trns_typ            AS trans_type,
                a.status_cd           AS status,
                a.cssn_nmbr           AS cession_number,
                a.rnsrnc_cmpny_sub_cd AS sub_code,
                a.plcy_dt             AS policy_date,
                t.from_dt             AS from_dt,
                t.to_dt               AS to_dt,

                ----------------------------------------------------------
                -- Numeric distance used only as a ranking tie-breaker.
                ----------------------------------------------------------
                CASE
                    WHEN p_nar IS NOT NULL
                     AND t.rv_reins_nar_1 IS NOT NULL
                    THEN ABS(t.rv_reins_nar_1 - p_nar)
                    ELSE NULL
                END AS nar_diff

            FROM tri_v_infrc_alpha_soundex a

            JOIN tri_infrc_alpha t
              ON t.infrc_alpha_id = a.infrc_alpha_id

            WHERE 1 = 1

              ------------------------------------------------------------
              -- Last-name enhanced search.
              ------------------------------------------------------------
              AND (
                    p_last_name IS NULL
                 OR (
                        METAPHONE(
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(a.lst_nm))
                            )
                        ) =
                        METAPHONE(
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(p_last_name))
                            )
                        )

                     OR a.lst_nm_soundex = SOUNDEX(p_last_name)

                     OR INSTR(
                            UPPER(a.lst_nm),
                            UPPER(p_last_name)
                        ) > 0
                    )
              )

              ------------------------------------------------------------
              -- First-name enhanced search.
              ------------------------------------------------------------
              AND (
                    p_first_name IS NULL
                 OR (
                        (
                            LENGTH(TRIM(p_first_name)) = 1
                            AND SUBSTR(
                                    a.frst_nm_metaphone,
                                    1,
                                    1
                                ) =
                                UPPER(
                                    SUBSTR(
                                        p_first_name,
                                        1,
                                        1
                                    )
                                )
                        )

                     OR (
                            LENGTH(TRIM(p_first_name)) > 1
                            AND (
                                   a.frst_nm_metaphone =
                                       METAPHONE(
                                           UPPER(p_first_name)
                                       )

                                OR SUBSTR(a.frst_nm, 1, 1) =
                                   UPPER(
                                       SUBSTR(
                                           p_first_name,
                                           1,
                                           1
                                       )
                                   )
                            )
                        )
                    )
              )

              ------------------------------------------------------------
              -- Associated-list DOB filter remains exact by calendar day.
              ------------------------------------------------------------
              AND (
                    p_dob IS NULL
                 OR TRUNC(a.dob) = TRUNC(p_dob)
              )

              AND ROWNUM <= 1000
        ),

        base_associated AS (
            SELECT
                b.infrc_alpha_id      AS infrc_alpha_id,
                b.admn_cmpny_cd       AS admn_cmpny_cd,
                b.company_code        AS company_code,
                b.policy_number       AS policy_number,
                b.trunc_policy_number AS trunc_policy_number,
                b.coverage_number     AS coverage_number,
                b.record_number       AS record_number,
                b.last_name           AS last_name,
                b.first_name          AS first_name,
                b.date_of_birth       AS date_of_birth,
                b.secondary_insured   AS secondary_insured,
                b.nar                 AS nar,
                b.src                 AS src,
                b.trans_type          AS trans_type,
                b.status              AS status,
                b.cession_number      AS cession_number,
                b.sub_code            AS sub_code,
                b.policy_date         AS policy_date,
                b.from_dt             AS from_dt,
                b.to_dt               AS to_dt,
                b.nar_diff            AS nar_diff,

                ----------------------------------------------------------
                -- Shared policy matching logic.
                --
                -- Return values:
                --   0 = raw exact
                --   1 = exact after removing leading zeroes
                --   2 = normalised DB value appears inside incoming value
                --   3 = significant trailing overlap
                --   9 = no match
                ----------------------------------------------------------
                PKG_CLAIMSWORKSHEET_AUTOMATION.get_policy_number_match_rank(
                    p_policy_number,
                    b.policy_number
                ) AS policy_match_rank,

                ----------------------------------------------------------
                -- Shared coverage/rider matching logic.
                --
                -- Return values:
                --   0 = raw exact, or no incoming rider supplied
                --   1 = exact after removing leading zeroes
                --   2 = normalised DB value appears inside incoming value
                --   9 = no match
                ----------------------------------------------------------
                PKG_CLAIMSWORKSHEET_AUTOMATION.get_coverage_number_match_rank(
                    p_rider_number,
                    b.coverage_number
                ) AS coverage_match_rank

            FROM base_source b
        ),

        associated AS (
            SELECT
                b.*,

                ----------------------------------------------------------
                -- Match classification.
                --
                -- Policy can match by:
                --   0 raw exact
                --   1 normalised exact
                --   2 controlled embedded match
                --   3 significant trailing overlap
                --
                -- Coverage can match by:
                --   0 raw exact or rider not supplied
                --   1 normalised exact
                --   2 controlled embedded match
                --
                -- Exact/fallback priority is handled in ranking.
                ----------------------------------------------------------
                CASE
                    ------------------------------------------------------
                    -- PERFECT
                    ------------------------------------------------------
                    WHEN p_admin_company_code IS NOT NULL
                     AND b.admn_cmpny_cd = p_admin_company_code

                     AND (
                            l_has_companies = 0
                         OR b.company_code IN (
                                SELECT column_value
                                FROM TABLE(l_company_codes)
                            )
                         )

                     AND p_policy_number IS NOT NULL
                     AND b.policy_match_rank IN (0, 1, 2, 3)

                     AND p_dob IS NOT NULL
                     AND b.date_of_birth = p_dob

                     AND v_proc_last_name IS NOT NULL
                     AND TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                             TRIM(UPPER(b.last_name))
                         ) = v_proc_last_name

                     AND (
                            v_first_initial IS NULL
                         OR SUBSTR(UPPER(b.first_name), 1, 1) =
                            v_first_initial
                         )

                     AND b.coverage_match_rank IN (0, 1, 2)

                     AND (
                            p_record_number IS NULL
                         OR b.record_number = p_record_number
                         )
                    THEN 'PERFECT'

                    ------------------------------------------------------
                    -- TOLERANCE_DOB
                    ------------------------------------------------------
                    WHEN p_admin_company_code IS NOT NULL
                     AND b.admn_cmpny_cd = p_admin_company_code

                     AND (
                            l_has_companies = 0
                         OR b.company_code IN (
                                SELECT column_value
                                FROM TABLE(l_company_codes)
                            )
                         )

                     AND p_policy_number IS NOT NULL
                     AND b.policy_match_rank IN (0, 1, 2, 3)

                     AND b.date_of_birth IS NOT NULL
                     AND p_dob IS NOT NULL

                     AND ABS(
                             EXTRACT(YEAR FROM b.date_of_birth) -
                             EXTRACT(YEAR FROM p_dob)
                         ) <= 2

                     AND v_proc_last_name IS NOT NULL
                     AND TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                             TRIM(UPPER(b.last_name))
                         ) = v_proc_last_name

                     AND (
                            v_first_initial IS NULL
                         OR SUBSTR(UPPER(b.first_name), 1, 1) =
                            v_first_initial
                         )

                     AND b.coverage_match_rank IN (0, 1, 2)

                     AND (
                            p_record_number IS NULL
                         OR b.record_number = p_record_number
                         )
                    THEN 'TOLERANCE_DOB'

                    ------------------------------------------------------
                    -- TOLERANCE_NAME
                    ------------------------------------------------------
                    WHEN p_admin_company_code IS NOT NULL
                     AND b.admn_cmpny_cd = p_admin_company_code

                     AND (
                            l_has_companies = 0
                         OR b.company_code IN (
                                SELECT column_value
                                FROM TABLE(l_company_codes)
                            )
                         )

                     AND p_policy_number IS NOT NULL
                     AND b.policy_match_rank IN (0, 1, 2, 3)

                     AND b.coverage_match_rank IN (0, 1, 2)

                     AND (
                            p_record_number IS NULL
                         OR b.record_number = p_record_number
                         )
                    THEN 'TOLERANCE_NAME'

                    ELSE 'NORMAL'
                END AS match_type

            FROM base_associated b
        ),

        ranked AS (
            SELECT
                a.*,

                MAX(
                    CASE
                        WHEN a.match_type = 'TOLERANCE_DOB'
                        THEN 1
                        ELSE 0
                    END
                ) OVER () AS has_dob_tol,

                MAX(
                    CASE
                        WHEN a.match_type = 'TOLERANCE_NAME'
                        THEN 1
                        ELSE 0
                    END
                ) OVER () AS has_name_tol,

                ROW_NUMBER() OVER (
                    ORDER BY
                        --------------------------------------------------
                        -- Correct active-status fallback:
                        --
                        -- 1. Active classified matches
                        -- 2. Non-active classified matches
                        -- 3. NORMAL rows
                        --
                        -- This prevents an active NORMAL record from
                        -- outranking an inactive valid match.
                        --------------------------------------------------
                        CASE
                            WHEN a.match_type IN (
                                     'PERFECT',
                                     'TOLERANCE_DOB',
                                     'TOLERANCE_NAME'
                                 )
                             AND a.status = 'I'
                            THEN 1

                            WHEN a.match_type IN (
                                     'PERFECT',
                                     'TOLERANCE_DOB',
                                     'TOLERANCE_NAME'
                                 )
                            THEN 2

                            ELSE 3
                        END,

                        --------------------------------------------------
                        -- Existing match-type priority.
                        --------------------------------------------------
                        CASE a.match_type
                            WHEN 'PERFECT'        THEN 1
                            WHEN 'TOLERANCE_DOB'  THEN 2
                            WHEN 'TOLERANCE_NAME' THEN 3
                            ELSE 4
                        END,

                        --------------------------------------------------
                        -- Exact policy/coverage beats controlled fallback.
                        --
                        -- Policy:
                        --   0 raw exact
                        --   1 normalised exact
                        --   2 embedded partial match
                        --   3 trailing overlap
                        --
                        -- Coverage:
                        --   0 raw exact or rider not supplied
                        --   1 normalised exact
                        --   2 embedded partial match
                        --------------------------------------------------
                        a.policy_match_rank,
                        a.coverage_match_rank,

                        --------------------------------------------------
                        -- Prefer rows for which NAR comparison can be
                        -- performed.
                        --------------------------------------------------
                        CASE
                            WHEN a.match_type IN (
                                     'PERFECT',
                                     'TOLERANCE_DOB',
                                     'TOLERANCE_NAME'
                                 )
                             AND p_nar IS NOT NULL
                             AND a.nar IS NOT NULL
                            THEN 0
                            ELSE 1
                        END,

                        --------------------------------------------------
                        -- Within the same status/match bucket, choose
                        -- the NAR closest to the supplied NAR.
                        --------------------------------------------------
                        CASE
                            WHEN a.match_type IN (
                                     'PERFECT',
                                     'TOLERANCE_DOB',
                                     'TOLERANCE_NAME'
                                 )
                             AND p_nar IS NOT NULL
                             AND a.nar IS NOT NULL
                            THEN a.nar_diff
                            ELSE NULL
                        END,

                        --------------------------------------------------
                        -- Deterministic fallback.
                        --------------------------------------------------
                        a.coverage_number,
                        a.record_number,
                        a.infrc_alpha_id
                ) AS rn

            FROM associated a
        )

        SELECT
            infrc_alpha_id,
            match_type,
            has_dob_tol,
            has_name_tol
        INTO
            v_best_infrc_alpha_id,
            v_best_match_type,
            v_has_dob_tolerance,
            v_has_name_tolerance
        FROM ranked
        WHERE rn = 1;

        o_match_status := NVL(
            v_best_match_type,
            'NO_MATCH'
        );

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_best_infrc_alpha_id := NULL;
            v_best_match_type     := NULL;
            v_has_dob_tolerance   := 0;
            v_has_name_tolerance  := 0;
            o_match_status        := 'NO_MATCH';
    END;

    ------------------------------------------------------------------
    -- STEP 1: Return full associated list and recommendation flags.
    --
    -- Important:
    -- Policy, rider, and record-number matching are intentionally NOT
    -- added to this result cursor filter.
    --
    -- The associated list continues to be driven by name and DOB logic,
    -- preserving existing UI/API behaviour.
    ------------------------------------------------------------------
    OPEN o_result FOR
        SELECT *
        FROM (
            --------------------------------------------------------------
            -- Inforce Alpha associated rows.
            --------------------------------------------------------------
            SELECT
                a.infrc_alpha_id        AS infrc_alpha_id,
                a.admn_cmpny_cd         AS admn_cmpny_cd,
                c.cmpny_nm              AS reins_co_name,
                a.rnsrnc_cmpny_cd       AS company_code,
                a.plcy_nmbr             AS policy_number,
                a.plcy_dt               AS policy_date,
                a.rec_nmbr              AS record_number,
                a.rdr_nmbr              AS coverage_number,
                a.lst_nm                AS last_name,
                a.frst_nm               AS first_name,
                a.dob                   AS date_of_birth,
                t.rv_reins_nar_1        AS nar,
                t.from_dt               AS from_dt,
                t.to_dt                 AS to_dt,
                a.status_cd             AS status,
                a.cssn_nmbr             AS cession_number,
                a.src_cd                AS src,
                a.jnt_insrd             AS jnt_insrd,

                CASE
                    WHEN a.infrc_alpha_id = v_best_infrc_alpha_id
                    THEN v_best_match_type
                    ELSE 'NORMAL'
                END AS match_type,

                CASE
                    WHEN a.infrc_alpha_id = v_best_infrc_alpha_id
                    THEN 'Y'
                    ELSE 'N'
                END AS recommended_sw,

                CASE
                    WHEN a.infrc_alpha_id = v_best_infrc_alpha_id
                    THEN v_best_match_type
                    ELSE NULL
                END AS recommended_match_type

            FROM tri_v_infrc_alpha_soundex a

            JOIN tri_infrc_alpha t
              ON t.infrc_alpha_id = a.infrc_alpha_id

            LEFT JOIN tri_cmpny c
              ON c.cmpny_cd = a.rnsrnc_cmpny_cd

            WHERE 1 = 1

              ------------------------------------------------------------
              -- Last-name enhanced search.
              ------------------------------------------------------------
              AND (
                    p_last_name IS NULL
                 OR (
                        METAPHONE(
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(a.lst_nm))
                            )
                        ) =
                        METAPHONE(
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(p_last_name))
                            )
                        )

                     OR a.lst_nm_soundex = SOUNDEX(p_last_name)

                     OR INSTR(
                            UPPER(a.lst_nm),
                            UPPER(p_last_name)
                        ) > 0
                    )
              )

              ------------------------------------------------------------
              -- First-name enhanced search.
              ------------------------------------------------------------
              AND (
                    p_first_name IS NULL
                 OR (
                        (
                            LENGTH(TRIM(p_first_name)) = 1
                            AND SUBSTR(
                                    a.frst_nm_metaphone,
                                    1,
                                    1
                                ) =
                                UPPER(
                                    SUBSTR(
                                        p_first_name,
                                        1,
                                        1
                                    )
                                )
                        )

                     OR (
                            LENGTH(TRIM(p_first_name)) > 1
                            AND (
                                   a.frst_nm_metaphone =
                                       METAPHONE(
                                           UPPER(p_first_name)
                                       )

                                OR SUBSTR(a.frst_nm, 1, 1) =
                                   UPPER(
                                       SUBSTR(
                                           p_first_name,
                                           1,
                                           1
                                       )
                                   )
                            )
                        )
                    )
              )

              ------------------------------------------------------------
              -- DOB remains exact by calendar day.
              ------------------------------------------------------------
              AND (
                    p_dob IS NULL
                 OR TRUNC(a.dob) = TRUNC(p_dob)
              )

              AND ROWNUM <= 1000

            UNION

            --------------------------------------------------------------
            -- Nebula rows remain informational and never recommended.
            --------------------------------------------------------------
            SELECT
                -1                          AS infrc_alpha_id,
                CAST(NULL AS VARCHAR2(30))  AS admn_cmpny_cd,
                n.cmpny_name                AS reins_co_name,
                n.cmpny_cd                  AS company_code,
                n.cl_policy_no              AS policy_number,
                n.policy_recvd_dt           AS policy_date,
                CAST(NULL AS VARCHAR2(50))  AS record_number,
                CAST(NULL AS VARCHAR2(50))  AS coverage_number,
                n.lst_nm                    AS last_name,
                n.frst_nm                   AS first_name,
                n.dob                       AS date_of_birth,
                n.cl_amt_accepted           AS nar,
                CAST(NULL AS DATE)          AS from_dt,
                CAST(NULL AS DATE)          AS to_dt,
                n.uw_desc                   AS status,
                n.ceding_co_policy_no       AS cession_number,
                'NEB'                       AS src,
                CAST(NULL AS VARCHAR2(1))   AS jnt_insrd,
                'NORMAL'                    AS match_type,
                'N'                         AS recommended_sw,
                CAST(NULL AS VARCHAR2(30))  AS recommended_match_type

            FROM tri_nebula n

            WHERE 1 = 1

              ------------------------------------------------------------
              -- Last-name enhanced search.
              ------------------------------------------------------------
              AND (
                    p_last_name IS NULL
                 OR (
                        METAPHONE(
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(n.lst_nm))
                            )
                        ) =
                        METAPHONE(
                            TRI_PKG_RM.GET_PROCESSED_LAST_NAME(
                                TRIM(UPPER(p_last_name))
                            )
                        )

                     OR n.lst_nm_soundex = SOUNDEX(p_last_name)

                     OR INSTR(
                            UPPER(n.lst_nm),
                            UPPER(p_last_name)
                        ) > 0
                    )
              )

              ------------------------------------------------------------
              -- First-name enhanced search.
              ------------------------------------------------------------
              AND (
                    p_first_name IS NULL
                 OR (
                        (
                            LENGTH(TRIM(p_first_name)) = 1
                            AND SUBSTR(
                                    n.frst_nm_metaphone,
                                    1,
                                    1
                                ) =
                                UPPER(
                                    SUBSTR(
                                        p_first_name,
                                        1,
                                        1
                                    )
                                )
                        )

                     OR (
                            LENGTH(TRIM(p_first_name)) > 1
                            AND (
                                   n.frst_nm_metaphone =
                                       METAPHONE(
                                           UPPER(p_first_name)
                                       )

                                OR SUBSTR(n.frst_nm, 1, 1) =
                                   UPPER(
                                       SUBSTR(
                                           p_first_name,
                                           1,
                                           1
                                       )
                                   )
                            )
                        )
                    )
              )

              ------------------------------------------------------------
              -- DOB remains exact by calendar day.
              ------------------------------------------------------------
              AND (
                    p_dob IS NULL
                 OR TRUNC(n.dob) = TRUNC(p_dob)
              )

              AND ROWNUM <= 1000
        )
        ORDER BY
            date_of_birth,
            coverage_number,
            record_number;

EXCEPTION
    WHEN OTHERS THEN
        o_match_status := 'ERROR';

        OPEN o_result FOR
            SELECT NULL
            FROM dual
            WHERE 1 = 0;

        RAISE;
END inforcealpha_search;



  ------------------------------------------------------------------------------
  -- EDI Transaction search.
  ------------------------------------------------------------------------------
PROCEDURE edi_trans_search (
    p_use_inforce_only     IN CHAR,     -- 'Y' or 'N'
    p_admin_company_code   IN VARCHAR2,
    p_company_codes        IN t_varchar2_tab,
    p_policy_number        IN VARCHAR2,
    p_record_number        IN VARCHAR2,
    p_rider_number         IN VARCHAR2,
    p_last_name            IN VARCHAR2,
    p_first_name_initial   IN VARCHAR2,
    p_dob                  IN DATE,
    p_date_of_death        IN DATE,
    p_claim_notice_nar     IN NUMBER,
    o_match_status         OUT VARCHAR2,
    o_result               OUT SYS_REFCURSOR
) IS
    ------------------------------------------------------------------
    -- Company-code support
    ------------------------------------------------------------------
    l_company_codes        SYS.ODCIVARCHAR2LIST :=
                               SYS.ODCIVARCHAR2LIST();

    l_has_companies        PLS_INTEGER := 0;

    ------------------------------------------------------------------
    -- Recommended transaction
    ------------------------------------------------------------------
    v_best_edi_trans_id    NUMBER := NULL;
    v_best_match_type      VARCHAR2(30) := NULL;
    v_best_policy_number   VARCHAR2(4000) := NULL;

    ------------------------------------------------------------------
    -- Normalised incoming parameters
    ------------------------------------------------------------------
    l_use_inforce_only     CHAR(1);
    l_policy_number        VARCHAR2(4000);
    l_record_number        VARCHAR2(4000);
    l_rider_number         VARCHAR2(4000);
    l_last_name_upper      VARCHAR2(4000);
    l_first_initial_upper  VARCHAR2(10);

    ------------------------------------------------------------------
    -- Exact-first / tolerant-second matching support.
    --
    -- Existing verified behaviour:
    --   exact policy and rider matching remain in effect whenever an
    --   eligible exact candidate exists.
    --
    -- Tolerant matching is enabled only when no eligible exact candidate
    -- exists within the complete incoming search scope.
    ------------------------------------------------------------------
    v_exact_candidate_cnt  NUMBER := 0;
    v_use_tolerant_match   PLS_INTEGER := 0;

BEGIN
    ------------------------------------------------------------------
    -- Initialise output
    ------------------------------------------------------------------
    o_match_status := 'NO_MATCH';

    ------------------------------------------------------------------
    -- Normalise incoming parameters
    ------------------------------------------------------------------
    l_use_inforce_only :=
        UPPER(
            NVL(
                TRIM(p_use_inforce_only),
                'N'
            )
        );

    l_policy_number :=
        TRIM(p_policy_number);

    l_record_number :=
        TRIM(p_record_number);

    l_rider_number :=
        TRIM(p_rider_number);

    l_last_name_upper :=
        UPPER(
            TRIM(p_last_name)
        );

    l_first_initial_upper :=
        UPPER(
            TRIM(p_first_name_initial)
        );

    ------------------------------------------------------------------
    -- Policy number is required for this search.
    --
    -- Return an empty cursor with exactly the same output columns and
    -- data types as the normal result cursor.
    ------------------------------------------------------------------
    IF l_policy_number IS NULL THEN
        OPEN o_result FOR
            SELECT
                a.edi_trans_id,
                a.trns_typ,
                b.trns_typ_desc,
                a.effctv_dt AS reporting_period,
                a.from_dt,
                a.to_dt,
                a.rdr_nmbr AS coverage_number,
                a.rec_nmbr AS record_number,
                a.rv_reins_nar_1 AS nar,
                a.rv_prm_amt_1 AS premium,
                a.rv_allw_amt_1 AS allowance,

                ------------------------------------------------------
                -- Preserve existing output contract.
                ------------------------------------------------------
                (
                    NVL(a.rv_prm_amt_1, 0)
                    - NVL(a.rv_allw_amt_1, 0)
                ) AS net_premium,

                a.src_cd,
                a.status_cd,
                a.jnt_insrd,
                a.rnsrnc_cmpny_cd AS company_code,
                a.lst_nm AS last_name,
                a.frst_nm AS first_name,
                a.dob AS dob,
                a.sex AS gender,
                a.plcy_dt AS issue_date,
                a.orig_plcy_dt AS original_issue_date,
                CAST(NULL AS VARCHAR2(1)) AS recommended_sw,
                CAST(NULL AS VARCHAR2(30)) AS match_type

            FROM tri_edi_trans a

            LEFT JOIN tri_edi_trns_typ b
                   ON b.trns_typ = a.trns_typ

            WHERE 1 = 0;

        RETURN;
    END IF;

    ------------------------------------------------------------------
    -- Normalise company codes.
    --
    -- '__ALL__' means no company restriction.
    ------------------------------------------------------------------
    IF p_company_codes IS NOT NULL
       AND p_company_codes.COUNT > 0
    THEN
        IF p_company_codes.COUNT = 1
           AND p_company_codes(1) = '__ALL__'
        THEN
            l_has_companies := 0;
        ELSE
            l_company_codes := SYS.ODCIVARCHAR2LIST();

            l_company_codes.EXTEND(
                p_company_codes.COUNT
            );

            FOR i IN 1 .. p_company_codes.COUNT LOOP
                l_company_codes(i) :=
                    p_company_codes(i);
            END LOOP;

            l_has_companies := 1;
        END IF;
    END IF;

    ------------------------------------------------------------------
    -- Exact-match pre-check.
    --
    -- Preserve existing verified behaviour:
    -- if at least one ELIGIBLE exact policy/rider candidate exists,
    -- continue using exact matching only.
    --
    -- Eligibility here intentionally mirrors the filters used later:
    --   1. Admin company and company scope
    --   2. Exact policy, record and rider
    --   3. Inforce-only/name/first-initial/DOB rule
    --   4. Non-negative premium
    --
    -- DOD range is not part of eligibility. DOD remains part of ranking.
    ------------------------------------------------------------------
    SELECT COUNT(*)
    INTO v_exact_candidate_cnt
    FROM (
        SELECT 1
        FROM tri_edi_trans a
        WHERE a.admn_cmpny_cd =
                  p_admin_company_code

          AND (
                l_has_companies = 0
                OR a.rnsrnc_cmpny_cd IN (
                    SELECT column_value
                    FROM TABLE(l_company_codes)
                )
              )

          --------------------------------------------------------------
          -- Existing verified exact-policy behaviour.
          --------------------------------------------------------------
          AND a.plcy_nmbr =
                  l_policy_number

          --------------------------------------------------------------
          -- Record number remains exact.
          --------------------------------------------------------------
          AND (
                l_record_number IS NULL
                OR a.rec_nmbr = l_record_number
              )

          --------------------------------------------------------------
          -- Rider remains exact during the exact-match pre-check.
          --------------------------------------------------------------
          AND (
                l_rider_number IS NULL
                OR a.rdr_nmbr = l_rider_number
              )

          --------------------------------------------------------------
          -- Preserve existing inforce-only/name/initial/DOB behaviour.
          --------------------------------------------------------------
          AND (
                l_use_inforce_only = 'Y'

                OR (
                       UPPER(TRIM(a.lst_nm)) =
                           l_last_name_upper

                   AND UPPER(
                           SUBSTR(
                               TRIM(a.frst_nm),
                               1,
                               1
                           )
                       ) =
                           l_first_initial_upper

                   AND (
                        p_dob IS NULL
                        OR a.dob = p_dob
                       )
                   )
              )

          --------------------------------------------------------------
          -- Business definition:
          -- zero is an eligible premium.
          -- NULL is treated as zero, preserving current behaviour.
          --------------------------------------------------------------
          AND NVL(a.rv_prm_amt_1, 0) >= 0

          AND ROWNUM = 1
    );

    IF v_exact_candidate_cnt = 0 THEN
        v_use_tolerant_match := 1;
    ELSE
        v_use_tolerant_match := 0;
    END IF;

    ------------------------------------------------------------------
    -- BEST-MATCH SELECTION
    --
    -- Eligibility:
    --   1. Renewal is TRNS_TYP = 'W'.
    --   2. Premium must be zero or greater than zero.
    --   3. Negative-premium rows cannot create a worksheet.
    --
    -- Coverage date rules:
    --   1. FROM_DT and TO_DT populated:
    --        FROM_DT <= DOD <= TO_DT
    --
    --   2. FROM_DT populated and TO_DT null:
    --        DOD >= FROM_DT
    --
    --   3. FROM_DT null and TO_DT populated:
    --        EFFCTV_DT <= DOD <= TO_DT
    --
    --   4. FROM_DT and TO_DT both null:
    --        Not a DOD-range match.
    --
    -- Identity selection:
    --   1. Exact policy/rider matching is used whenever an eligible
    --      exact candidate exists.
    --
    --   2. Only when no eligible exact candidate exists, shared tolerant
    --      policy/rider matching is enabled.
    --
    -- Selection order:
    --   1. Strongest policy identity match.
    --   2. Strongest rider/coverage identity match.
    --   3. DOD-in-range transactions.
    --      Newest reporting period wins among these.
    --
    --   4. If no DOD-in-range transaction exists:
    --      newest renewal transaction.
    --
    --   5. If neither exists:
    --      newest other eligible transaction.
    --
    -- Tie-breakers after reporting period:
    --   1. Renewal preference
    --   2. Closest NAR
    --   3. Highest EDI_TRANS_ID
    ------------------------------------------------------------------
    BEGIN
        WITH all_txn AS (
            SELECT
                a.edi_trans_id,
                a.trns_typ,
                a.effctv_dt AS reporting_period,
                a.from_dt,
                a.to_dt,
                a.rec_nmbr,
                a.rdr_nmbr,
                a.rv_reins_nar_1 AS nar,
                a.rv_prm_amt_1 AS premium,
                a.rv_allw_amt_1 AS allowance,
                a.src_cd,
                a.status_cd,
                a.jnt_insrd,
                a.rnsrnc_cmpny_cd AS company_code,
                a.lst_nm,
                a.frst_nm,
                a.dob,
                a.sex,
                a.plcy_dt AS issue_date,
                a.orig_plcy_dt AS original_issue_date,
                a.plcy_nmbr,

                ------------------------------------------------------
                -- Shared policy match quality.
                --
                -- Return values:
                --   0 = raw exact
                --   1 = leading-zero normalised exact
                --   2 = embedded policy match
                --   3 = significant trailing overlap
                --   9 = no match
                ------------------------------------------------------
                PKG_CLAIMSWORKSHEET_AUTOMATION
                    .get_policy_number_match_rank(
                        l_policy_number,
                        a.plcy_nmbr
                    ) AS policy_match_rank,

                ------------------------------------------------------
                -- Shared rider/coverage match quality.
                --
                -- Return values:
                --   0 = raw exact or no rider supplied
                --   1 = leading-zero normalised exact
                --   2 = embedded rider match
                --   9 = no match
                ------------------------------------------------------
                PKG_CLAIMSWORKSHEET_AUTOMATION
                    .get_coverage_number_match_rank(
                        l_rider_number,
                        a.rdr_nmbr
                    ) AS coverage_match_rank,

                CASE
                    WHEN a.trns_typ = 'W' THEN 1
                    ELSE 0
                END AS is_renewal,

                ------------------------------------------------------
                -- Effective coverage start:
                --
                -- If FROM_DT exists, use FROM_DT.
                -- If FROM_DT is null but TO_DT exists, use EFFCTV_DT.
                ------------------------------------------------------
                CASE
                    WHEN a.from_dt IS NOT NULL
                        THEN a.from_dt

                    WHEN a.from_dt IS NULL
                     AND a.to_dt IS NOT NULL
                        THEN a.effctv_dt

                    ELSE NULL
                END AS coverage_start_dt,

                a.to_dt AS coverage_end_dt

            FROM tri_edi_trans a

            WHERE a.admn_cmpny_cd =
                      p_admin_company_code

              AND (
                    l_has_companies = 0
                    OR a.rnsrnc_cmpny_cd IN (
                        SELECT column_value
                        FROM TABLE(l_company_codes)
                    )
                  )

              AND (
                    --------------------------------------------------
                    -- Existing verified behaviour:
                    -- exact policy match remains in effect whenever
                    -- an eligible exact candidate exists.
                    --------------------------------------------------
                    (
                        v_use_tolerant_match = 0
                        AND a.plcy_nmbr = l_policy_number
                    )

                    OR

                    --------------------------------------------------
                    -- Tolerant policy matching.
                    --
                    -- This path is enabled only when the exact
                    -- pre-check found no eligible exact candidate.
                    --------------------------------------------------
                    (
                        v_use_tolerant_match = 1

                        AND PKG_CLAIMSWORKSHEET_AUTOMATION
                                .get_policy_number_match_rank(
                                    l_policy_number,
                                    a.plcy_nmbr
                                ) IN (0, 1, 2, 3)
                    )
                  )
        ),

        filtered_txn AS (
            SELECT
                t.edi_trans_id,
                t.trns_typ,
                t.reporting_period,
                t.from_dt,
                t.to_dt,
                t.rec_nmbr,
                t.rdr_nmbr,
                t.nar,
                t.premium,
                t.allowance,
                t.src_cd,
                t.status_cd,
                t.jnt_insrd,
                t.company_code,
                t.lst_nm,
                t.frst_nm,
                t.dob,
                t.sex,
                t.issue_date,
                t.original_issue_date,
                t.plcy_nmbr,
                t.policy_match_rank,
                t.coverage_match_rank,
                t.is_renewal,
                t.coverage_start_dt,
                t.coverage_end_dt

            FROM all_txn t

            WHERE (
                    --------------------------------------------------
                    -- Record number remains exact.
                    --------------------------------------------------
                    l_record_number IS NULL
                    OR t.rec_nmbr = l_record_number
                  )

              AND (
                    --------------------------------------------------
                    -- No rider supplied:
                    -- do not restrict by coverage/rider.
                    --------------------------------------------------
                    l_rider_number IS NULL

                    OR

                    --------------------------------------------------
                    -- Existing verified exact-rider behaviour.
                    --------------------------------------------------
                    (
                        v_use_tolerant_match = 0
                        AND t.rdr_nmbr = l_rider_number
                    )

                    OR

                    --------------------------------------------------
                    -- Tolerant rider matching.
                    --
                    -- This path is enabled only when no eligible exact
                    -- policy/rider candidate exists.
                    --------------------------------------------------
                    (
                        v_use_tolerant_match = 1

                        AND t.coverage_match_rank IN (0, 1, 2)
                    )
                  )

              AND (
                    l_use_inforce_only = 'Y'

                    OR (
                           UPPER(TRIM(t.lst_nm)) =
                               l_last_name_upper

                       AND UPPER(
                               SUBSTR(
                                   TRIM(t.frst_nm),
                                   1,
                                   1
                               )
                           ) =
                               l_first_initial_upper

                       AND (
                            p_dob IS NULL
                            OR t.dob = p_dob
                           )
                       )
                  )
        ),

        eligible_txn AS (
            SELECT
                t.edi_trans_id,
                t.trns_typ,
                t.reporting_period,
                t.from_dt,
                t.to_dt,
                t.rec_nmbr,
                t.rdr_nmbr,
                t.nar,
                t.premium,
                t.allowance,
                t.src_cd,
                t.status_cd,
                t.jnt_insrd,
                t.company_code,
                t.lst_nm,
                t.frst_nm,
                t.dob,
                t.sex,
                t.issue_date,
                t.original_issue_date,
                t.plcy_nmbr,
                t.policy_match_rank,
                t.coverage_match_rank,
                t.is_renewal,
                t.coverage_start_dt,
                t.coverage_end_dt,

                ------------------------------------------------------
                -- NAR distance is only a tie-breaker.
                ------------------------------------------------------
                ABS(
                    NVL(t.nar, 0)
                    - NVL(p_claim_notice_nar, 0)
                ) AS nar_delta,

                ------------------------------------------------------
                -- Determine whether DOD is within coverage.
                ------------------------------------------------------
                CASE
                    WHEN p_date_of_death IS NULL THEN 0

                    WHEN t.coverage_start_dt IS NULL THEN 0

                    WHEN t.coverage_end_dt IS NOT NULL
                     AND p_date_of_death >=
                             t.coverage_start_dt
                     AND p_date_of_death <=
                             t.coverage_end_dt
                    THEN 1

                    WHEN t.coverage_end_dt IS NULL
                     AND p_date_of_death >=
                             t.coverage_start_dt
                    THEN 1

                    ELSE 0
                END AS is_dod_in_range

            FROM filtered_txn t

            ----------------------------------------------------------
            -- Business definition:
            -- zero is an eligible premium.
            -- NULL is currently treated as zero, preserving the
            -- previously agreed NVL behaviour.
            ----------------------------------------------------------
            WHERE NVL(t.premium, 0) >= 0
        ),

        ranked_txn AS (
            SELECT
                e.edi_trans_id,
                e.trns_typ,
                e.reporting_period,
                e.from_dt,
                e.to_dt,
                e.rec_nmbr,
                e.rdr_nmbr,
                e.nar,
                e.premium,
                e.allowance,
                e.src_cd,
                e.status_cd,
                e.jnt_insrd,
                e.company_code,
                e.lst_nm,
                e.frst_nm,
                e.dob,
                e.sex,
                e.issue_date,
                e.original_issue_date,
                e.plcy_nmbr,
                e.policy_match_rank,
                e.coverage_match_rank,
                e.is_renewal,
                e.coverage_start_dt,
                e.coverage_end_dt,
                e.nar_delta,
                e.is_dod_in_range,

                ------------------------------------------------------
                -- Classification returned to caller.
                ------------------------------------------------------
                CASE
                    WHEN e.is_dod_in_range = 1
                        THEN 'PERFECT'

                    WHEN e.is_renewal = 1
                        THEN 'BEST'

                    ELSE 'NEXT'
                END AS match_type,

                ------------------------------------------------------
                -- Search order:
                --
                -- Identity quality is evaluated first so a weaker
                -- partial policy does not outrank a stronger policy
                -- identity merely because the weaker row is newer.
                --
                -- In exact mode, policy_match_rank and
                -- coverage_match_rank are both zero, so the existing
                -- verified transaction-selection order is unchanged.
                --
                -- After identity selection:
                --   1. Newest DOD-qualified period
                --   2. Newest renewal
                --   3. Newest general eligible transaction
                ------------------------------------------------------
                ROW_NUMBER() OVER (
                    ORDER BY
                        --------------------------------------------------
                        -- Strongest policy identity first.
                        --------------------------------------------------
                        e.policy_match_rank ASC,

                        --------------------------------------------------
                        -- Strongest rider/coverage identity second.
                        --------------------------------------------------
                        e.coverage_match_rank ASC,

                        --------------------------------------------------
                        -- Existing verified business priority.
                        --------------------------------------------------
                        CASE
                            WHEN e.is_dod_in_range = 1
                                THEN 1

                            WHEN e.is_renewal = 1
                                THEN 2

                            ELSE 3
                        END ASC,

                        e.reporting_period DESC NULLS LAST,

                        e.is_renewal DESC,

                        e.nar_delta ASC,

                        e.edi_trans_id DESC
                ) AS rn

            FROM eligible_txn e
        )

        SELECT
            r.edi_trans_id,
            r.match_type,
            r.plcy_nmbr
        INTO
            v_best_edi_trans_id,
            v_best_match_type,
            v_best_policy_number
        FROM ranked_txn r
        WHERE r.rn = 1;

        o_match_status := v_best_match_type;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_best_edi_trans_id  := NULL;
            v_best_match_type    := NULL;
            v_best_policy_number := NULL;
            o_match_status       := 'NO_MATCH';
    END;

    ------------------------------------------------------------------
    -- Return all associated EDI records for the selected policy/company
    -- scope.
    --
    -- IMPORTANT:
    -- Preserve the existing output columns, aliases, order and
    -- net-premium calculation.
    --
    -- If a best transaction was selected, return all transactions for
    -- that transaction's database policy number.
    --
    -- This prevents rows for multiple partially matching policy numbers
    -- from being mixed into one returned result set.
    ------------------------------------------------------------------
    OPEN o_result FOR
        SELECT
            a.edi_trans_id,
            a.trns_typ,
            b.trns_typ_desc,
            a.effctv_dt AS reporting_period,
            a.from_dt,
            a.to_dt,
            a.rdr_nmbr AS coverage_number,
            a.rec_nmbr AS record_number,
            a.rv_reins_nar_1 AS nar,
            a.rv_prm_amt_1 AS premium,
            a.rv_allw_amt_1 AS allowance,

            ----------------------------------------------------------
            -- Existing output contract preserved exactly.
            ----------------------------------------------------------
            (
                NVL(a.rv_prm_amt_1, 0)
                - NVL(a.rv_allw_amt_1, 0)
            ) AS net_premium,

            a.src_cd,
            a.status_cd,
            a.jnt_insrd,
            a.rnsrnc_cmpny_cd AS company_code,
            a.lst_nm AS last_name,
            a.frst_nm AS first_name,
            a.dob AS dob,
            a.sex AS gender,
            a.plcy_dt AS issue_date,
            a.orig_plcy_dt AS original_issue_date,

            CASE
                WHEN a.edi_trans_id =
                         v_best_edi_trans_id
                THEN 'Y'
                ELSE 'N'
            END AS recommended_sw,

            CASE
                WHEN a.edi_trans_id =
                         v_best_edi_trans_id
                THEN v_best_match_type
                ELSE NULL
            END AS match_type

        FROM tri_edi_trans a

        LEFT JOIN tri_edi_trns_typ b
               ON b.trns_typ = a.trns_typ

        WHERE a.admn_cmpny_cd =
                  p_admin_company_code

          AND (
                l_has_companies = 0
                OR a.rnsrnc_cmpny_cd IN (
                    SELECT column_value
                    FROM TABLE(l_company_codes)
                )
              )

          AND (
                ----------------------------------------------------------
                -- If a best match was selected, return all associated EDI
                -- transactions for the selected DB policy number.
                ----------------------------------------------------------
                (
                    v_best_policy_number IS NOT NULL
                    AND a.plcy_nmbr =
                            v_best_policy_number
                )

                OR

                ----------------------------------------------------------
                -- If no best match was selected in exact mode, preserve
                -- the original exact-policy return behaviour.
                ----------------------------------------------------------
                (
                    v_best_policy_number IS NULL
                    AND v_use_tolerant_match = 0
                    AND a.plcy_nmbr =
                            l_policy_number
                )

                OR

                ----------------------------------------------------------
                -- If no best match was selected in tolerant mode, return
                -- tolerant policy candidates for diagnostic visibility.
                ----------------------------------------------------------
                (
                    v_best_policy_number IS NULL
                    AND v_use_tolerant_match = 1

                    AND PKG_CLAIMSWORKSHEET_AUTOMATION
                            .get_policy_number_match_rank(
                                l_policy_number,
                                a.plcy_nmbr
                            ) IN (0, 1, 2, 3)
                )
              )

        ORDER BY
            a.effctv_dt DESC,
            a.from_dt ASC,
            a.edi_trans_id DESC;

EXCEPTION
    WHEN OTHERS THEN
        o_match_status := 'NO_MATCH';

        ------------------------------------------------------------------
        -- Return an empty cursor with the same columns and data types as
        -- the normal cursor.
        --
        -- This preserves the cursor contract for the caller.
        ------------------------------------------------------------------
        OPEN o_result FOR
            SELECT
                a.edi_trans_id,
                a.trns_typ,
                b.trns_typ_desc,
                a.effctv_dt AS reporting_period,
                a.from_dt,
                a.to_dt,
                a.rdr_nmbr AS coverage_number,
                a.rec_nmbr AS record_number,
                a.rv_reins_nar_1 AS nar,
                a.rv_prm_amt_1 AS premium,
                a.rv_allw_amt_1 AS allowance,

                (
                    NVL(a.rv_prm_amt_1, 0)
                    - NVL(a.rv_allw_amt_1, 0)
                ) AS net_premium,

                a.src_cd,
                a.status_cd,
                a.jnt_insrd,
                a.rnsrnc_cmpny_cd AS company_code,
                a.lst_nm AS last_name,
                a.frst_nm AS first_name,
                a.dob AS dob,
                a.sex AS gender,
                a.plcy_dt AS issue_date,
                a.orig_plcy_dt AS original_issue_date,
                CAST(NULL AS VARCHAR2(1)) AS recommended_sw,
                CAST(NULL AS VARCHAR2(30)) AS match_type

            FROM tri_edi_trans a

            LEFT JOIN tri_edi_trns_typ b
                   ON b.trns_typ = a.trns_typ

            WHERE 1 = 0;

END edi_trans_search;

END PKG_CLAIMSWORKSHEET_AUTOMATION;
/

