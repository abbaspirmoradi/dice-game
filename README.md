Public Class ClaimWorkflowHistory

#Region "Private Variables"

    Private mHISTORYID As Decimal
    Private mSUBMISSIONID As String
    Private mRECORDCASEID As String

    Private mSTEPNAME As String
    Private mWORKFLOWSTATE As String

    Private mSTATUS As String
    Private mERRORMESSAGE As String

    Private mWORKERID As String
    Private mSTARTTS As Date
    Private mENDTS As Date

    Private mRESULTCODE As String
    Private mRESULTDETAILS As String
    Private mCLAIMIDS As String
    Private mWORKSHEETIDS As String
    Private mINFRCALPHAIDS As String
    Private mEDITRANSIDS As String
    Private mCREATEDWORKSHEETID As String

    Private mADDUSERID As String
    Private mADDTMSTP As Date
    Private mMDFYUSERID As String
    Private mMDFYTMSTP As Date

#End Region

#Region "Properties"

    Friend Property HISTORYID() As Decimal
        Get
            Return mHISTORYID
        End Get
        Set(ByVal Value As Decimal)
            mHISTORYID = Value
        End Set
    End Property

    Friend Property SUBMISSIONID() As String
        Get
            Return mSUBMISSIONID
        End Get
        Set(ByVal Value As String)
            mSUBMISSIONID = Value
        End Set
    End Property

    Friend Property RECORDCASEID() As String
        Get
            Return mRECORDCASEID
        End Get
        Set(ByVal Value As String)
            mRECORDCASEID = Value
        End Set
    End Property

    Friend Property STEPNAME() As String
        Get
            Return mSTEPNAME
        End Get
        Set(ByVal Value As String)
            mSTEPNAME = Value
        End Set
    End Property

    Friend Property WORKFLOWSTATE() As String
        Get
            Return mWORKFLOWSTATE
        End Get
        Set(ByVal Value As String)
            mWORKFLOWSTATE = Value
        End Set
    End Property

    Friend Property STATUS() As String
        Get
            Return mSTATUS
        End Get
        Set(ByVal Value As String)
            mSTATUS = Value
        End Set
    End Property

    Friend Property ERRORMESSAGE() As String
        Get
            Return mERRORMESSAGE
        End Get
        Set(ByVal Value As String)
            mERRORMESSAGE = Value
        End Set
    End Property

    Friend Property WORKERID() As String
        Get
            Return mWORKERID
        End Get
        Set(ByVal Value As String)
            mWORKERID = Value
        End Set
    End Property

    Friend Property STARTTS() As Date
        Get
            Return mSTARTTS
        End Get
        Set(ByVal Value As Date)
            mSTARTTS = Value
        End Set
    End Property

    Friend Property ENDTS() As Date
        Get
            Return mENDTS
        End Get
        Set(ByVal Value As Date)
            mENDTS = Value
        End Set
    End Property

    Friend Property RESULTCODE() As String
        Get
            Return mRESULTCODE
        End Get
        Set(ByVal Value As String)
            mRESULTCODE = Value
        End Set
    End Property

    Friend Property RESULTDETAILS() As String
        Get
            Return mRESULTDETAILS
        End Get
        Set(ByVal Value As String)
            mRESULTDETAILS = Value
        End Set
    End Property

    Friend Property CLAIMIDS() As String
        Get
            Return mCLAIMIDS
        End Get
        Set(ByVal Value As String)
            mCLAIMIDS = Value
        End Set
    End Property

    Friend Property WORKSHEETIDS() As String
        Get
            Return mWORKSHEETIDS
        End Get
        Set(ByVal Value As String)
            mWORKSHEETIDS = Value
        End Set
    End Property

    Friend Property INFRCALPHAIDS() As String
        Get
            Return mINFRCALPHAIDS
        End Get
        Set(ByVal Value As String)
            mINFRCALPHAIDS = Value
        End Set
    End Property

    Friend Property EDITRANSIDS() As String
        Get
            Return mEDITRANSIDS
        End Get
        Set(ByVal Value As String)
            mEDITRANSIDS = Value
        End Set
    End Property

    Friend Property CREATEDWORKSHEETID() As String
        Get
            Return mCREATEDWORKSHEETID
        End Get
        Set(ByVal Value As String)
            mCREATEDWORKSHEETID = Value
        End Set
    End Property

    Friend Property ADDUSERID() As String
        Get
            Return mADDUSERID
        End Get
        Set(ByVal Value As String)
            mADDUSERID = Value
        End Set
    End Property

    Friend Property ADDTMSTP() As Date
        Get
            Return mADDTMSTP
        End Get
        Set(ByVal Value As Date)
            mADDTMSTP = Value
        End Set
    End Property

    Friend Property MDFYUSERID() As String
        Get
            Return mMDFYUSERID
        End Get
        Set(ByVal Value As String)
            mMDFYUSERID = Value
        End Set
    End Property

    Friend Property MDFYTMSTP() As Date
        Get
            Return mMDFYTMSTP
        End Get
        Set(ByVal Value As Date)
            mMDFYTMSTP = Value
        End Set
    End Property

#End Region

#Region "Mapping"

    Friend Sub SetFromDataRow(ByVal row As DataRow)

        If row Is Nothing Then Exit Sub

        If row("HISTORY_ID") IsNot DBNull.Value Then mHISTORYID = row("HISTORY_ID")

        If row("SUBMISSION_ID") IsNot DBNull.Value Then mSUBMISSIONID = row("SUBMISSION_ID").ToString()
        If row("RECORD_CASE_ID") IsNot DBNull.Value Then mRECORDCASEID = row("RECORD_CASE_ID").ToString()

        If row("STEP_NAME") IsNot DBNull.Value Then mSTEPNAME = row("STEP_NAME").ToString()
        If row("WORKFLOW_STATE") IsNot DBNull.Value Then mWORKFLOWSTATE = row("WORKFLOW_STATE").ToString()

        If row("STATUS") IsNot DBNull.Value Then mSTATUS = row("STATUS").ToString()
        If row("ERROR_MESSAGE") IsNot DBNull.Value Then mERRORMESSAGE = row("ERROR_MESSAGE").ToString()

        If row("WORKER_ID") IsNot DBNull.Value Then mWORKERID = row("WORKER_ID").ToString()

        If row("START_TS") IsNot DBNull.Value Then mSTARTTS = row("START_TS")
        If row("END_TS") IsNot DBNull.Value Then mENDTS = row("END_TS")

        If row("RESULT_CODE") IsNot DBNull.Value Then mRESULTCODE = row("RESULT_CODE").ToString()
        If row("RESULT_DETAILS") IsNot DBNull.Value Then mRESULTDETAILS = row("RESULT_DETAILS").ToString()

        If row("CLAIM_IDS") IsNot DBNull.Value Then mCLAIMIDS = row("CLAIM_IDS").ToString()
        If row("WORKSHEET_IDS") IsNot DBNull.Value Then mWORKSHEETIDS = row("WORKSHEET_IDS").ToString()

        If row("INFRC_ALPHA_IDS") IsNot DBNull.Value Then mINFRCALPHAIDS = row("INFRC_ALPHA_IDS").ToString()
        If row("EDI_TRANS_IDS") IsNot DBNull.Value Then mEDITRANSIDS = row("EDI_TRANS_IDS").ToString()

        If row("CREATED_WORKSHEET_ID") IsNot DBNull.Value Then
            mCREATEDWORKSHEETID = row("CREATED_WORKSHEET_ID").ToString()
        End If

        If row("ADD_USER_ID") IsNot DBNull.Value Then mADDUSERID = row("ADD_USER_ID").ToString()
        If row("ADD_TMSTP") IsNot DBNull.Value Then mADDTMSTP = row("ADD_TMSTP")

        If row("MDFY_USER_ID") IsNot DBNull.Value Then mMDFYUSERID = row("MDFY_USER_ID").ToString()
        If row("MDFY_TMSTP") IsNot DBNull.Value Then mMDFYTMSTP = row("MDFY_TMSTP")

    End Sub

#End Region

End Class

Imports Oracle.DataAccess.Client
Imports System.Data

Public Class ClaimWorkflowHistoryDLC

    Private mConn As OracleConnection

    Friend Function GetWorkflowHistory(ByVal pFromTime As Date,
                                   ByVal pToTime As Date,
                                   Optional ByVal pWorkerID As String = "",
                                   Optional ByVal pStatus As String = "") As DataTable

        Dim oAdapter As New OracleDataAdapter()
        Dim oTable As New DataTable
        Dim oCmd As OracleCommand
        Dim strCmd As String

        Dim oWorker As Object
        Dim oStatus As Object

        Try

            If pToTime < pFromTime Then
                Throw New ArgumentException("Invalid time range")
            End If

            oWorker = If(String.IsNullOrWhiteSpace(pWorkerID), DBNull.Value, pWorkerID.Trim())
            oStatus = If(String.IsNullOrWhiteSpace(pStatus), DBNull.Value, pStatus.Trim())

            ' ✅ IMPORTANT: use DB time awareness
            strCmd =
        "SELECT * FROM tri_claim_workflow_history " &
        "WHERE start_ts IS NOT NULL " &
        "AND start_ts >= :fromTime " &
        "AND start_ts <= :toTime " &
        "AND (:workerId IS NULL OR worker_id = :workerId) " &
        "AND (:status IS NULL OR status = :status) " &
        "ORDER BY start_ts DESC"

            mConn = DatabaseCon.GetConnection()

            oCmd = New OracleCommand
            oCmd.BindByName = True
            oCmd.CommandText = strCmd
            oCmd.Connection = mConn

            oCmd.Parameters.Add("fromTime", OracleDbType.TimeStamp).Value = pFromTime
            oCmd.Parameters.Add("toTime", OracleDbType.TimeStamp).Value = pToTime
            oCmd.Parameters.Add("workerId", OracleDbType.Varchar2).Value = oWorker
            oCmd.Parameters.Add("status", OracleDbType.Varchar2).Value = oStatus

            oAdapter.SelectCommand = oCmd
            oAdapter.Fill(oTable)

            Return oTable

        Catch ex As Exception
            Throw New Exception("DLC Error", ex)
        End Try

    End Function

End Class


"   ","HISTORY_ID","SUBMISSION_ID","RECORD_CASE_ID","STEP_NAME","WORKFLOW_STATE","STATUS","ERROR_MESSAGE","WORKER_ID","START_TS","END_TS","RESULT_CODE","RESULT_DETAILS","CLAIM_IDS","WORKSHEET_IDS","INFRC_ALPHA_IDS","EDI_TRANS_IDS","CREATED_WORKSHEET_ID","ADD_USER_ID","ADD_TMSTP","MDFY_USER_ID","MDFY_TMSTP"
"1","105","CO0560508202608:41:19","1","New","New","COMPLETED","","LE6133","13-JUL-26 04.03.11.233633 PM","13-JUL-26 04.03.11.327966 PM","","","","","","","0","REINS_SVC","7/13/2026 4:03:11 PM","REINS_SVC","7/13/2026 4:03:11 PM"
"2","106","CO0560508202608:41:19","1","SearchExistingClaim","SearchExistingClaim","COMPLETED","","LE6133","13-JUL-26 04.03.12.775462 PM","13-JUL-26 04.03.13.545431 PM","NOT_FOUND","No existing claims matched search criteria.","","","","","0","REINS_SVC","7/13/2026 4:03:12 PM","REINS_SVC","7/13/2026 4:03:13 PM"
"3","83","CO0560508202608:41:19","1","SearchExistingWorksheet","SearchExistingWorksheet","COMPLETED","","LE6133","13-JUL-26 04.03.15.072043 PM","13-JUL-26 04.03.15.415129 PM","NOT_FOUND","No open claim worksheet found.","","","","","0","REINS_SVC","7/13/2026 4:03:15 PM","REINS_SVC","7/13/2026 4:03:15 PM"
"4","84","CO0560508202608:41:19","1","SearchInforce","SearchInforce","COMPLETED","","LE6133","13-JUL-26 04.03.16.888856 PM","13-JUL-26 04.03.53.776931 PM","FOUND","Existing Inforce records found. Count=2","","","55595573,55595271","","0","REINS_SVC","7/13/2026 4:03:16 PM","REINS_SVC","7/13/2026 4:03:53 PM"
"5","85","CO0560508202608:41:19","1","SearchEdi","SearchEdi","COMPLETED","","LE6133","13-JUL-26 04.03.55.247325 PM","13-JUL-26 04.04.41.056394 PM","FOUND","Existing EDI records found. Count=792","","","55595271,55595573","4172570056,4149573014,4124122883,4105301356,4083444905,4058047461,4033784558,4010685619,3987095274,3965092954,3936430911,3913597824,3887916188,3868517886,3841205086,3817046542,3801065948,3772504250,3745115407,3723490093,3698357522,3674591602,3653041321,3628174118,3604432014,3580580741,3557741620,3534050662,3511517403,3488753312,3464810470,3441719268,3428923660,3394024346,3370402257,3345396641,3322966471,3298104289,3276303112,3255036695,3231551333,3206796589,3185042621,3163895265,3137768213,3112496285,3087891354,3064159669,3044265678,3023105058,2997172693,2975737051,2950455179,2930717804,2906232682,2884070010,2860634816,2838869692,2814695891,2791705102,2767325465,2745538058,2724921982,2701796794,2689344671,2659285190,2637281165,2612401192,2593315791,2569003373,2545346019,2523495357,2498520864,2477333723,2455951455,2433189282,2412125870,2410799819,2398461223,2341508604,2321398101,2294480944,2275452087,2254177995,2227973043,2207985999,2188001213,2167447675,2140962537,2124366653,2095724542,2075706615,2054616412,2034044685,2010594696,1987320056,1963613232,1953774163,1923307586,1901110635,1877753634,1854193217,1833056203,1810676349,1787051600,1767141367,1743974701,1720343204,1701374303,1677056211,1656717694,1638065705,1611639890,1589150342,1568126681,1546192705,1524239652,1502829701,1479768856,1457755709,1436397633,1413415274,1388994037,1369228769,1346844135,1322910823,1304195672,1282406898,1267048664,1240430844,1240430843,1240430845,1240430842,1240430846,1240430841,1240430847,1240430840,1240430848,1240430839,1240430849,1240430838,1240430850,1240430837,1240430851,1240430836,1240430852,1240430835,1240430853,1240430834,1240430854,1240430833,1240430855,1240430832,1240430856,1240430831,1240430857,1240430830,1240430858,1240430829,1240430859,1240430828,1240430860,1240430827,1240430861,1240430826,1240430862,1240430825,1240430863,1240430824,1240430864,1240430823,1240430865,1240430822,1240430866,1240430821,1240430867,1240430820,1240430868,1240430819,1240430869,1240430818,1240430870,1240430817,1240430871,1240430816,1240430872,1240430815,1240430873,1240430814,1240430874,1240430813,1240430875,1240430812,1240430876,1240430811,1240430877,1240430810,1240430878,1240430809,1240430879,1240430808,1240430880,1240430807,1240430881,1240430806,1240430882,1240430805,1240430883,1240430804,1240430884,1240430803,1240430885,1240430802,1240430886,1240430801,1240430887,1240430800,1240430888,1240430799,1240430889,1240430798,1240430890,1240430797,1240430891,1240430796,1240430892,1240430795,1240430893,1240430794,1240430894,1240430793,1240430895,1240430792,1240430896,1240430791,1240430897,1240430790,1240430898,1240430789,1240430899,1240430788,1240430900,1240430786,1240430901,1240430784,1240430902,1240430782,1240430903,1240430779,1240430904,1240430778,1240430905,1240430776,1240430906,1240430773,1240430907,1240430772,1240430908,1240430769,1240430909,1240430767,1240430910,1240430765,1240430911,1240430763,1240430912,1240430762,1240430913,1240430759,1240430914,1240430757,1240430915,1240430756,1240430916,1240430754,1240430917,1240430752,1240430918,1240430750,1240430919,1240430748,1240430920,1240430746,1240430921,1240430744,1240430922,1240430742,1240430923,1240430740,1240430924,1240430738,1240430925,1240430736,1240430926,1240430734,1240430927,1240430732,1240430928,1240430730,1240430929,1240430728,1240430930,1240430726,1240430931,1240430724,1240430932,1240430722,1240430933,1240430720,1240430934,1240430718,1240430935,1240430716,1240430936,1240430714,1240430937,1240430712,1240430938,1240430710,1240430939,1240430708,1240430940,1240430706,1240430941,1240430704,1240430942,1240430702,1240430943,1240430700,1240430944,1240430698,1240430945,1240430696,1240430946,1240430694,1240430947,1240430692,1240430948,1240430690,1240430949,1240430688,1240430950,1240430686,1240430951,1240430684,1240430952,1240430682,1240430953,1240430680,1240430954,1240430678,1240430955,1240430676,1240430956,1240430674,1240430957,1240430672,1240430958,1240430670,1240430959,1240430668,1240430960,1240430666,1240430","0","REINS_SVC","7/13/2026 4:03:55 PM","REINS_SVC","7/13/2026 4:04:41 PM"
"6","86","CO0560508202608:41:19","1","CreateWorksheet","CreateWorksheet","STARTED","","LE6133","13-JUL-26 04.04.42.545053 PM","","","","","","","","","REINS_SVC","7/13/2026 4:04:42 PM","",""
"7","81","CO1646_Jul022026_10:52:24","1","New","New","COMPLETED","","LE6133","13-JUL-26 04.02.33.977168 PM","13-JUL-26 04.02.34.215643 PM","","","","","","","0","REINS_SVC","7/13/2026 4:02:33 PM","REINS_SVC","7/13/2026 4:02:34 PM"
"8","82","CO1646_Jul022026_10:52:24","1","SearchExistingClaim","SearchExistingClaim","COMPLETED","","LE6133","13-JUL-26 04.02.35.929097 PM","13-JUL-26 04.02.37.096384 PM","NOT_FOUND","No existing claims matched search criteria.","","","","","0","REINS_SVC","7/13/2026 4:02:35 PM","REINS_SVC","7/13/2026 4:02:37 PM"
"9","101","CO1646_Jul022026_10:52:24","1","SearchExistingWorksheet","SearchExistingWorksheet","COMPLETED","","LE6133","13-JUL-26 04.02.38.575357 PM","13-JUL-26 04.02.39.003925 PM","FOUND","Open worksheet found. Count=1; WorksheetId=223775","","223775","","","0","REINS_SVC","7/13/2026 4:02:38 PM","REINS_SVC","7/13/2026 4:02:39 PM"
"10","102","CO1646_Jul022026_10:52:24","1","AttachDocuments","AttachDocuments","COMPLETED","","LE6133","13-JUL-26 04.02.40.449106 PM","13-JUL-26 04.02.46.418237 PM","ATTACHED","3 file(s) attached to 1 ClaimWorksheets.","","223775","","","0","REINS_SVC","7/13/2026 4:02:40 PM","REINS_SVC","7/13/2026 4:02:46 PM"
"11","103","CO1646_Jul022026_10:52:24","1","SystemGeneratedEmail","SystemGeneratedEmail","COMPLETED","","LE6133","13-JUL-26 04.02.47.878774 PM","13-JUL-26 04.02.52.618703 PM","","","","223775","","","0","REINS_SVC","7/13/2026 4:02:47 PM","REINS_SVC","7/13/2026 4:02:52 PM"
"12","104","CO1646_Jul022026_10:52:24","1","WORKFLOW_COMPLETED","Final","COMPLETED","","LE6133","13-JUL-26 04.02.52.717368 PM","13-JUL-26 04.02.52.818071 PM","SUCCESS","Workflow completed successfully","","223775","","","0","REINS_SVC","7/13/2026 4:02:52 PM","REINS_SVC","7/13/2026 4:02:52 PM"
"13","62","CO646_Jul022026_10:52:24","1","New","New","COMPLETED","","LE6133","10-JUL-26 03.28.17.466781 PM","10-JUL-26 03.28.17.536229 PM","","","","","","","0","REINS_SVC","7/10/2026 3:28:17 PM","REINS_SVC","7/10/2026 3:28:17 PM"
"14","63","CO646_Jul022026_10:52:24","1","SearchExistingClaim","SearchExistingClaim","COMPLETED","","LE6133","10-JUL-26 03.28.18.915985 PM","10-JUL-26 03.28.19.266762 PM","NOT_FOUND","No existing claims matched search criteria.","","","","","0","REINS_SVC","7/10/2026 3:28:18 PM","REINS_SVC","7/10/2026 3:28:19 PM"
"15","64","CO646_Jul022026_10:52:24","1","SearchExistingWorksheet","SearchExistingWorksheet","COMPLETED","","LE6133","10-JUL-26 03.28.20.619703 PM","10-JUL-26 03.28.20.929871 PM","NOT_FOUND","No open claim worksheet found.","","","","","0","REINS_SVC","7/10/2026 3:28:20 PM","REINS_SVC","7/10/2026 3:28:20 PM"
"16","65","CO646_Jul022026_10:52:24","1","SearchInforce","SearchInforce","COMPLETED","","LE6133","10-JUL-26 03.28.22.262002 PM","10-JUL-26 03.28.58.292552 PM","FOUND","Existing Inforce records found. Count=10","","","4183657901,32044603,58189991,136393933,51522761,59701181,60373433,136348817,38921354,39101595","","0","REINS_SVC","7/10/2026 3:28:22 PM","REINS_SVC","7/10/2026 3:28:58 PM"
"17","66","CO646_Jul022026_10:52:24","1","SearchEdi","SearchEdi","COMPLETED","","LE6133","10-JUL-26 03.28.59.656655 PM","10-JUL-26 03.29.00.683896 PM","FOUND","Existing EDI records found. Count=19","","","58189991,136393933,51522761,59701181,60373433,32044603,4183657901,38921354,136348817,39101595","4032477935,3745139327,3464836473,3185068808,2903713208,2644116627,2378569103,2110279472,1842098978,1578111255,1304205742,1043249541,792884990,530037506,279697419,203280505,176191363,150043530,130175667","0","REINS_SVC","7/10/2026 3:28:59 PM","REINS_SVC","7/10/2026 3:29:00 PM"
"18","67","CO646_Jul022026_10:52:24","1","CreateWorksheet","CreateWorksheet","COMPLETED","","LE6133","10-JUL-26 03.29.02.039838 PM","10-JUL-26 03.29.52.546217 PM","FOUND","Existing Inforce records found. Count=10; Existing EDI records found. Count=19; Worksheet created from EDI; EDI Selected Record Match=PERFECT; Inforce Selected Record Match=PERFECT","","","","","223775","REINS_SVC","7/10/2026 3:29:02 PM","REINS_SVC","7/10/2026 3:29:52 PM"
"19","68","CO646_Jul022026_10:52:24","1","AttachDocuments","AttachDocuments","COMPLETED","","LE6133","10-JUL-26 03.29.54.063745 PM","10-JUL-26 03.29.56.619330 PM","ATTACHED","3 file(s) attached to 1 ClaimWorksheets.","","","","","223775","REINS_SVC","7/10/2026 3:29:54 PM","REINS_SVC","7/10/2026 3:29:56 PM"
"20","69","CO646_Jul022026_10:52:24","1","SystemGeneratedEmail","SystemGeneratedEmail","COMPLETED","","LE6133","10-JUL-26 03.29.58.077779 PM","10-JUL-26 03.29.59.769332 PM","","","","","","","223775","REINS_SVC","7/10/2026 3:29:58 PM","REINS_SVC","7/10/2026 3:29:59 PM"
"21","50","CO646_Jul022026_10:52:24","1","WORKFLOW_COMPLETED","Final","COMPLETED","","LE6133","10-JUL-26 03.29.59.860449 PM","10-JUL-26 03.29.59.944246 PM","SUCCESS","Workflow completed successfully","","","","","223775","REINS_SVC","7/10/2026 3:29:59 PM","REINS_SVC","7/10/2026 3:29:59 PM"


