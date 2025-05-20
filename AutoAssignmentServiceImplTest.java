package com.rbs.tntr.business.taggingService.service.jiraAssignment;

import com.google.common.collect.Lists;
import com.nwm.tntr.commons.domain.persistence.constant.*;
import com.nwm.tntr.commons.domain.persistence.entity.DocumentId;
import com.nwm.tntr.commons.domain.persistence.entity.DocumentLifetime;
import com.nwm.tntr.commons.domain.persistence.entity.ExceptionManagement;
import com.nwm.tntr.commons.domain.persistence.entity.NonReportableData;
import com.nwm.tntr.commons.domain.persistence.entity.TransactionReportingStatus;
import com.nwm.tntr.commons.domain.persistence.entity.collateral.CollateralReport;
import com.nwm.tntr.commons.domain.persistence.entity.collateral.CollateralReportDocument;
import com.nwm.tntr.commons.domain.persistence.entity.collateral.CollateralReportId;
import com.nwm.tntr.commons.domain.persistence.entity.collaterallink.CollateralLinkReport;
import com.nwm.tntr.commons.domain.persistence.entity.collaterallink.CollateralLinkReportDocument;
import com.nwm.tntr.commons.domain.persistence.entity.collaterallink.CollateralLinkReportId;
import com.nwm.tntr.commons.domain.persistence.entity.recon.BreakManagement;
import com.nwm.tntr.commons.domain.persistence.entity.recon.ReconReport;
import com.nwm.tntr.commons.domain.persistence.entity.recon.ReconReportDocument;
import com.nwm.tntr.commons.domain.persistence.entity.recon.ReconReportId;
import com.nwm.tntr.commons.domain.persistence.entity.trade.TradeReport;
import com.nwm.tntr.commons.domain.persistence.entity.trade.TradeReportDocument;
import com.nwm.tntr.commons.domain.persistence.entity.trade.TradeReportId;
import com.nwm.tntr.commons.domain.persistence.entity.valuation.ValuationReport;
import com.nwm.tntr.commons.domain.persistence.entity.valuation.ValuationReportDocument;
import com.nwm.tntr.commons.domain.persistence.entity.valuation.ValuationReportId;
import com.nwm.tntr.commons.repository.regreporting.WriteResult;
import com.nwm.tntr.configuration.CustomExecutorConfiguration;
import com.nwm.tntr.executors.CustomExecutor;
import com.rbs.datafabric.agile.commons.lang.StartableException;
import com.rbs.datafabric.api.exception.InsertException;
import com.rbs.datafabric.api.exception.OptimisticLockException;
import com.rbs.datafabric.api.exception.UpsertException;
import com.rbs.datafabric.common.DataFabricSerializerException;
import com.rbs.tntr.business.taggingService.configuration.TaggingServiceQueryConfiguration;
import com.rbs.tntr.business.taggingService.df.DfCollateralLinkPersistManager;
import com.rbs.tntr.business.taggingService.df.DfCollateralPersistManager;
import com.rbs.tntr.business.taggingService.df.DfReconciliationPersistManager;
import com.rbs.tntr.business.taggingService.df.DfTradePersistManager;
import com.rbs.tntr.business.taggingService.df.DfValuationPersistManager;
import com.rbs.tntr.business.taggingService.df.ExceptionManagmentUpdater;
import com.rbs.tntr.business.taggingService.df.ReconBreakManagerUpdater;
import com.rbs.tntr.business.taggingService.result.JiraAssignmentResult;
import com.rbs.tntr.business.taggingService.service.common.TaggingServiceRepository;
import com.rbs.tntr.business.taggingService.service.trigger.JiraAssignmentTriggerEvent;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.common.AutoAssignResult;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.enums.ActionType;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.enums.FlowType;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.exceptions.TaggingServiceRunTimeException;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.response.Status;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.TaggingStringConstants;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@SuppressWarnings("unchecked")
public class AutoAssignmentServiceImplTest {

    private TaggingServiceRepository taggingServiceRepository;
    private TaggingServiceQueryConfiguration taggingServiceQueryConfiguration;
    private DfTradePersistManager dfTradePersistManager;
    private DfValuationPersistManager dfValuationPersistManager;
    private DfCollateralPersistManager dfCollateralPersistManager;
    private DfCollateralLinkPersistManager dfCollateralLinkPersistManager;
    private DfReconciliationPersistManager dfReconciliationPersistManager;
    private ExceptionManagmentUpdater exceptionManagmentUpdater;
    private ReconBreakManagerUpdater reconBreakManagerUpdater;
    private AutoAssignmentServiceImpl autoAssignmentService;
    private static final String testJiraId = "TNTR-5234";
    private static final String testJiraType = IssueType.MIS_REPORTING.value();
    private CustomExecutor executor;

    @Before
    public void initialize() throws StartableException {
        dfTradePersistManager = Mockito.mock(DfTradePersistManager.class);
        dfValuationPersistManager = Mockito.mock(DfValuationPersistManager.class);
        dfCollateralPersistManager = Mockito.mock(DfCollateralPersistManager.class);
        dfCollateralLinkPersistManager = Mockito.mock(DfCollateralLinkPersistManager.class);
        dfReconciliationPersistManager = Mockito.mock(DfReconciliationPersistManager.class);
        taggingServiceRepository = Mockito.mock(TaggingServiceRepository.class);
        taggingServiceQueryConfiguration = Mockito.mock(TaggingServiceQueryConfiguration.class);
        exceptionManagmentUpdater = new ExceptionManagmentUpdater();
        reconBreakManagerUpdater = new ReconBreakManagerUpdater();
        executor = new CustomExecutor(new CustomExecutorConfiguration.Builder()
                .setActivateRunnable(true)
                .setRunnableThreadPoolName("TaggingRunnableExecutorThreadPoolThread-%d")
                .setRunnableCorePoolSize(2)
                .setRunnableMaxPoolSize(2)
                .setActivateCallable(true)
                .setCallableCorePoolSize(2)
                .setCallableMaxPoolSize(2)
                .setCallableThreadPoolName("TaggingCallableExecutorThreadPoolThread-%d")
                .build());
        autoAssignmentService = new AutoAssignmentServiceImpl(taggingServiceRepository,
                taggingServiceQueryConfiguration, dfTradePersistManager, dfValuationPersistManager,
                dfCollateralPersistManager, dfCollateralLinkPersistManager, dfReconciliationPersistManager,
                exceptionManagmentUpdater, reconBreakManagerUpdater, executor);

        Mockito.when(taggingServiceQueryConfiguration.getTradeQuery())
                .thenReturn("and _df.lifetimeFrom >= '{lifetimeFrom}' and _df.lifetimeTo >= 9223372036854775807L");
        Mockito.when(taggingServiceRepository.searchRulesFromDf(Mockito.any()))
                .thenReturn(getJiraAssignmentTriggerEvent());
        Mockito.when(dfTradePersistManager.getAllRecords(Mockito.any()))
                .thenReturn(Lists.newArrayList(getTradeReportDocument()));
        Mockito.when(dfValuationPersistManager.getAllRecords(Mockito.any()))
                .thenReturn(Lists.newArrayList(getValReportDocument()));
        Mockito.when(dfCollateralPersistManager.getAllRecords(Mockito.any()))
                .thenReturn(Lists.newArrayList(getColReportDocument()));
        Mockito.when(dfCollateralLinkPersistManager.getAllRecords(Mockito.any()))
                .thenReturn(Lists.newArrayList(getColLinkReportDocument()));
        Mockito.when(dfReconciliationPersistManager.getAllRecords(Mockito.any()))
                .thenReturn(Lists.newArrayList(getReconReportDocument()));
        Mockito.when(dfTradePersistManager.saveAllRecords(Mockito.any())).thenReturn(getWriteResults());
        Mockito.when(dfValuationPersistManager.saveAllRecords(Mockito.any())).thenReturn(getValWriteResults());
        Mockito.when(dfCollateralPersistManager.saveAllRecords(Mockito.any())).thenReturn(getColWriteResults());
        Mockito.when(dfCollateralLinkPersistManager.saveAllRecords(Mockito.any())).thenReturn(getColLinkWriteResults());
        Mockito.when(dfReconciliationPersistManager.saveAllRecords(Mockito.any())).thenReturn(getReconWriteResults());
    }

    @Test
    public void testAutoAssign_Success() {
        List<AutoAssignResult> successMsg = autoAssignmentService.autoAssign(TaggingStringConstants.WHERE_CLAUSE);

        Assert.assertNotNull(successMsg);
        Assert.assertEquals(4, successMsg.size());
        Assert.assertEquals("Trade Report_Jira Assignment_GTR-3012_EMIRSFTR_GTR-3012", successMsg.get(0).getPrimaryKey());
        Assert.assertEquals(1, successMsg.get(0).getRecordsModified());
        Assert.assertEquals(Status.SUCCESS, successMsg.get(0).getStatus());
    }

    @Test
    public void testAutoAssign_NoActiveRules() throws StartableException {
        Mockito.when(taggingServiceRepository.searchRulesFromDf(Mockito.any())).thenReturn(Collections.emptyList());
        List<AutoAssignResult> result = autoAssignmentService.autoAssign("dummyWhereClause");

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testAutoAssign_EmptyRecords() throws StartableException {
        Mockito.when(dfTradePersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());
        Mockito.when(dfValuationPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());
        Mockito.when(dfCollateralPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());
        Mockito.when(dfCollateralLinkPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());
        Mockito.when(dfReconciliationPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        List<AutoAssignResult> result = autoAssignmentService.autoAssign("dummyWhereClause");

        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.size());
        Assert.assertEquals(0, result.get(0).getRecordsModified());
        Assert.assertEquals(Status.FAILED, result.get(0).getStatus());
    }

    @Test
    public void testAutoAssign_RecordsExceedMaxVolume() throws StartableException {
        List<TradeReportDocument> largeList = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            largeList.add(getTradeReportDocument());
        }
        Mockito.when(dfTradePersistManager.getAllRecords(Mockito.any())).thenReturn(largeList);

        List<AutoAssignResult> result = autoAssignmentService.autoAssign("dummyWhereClause");

        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.size());
        Assert.assertEquals(0, result.get(0).getRecordsModified());
        Assert.assertEquals(Status.FAILED, result.get(0).getStatus());
        Assert.assertTrue(result.get(0).getComment().contains("Total records found are more than maxVolume"));
    }

    @Test
    public void testAutoAssign_FetchRecordsThrowsException() throws StartableException {
        Mockito.when(dfTradePersistManager.getAllRecords(Mockito.any()))
                .thenThrow(new RuntimeException("Fetch error"));

        List<AutoAssignResult> result = autoAssignmentService.autoAssign("dummyWhereClause");

        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.size());
        Assert.assertEquals(Status.FAILED, result.get(0).getStatus());
        Assert.assertTrue(result.get(0).getComment().contains("Fetch error"));
    }

    @Test
    public void testAutoAssign_SaveRecordsThrowsException() throws StartableException {
        Mockito.when(dfTradePersistManager.saveAllRecords(Mockito.any()))
                .thenThrow(new TaggingServiceRunTimeException("Save error"));
        List<AutoAssignResult> result = autoAssignmentService.autoAssign("dummyWhereClause");

        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.size());
        Assert.assertEquals(0, result.get(0).getRecordsModified());
        Assert.assertEquals(Status.FAILED, result.get(0).getStatus());
    }

    @Test
    public void testReTriggerRules_Success() throws StartableException {
        Mockito.when(taggingServiceRepository.searchRulesFromDf(Mockito.any()))
                .thenReturn(Lists.newArrayList(getJiraAssignmentTriggerEvent().get(0)));
        List<AutoAssignResult> results = autoAssignmentService.reTriggerRules(TaggingStringConstants.WHERE_CLAUSE);

        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("Trade Report_Jira Assignment_GTR-3012_EMIRSFTR_GTR-3012", results.get(0).getPrimaryKey());
        Assert.assertEquals(1, results.get(0).getRecordsModified());
        Assert.assertEquals(Status.SUCCESS, results.get(0).getStatus());
    }

    @Test
    public void testReTriggerRules_NoMatchingRules() throws StartableException {
        Mockito.when(taggingServiceRepository.searchRulesFromDf(Mockito.any()))
                .thenReturn(Collections.emptyList());
        List<AutoAssignResult> result = autoAssignmentService.reTriggerRules("dummyWhereClause");

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testReTriggerRules_ReconWithSubExpression() throws StartableException {
        JiraAssignmentTriggerEvent reconEvent = new JiraAssignmentTriggerEvent.Builder()
                .withId("5")
                .withPrimaryKey("Recon_Jira Assignment_GTR-3016_EMIRSFTR_GTR-3016")
                .withExpression("reconType = 'Completeness'")
                .withSubExpression("breakStatus = 'UNPAIRED'")
                .withFlow(FlowType.RECONCILIATION.toString())
                .withAction("Jira Assignment")
                .withActionValue("GTR-3016")
                .withActionValueType(IssueType.MIS_REPORTING.value())
                .withIsActive(true)
                .withLastSuccessDate("2021-11-02T06:38:10.841Z")
                .withIndexHint("recon-type")
                .withMaxVolume(10)
                .create();
        Mockito.when(taggingServiceRepository.searchRulesFromDf(Mockito.any()))
                .thenReturn(Lists.newArrayList(reconEvent));
        Mockito.when(dfReconciliationPersistManager.getAllRecords(Mockito.any()))
                .thenReturn(Lists.newArrayList(getReconReportDocument()));

        List<AutoAssignResult> result = autoAssignmentService.reTriggerRules("dummyWhereClause");

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(Status.SUCCESS, result.get(0).getStatus());
    }

    @Test
    public void testUpdateTaggingBatchDf_InsertException() throws Exception {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(0);
        JiraAssignmentResult result = new JiraAssignmentResult();
        Mockito.doThrow(new InsertException("Insert error"))
                .when(taggingServiceRepository)
                .updateEntity(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("updateTaggingBatchDf", String.class, JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, Date.class);
        method.setAccessible(true);
        method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, result, null);

        // No assertion needed; verify no unhandled exception
    }

    @Test
    public void testUpdateTaggingBatchDf_UpsertException() throws Exception {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(0);
        JiraAssignmentResult result = new JiraAssignmentResult();
        Mockito.doThrow(new UpsertException("Upsert error"))
                .when(taggingServiceRepository)
                .updateEntity(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("updateTaggingBatchDf", String.class, JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, Date.class);
        method.setAccessible(true);
        method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, result, null);

        // No assertion needed; verify no unhandled exception
    }

    @Test
    public void testUpdateTaggingBatchDf_OptimisticLockException() throws Exception {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(0);
        JiraAssignmentResult result = new JiraAssignmentResult();
        Mockito.doThrow(new OptimisticLockException("Optimistic lock error"))
                .when(taggingServiceRepository)
                .updateEntity(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("updateTaggingBatchDf", String.class, JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, Date.class);
        method.setAccessible(true);
        method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, result, null);

        // No assertion needed; verify no unhandled exception
    }

    @Test
    public void testUpdateTaggingBatchDf_DataFabricSerializerException() throws Exception {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(0);
        JiraAssignmentResult result = new JiraAssignmentResult();
        Mockito.doThrow(new DataFabricSerializerException("Serializer error"))
                .when(taggingServiceRepository)
                .updateEntity(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("updateTaggingBatchDf", String.class, JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, Date.class);
        method.setAccessible(true);
        method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, result, null);

        // No assertion needed; verify no unhandled exception
    }

    @Test
    public void testUpdateTaggingBatchDf_StartableException() throws Exception {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(0);
        JiraAssignmentResult result = new JiraAssignmentResult();
        Mockito.doThrow(new StartableException("Startable error"))
                .when(taggingServiceRepository)
                .updateEntity(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("updateTaggingBatchDf", String.class, JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, Date.class);
        method.setAccessible(true);
        method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, result, null);

        // No assertion needed; verify no unhandled exception
    }

    @Test
    public void testAssignTradeActions_EmptyRecords() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(0);
        List<AutoAssignResult> results = new ArrayList<>();
        Mockito.when(dfTradePersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignTradeActions", String.class, JiraAssignmentTriggerEvent.class, List.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, results);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Status.FAILED, results.get(0).getStatus());
        Assert.assertEquals(0, results.get(0).getRecordsModified());
    }

    @Test
    public void testAssignTradeActions_RecordsExceedMaxVolume() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(0);
        List<AutoAssignResult> results = new ArrayList<>();
        List<TradeReportDocument> largeList = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            largeList.add(getTradeReportDocument());
        }
        Mockito.when(dfTradePersistManager.getAllRecords(Mockito.any())).thenReturn(largeList);

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignTradeActions", String.class, JiraAssignmentTriggerEvent.class, List.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, results);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Status.FAILED, results.get(0).getStatus());
        Assert.assertTrue(results.get(0).getComment().contains("Total records found are more than maxVolume"));
    }

    @Test
    public void testAssignTradeActions_FetchException() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(0);
        List<AutoAssignResult> results = new ArrayList<>();
        Mockito.when(dfTradePersistManager.getAllRecords(Mockito.any()))
                .thenThrow(new RuntimeException("Fetch error"));

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignTradeActions", String.class, JiraAssignmentTriggerEvent.class, List.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, results);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Status.FAILED, results.get(0).getStatus());
        Assert.assertTrue(results.get(0).getComment().contains("Fetch error"));
    }

    @Test
    public void testAssignValuationActions_EmptyRecords() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(1);
        List<AutoAssignResult> results = new ArrayList<>();
        Mockito.when(dfValuationPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignValuationActions", String.class, JiraAssignmentTriggerEvent.class, List.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, results);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Status.FAILED, results.get(0).getStatus());
        Assert.assertEquals(0, results.get(0).getRecordsModified());
    }

    @Test
    public void testAssignCollateralActions_EmptyRecords() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(2);
        List<AutoAssignResult> results = new ArrayList<>();
        Mockito.when(dfCollateralPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignCollateralActions", String.class, JiraAssignmentTriggerEvent.class, List.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, results);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Status.FAILED, results.get(0).getStatus());
        Assert.assertEquals(0, results.get(0).getRecordsModified());
    }

    @Test
    public void testAssignCollateralLinkActions_EmptyRecords() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(3);
        List<AutoAssignResult> results = new ArrayList<>();
        Mockito.when(dfCollateralLinkPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignCollateralLinkActions", String.class, JiraAssignmentTriggerEvent.class, List.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, results);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Status.FAILED, results.get(0).getStatus());
        Assert.assertEquals(0, results.get(0).getRecordsModified());
    }

    @Test
    public void testAssignReconActions_EmptyRecords() {
        JiraAssignmentTriggerEvent ob = new JiraAssignmentTriggerEvent.Builder()
                .withId("5")
                .withPrimaryKey("Recon_Jira Assignment_GTR-3016_EMIRSFTR_GTR-3016")
                .withExpression("reconType = 'Completeness'")
                .withFlow(FlowType.RECONCILIATION.toString())
                .withAction("Jira Assignment")
                .withActionValue("GTR-3016")
                .withActionValueType(IssueType.MIS_REPORTING.value())
                .withIsActive(true)
                .withLastSuccessDate("2021-11-02T06:38:10.841Z")
                .withIndexHint("recon-type")
                .withMaxVolume(10)
                .create();
        List<AutoAssignResult> results = new ArrayList<>();
        Mockito.when(dfReconciliationPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignReconActions", String.class, JiraAssignmentTriggerEvent.class, List.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, results);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Status.FAILED, results.get(0).getStatus());
        Assert.assertEquals(0, results.get(0).getRecordsModified());
    }

    @Test
    public void testAssignReconActions_WithSubExpression() {
        JiraAssignmentTriggerEvent ob = new JiraAssignmentTriggerEvent.Builder()
                .withId("5")
                .withPrimaryKey("Recon_Jira Assignment_GTR-3016_EMIRSFTR_GTR-3016")
                .withExpression("reconType = 'Completeness'")
                .withSubExpression("breakStatus = 'UNPAIRED'")
                .withFlow(FlowType.RECONCILIATION.toString())
                .withAction("Jira Assignment")
                .withActionValue("GTR-3016")
                .withActionValueType(IssueType.MIS_REPORTING.value())
                .withIsActive(true)
                .withLastSuccessDate("2021-11-02T06:38:10.841Z")
                .withIndexHint("recon-type")
                .withMaxVolume(10)
                .create();
        List<AutoAssignResult> results = new ArrayList<>();
        Mockito.when(dfReconciliationPersistManager.getAllRecords(Mockito.any()))
                .thenReturn(Lists.newArrayList(getReconReportDocument()));

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignReconActions", String.class, JiraAssignmentTriggerEvent.class, List.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, "2021-11-02T06:38:10.841Z", ob, results);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Status.SUCCESS, results.get(0).getStatus());
        Assert.assertEquals(1, results.get(0).getRecordsModified());
    }

    @Test
    public void testGetFilteredRules() throws Exception {
        List<JiraAssignmentTriggerEvent> events = getJiraAssignmentTriggerEvent();
        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("getFilteredRules", List.class, String.class);
        method.setAccessible(true);
        List<JiraAssignmentTriggerEvent> filtered = (List<JiraAssignmentTriggerEvent>) method.invoke(autoAssignmentService, events, FlowType.TRADE_REPORT.getValue());

        Assert.assertEquals(1, filtered.size());
        Assert.assertEquals("Trade Report", filtered.get(0).getFlow());
    }

    @Test
    public void testGetFilteredRulesForRetrigger() throws Exception {
        List<JiraAssignmentTriggerEvent> events = getJiraAssignmentTriggerEvent();
        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("getFilteredRulesForRetrigger", List.class, String.class);
        method.setAccessible(true);
        List<JiraAssignmentTriggerEvent> filtered = (List<JiraAssignmentTriggerEvent>) method.invoke(autoAssignmentService, events, FlowType.RECONCILIATION.getValue());

        Assert.assertTrue(filtered.isEmpty());
    }

    @Test
    public void testGetWhereClause() throws Exception {
        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("getWhereClause", String.class, String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(autoAssignmentService, "testExpression", "2021-11-02T06:38:10.841Z");

        Assert.assertEquals("testExpression and _df.lifetimeFrom >= '2021-11-02T06:38:10.841Z' and _df.lifetimeTo >= 9223372036854775807L", result);
    }

    @Test
    public void testGetWhereClauseForRecon() throws Exception {
        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("getWhereClauseForRecon", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(autoAssignmentService, "testExpression");

        Assert.assertEquals("testExpression ", result);
    }

    @Test
    public void testGetReportsWithExpectedBaseVersion() throws Exception {
        TradeReportDocument doc = getTradeReportDocument();
        TradeReport report = doc.getTradeReport();
        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("getReportsWithExpectedBaseVersion", TradeReportDocument.class, TradeReport.class);
        method.setAccessible(true);
        Pair<TradeReport, Long> result = (Pair<TradeReport, Long>) method.invoke(autoAssignmentService, doc, report);

        Assert.assertEquals(report, result.getLeft());
        Assert.assertEquals(Long.valueOf(1), result.getRight());
    }

    @Test
    public void testAssignActionToTrade_Success() {
        String jiraId = "TNTR-7845";
        List<Pair<TradeReport, Long>> tradeReports = autoAssignmentService.assignActionToTrade(
                Lists.newArrayList(getTradeReportDocument()), jiraId, IssueType.UNDER_REPORTING.value());

        Assert.assertNotNull(tradeReports);
        Assert.assertNotNull(tradeReports.get(0).getKey().getExceptionManagement());
        Assert.assertEquals(IssueType.UNDER_REPORTING, tradeReports.get(0).getKey().getExceptionManagement().getIssueType());
        Assert.assertEquals(3, tradeReports.get(0).getKey().getExceptionManagement().getIssueIds().size());
        Assert.assertEquals(jiraId, tradeReports.get(0).getKey().getExceptionManagement().getIssueIds().get(2));
    }

    @Test
    public void testAssignActionToTrade_ThrowsException() {
        Mockito.when(dfTradePersistManager.getAllRecords(Mockito.any()))
                .thenReturn(Lists.newArrayList(getTradeReportDocument()));
        Mockito.doThrow(new RuntimeException("Update error")).when(exceptionManagmentUpdater)
                .addJiraIdToException(Mockito.any(), Mockito.any(), Mockito.any());

        try {
            autoAssignmentService.assignActionToTrade(Lists.newArrayList(getTradeReportDocument()), "TNTR-7845", IssueType.UNDER_REPORTING.value());
            Assert.fail("Expected TaggingServiceRunTimeException");
        } catch (TaggingServiceRunTimeException e) {
            Assert.assertTrue(e.getMessage().contains("Error occurred while adding jira in ExceptionManagement"));
        }
    }

    @Test
    public void testAssignActionToValuation_Success() {
        String jiraId = "TNTR-7845";
        List<Pair<ValuationReport, Long>> tradeReports = autoAssignmentService.assignActionToValuation(
                Lists.newArrayList(getValReportDocument()), jiraId, IssueType.UNDER_REPORTING.value());

        Assert.assertNotNull(tradeReports);
        Assert.assertNotNull(tradeReports.get(0).getKey().getExceptionManagement());
        Assert.assertEquals(IssueType.UNDER_REPORTING, tradeReports.get(0).getKey().getExceptionManagement().getIssueType());
        Assert.assertEquals(3, tradeReports.get(0).getKey().getExceptionManagement().getIssueIds().size());
        Assert.assertEquals(jiraId, tradeReports.get(0).getKey().getExceptionManagement().getIssueIds().get(2));
    }

    @Test
    public void testAssignActionToCollateral_Success() {
        String jiraId = "TNTR-7845";
        List<Pair<CollateralReport, Long>> tradeReports = autoAssignmentService.assignActionToCollateral(
                Lists.newArrayList(getColReportDocument()), jiraId, IssueType.OVER_REPORTING.value());

        Assert.assertNotNull(tradeReports);
        Assert.assertNotNull(tradeReports.get(0).getKey().getExceptionManagement());
        Assert.assertEquals(IssueType.OVER_REPORTING, tradeReports.get(0).getKey().getExceptionManagement().getIssueType());
        Assert.assertEquals(3, tradeReports.get(0).getKey().getExceptionManagement().getIssueIds().size());
        Assert.assertEquals(jiraId, tradeReports.get(0).getKey().getExceptionManagement().getIssueIds().get(2));
    }

    @Test
    public void testAssignActionToCollateralLink_Success() {
        String jiraId = "TNTR-7845";
        List<Pair<CollateralLinkReport, Long>> tradeReports = autoAssignmentService.assignActionToCollateralLink(
                Lists.newArrayList(getColLinkReportDocument()), jiraId, IssueType.MIS_REPORTING.value());

        Assert.assertNotNull(tradeReports);
        Assert.assertNotNull(tradeReports.get(0).getKey().getExceptionManagement());
        Assert.assertEquals(IssueType.MIS_REPORTING, tradeReports.get(0).getKey().getExceptionManagement().getIssueType());
        Assert.assertEquals(3, tradeReports.get(0).getKey().getExceptionManagement().getIssueIds().size());
        Assert.assertEquals(jiraId, tradeReports.get(0).getKey().getExceptionManagement().getIssueIds().get(2));
    }

    @Test
    public void testAssignActionToRecon_Success() {
        String jiraId = "TNTR-7845";
        List<Pair<ReconReport, Long>> reconReports = autoAssignmentService.assignActionToRecon(
                Lists.newArrayList(getReconReportDocument()), ActionType.JIRA_ASSIGNMENT.getValue(), jiraId,
                IssueType.UNDER_REPORTING.value());

        Assert.assertNotNull(reconReports);
        Assert.assertNotNull(reconReports.get(0).getKey().getBreakManagement());
        Assert.assertEquals(IssueType.UNDER_REPORTING.value(), reconReports.get(0).getKey().getBreakManagement().getIssueType());
        Assert.assertEquals(2, reconReports.get(0).getKey().getBreakManagement().getIssueTrackingReferences().size());
        Assert.assertEquals("philvdp", reconReports.get(0).getKey().getBreakManagement().getAssignedTo());
        Assert.assertEquals("Test Comment", reconReports.get(0).getKey().getBreakManagement().getUserComment());
    }

    @Test
    public void testAssignActionToRecon_ThrowsException() {
        Mockito.when(dfReconciliationPersistManager.getAllRecords(Mockito.any()))
                .thenReturn(Lists.newArrayList(getReconReportDocument()));
        Mockito.doThrow(new RuntimeException("Update error")).when(reconBreakManagerUpdater)
                .addActionToBreakManagement(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        try {
            autoAssignmentService.assignActionToRecon(Lists.newArrayList(getReconReportDocument()),
                    ActionType.JIRA_ASSIGNMENT.getValue(), "TNTR-7845", IssueType.UNDER_REPORTING.value());
            Assert.fail("Expected TaggingServiceRunTimeException");
        } catch (TaggingServiceRunTimeException e) {
            Assert.assertTrue(e.getMessage().contains("Error occurred while adding"));
        }
    }

    @Test
    public void testFilterJiraReference_NullExceptionManagement() {
        boolean value = autoAssignmentService.filterJiraReference(null, testJiraId, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReference_NullIssueIdsAndType() {
        ExceptionManagement em = getTradeReportWithNullExceptionMgt().getTradeReport().getExceptionManagement();
        boolean value = autoAssignmentService.filterJiraReference(em, testJiraId, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReference_NullIssueIds() {
        ExceptionManagement em = getTradeReportWithNullJiraId().getTradeReport().getExceptionManagement();
        boolean value = autoAssignmentService.filterJiraReference(em, testJiraId, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReference_NullIssueType() {
        ExceptionManagement em = getTradeReportWithNullJiraType().getTradeReport().getExceptionManagement();
        boolean value = autoAssignmentService.filterJiraReference(em, testJiraId, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReference_NullActionValue() {
        ExceptionManagement em = getTradeReportWithNullJiraId().getTradeReport().getExceptionManagement();
        boolean value = autoAssignmentService.filterJiraReference(em, null, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReference_NullActionValueType() {
        ExceptionManagement em = getTradeReportWithNullJiraType().getTradeReport().getExceptionManagement();
        boolean value = autoAssignmentService.filterJiraReference(em, testJiraId, null);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReference_SameIssueTypeAndId() throws Exception {
        Class<?> builderClass = Class.forName("com.nwm.tntr.commons.domain.persistence.entity.ExceptionManagement$Builder");
        Constructor<?> builderConstructor = builderClass.getDeclaredConstructor();
        builderConstructor.setAccessible(true);
        Object builderInstance = builderConstructor.newInstance();
        Method withIssueTypeMethod = builderClass.getMethod("withIssueType", IssueType.class);
        withIssueTypeMethod.invoke(builderInstance, IssueType.UNDER_REPORTING);
        Method withIssueIdsMethod = builderClass.getMethod("withIssueIds", List.class);
        withIssueIdsMethod.invoke(builderInstance, Collections.singletonList("TNTR-1234"));
        Method buildMethod = builderClass.getMethod("build");
        ExceptionManagement em = (ExceptionManagement) buildMethod.invoke(builderInstance);

        boolean result = autoAssignmentService.filterJiraReference(em, "TNTR-1234", IssueType.UNDER_REPORTING.value());
        Assert.assertFalse(result);
    }

    @Test
    public void testFilterJiraReference_SameIssueId() {
        ExceptionManagement em = getTradeReportWithNullJiraType().getTradeReport().getExceptionManagement();
        boolean value = autoAssignmentService.filterJiraReference(em, "TNTR-1234", null);
        Assert.assertFalse(value);
    }

    @Test
    public void testFilterJiraReference_SameIssueType() {
        ExceptionManagement em = getTradeReportWithNullJiraId().getTradeReport().getExceptionManagement();
        boolean value = autoAssignmentService.filterJiraReference(em, null, IssueType.UNDER_REPORTING.value());
        Assert.assertFalse(value);
    }

    @Test
    public void testFilterJiraReference_DifferentIssueIdAndType() {
        ExceptionManagement em = getTradeReportDocument().getTradeReport().getExceptionManagement();
        boolean value = autoAssignmentService.filterJiraReference(em, "TNTR-9234", IssueType.OVER_REPORTING.value());
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_NullBreakManagement() {
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(null, ActionType.JIRA_ASSIGNMENT.getValue(),
                testJiraId, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_NullIssueIdsAndType() {
        BreakManagement em = getReconReportDocumentWithNullBreakManagement().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.JIRA_ASSIGNMENT.getValue(),
                testJiraId, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_NullIssueIds() {
        BreakManagement em = getReconReportDocumentWithNullJiraId().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.JIRA_ASSIGNMENT.getValue(),
                testJiraId, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_NullIssueType() {
        BreakManagement em = getReconReportDocumentWithNullJiraType().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.JIRA_ASSIGNMENT.getValue(),
                testJiraId, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_NullActionValue() {
        BreakManagement em = getReconReportDocumentWithNullJiraId().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.JIRA_ASSIGNMENT.getValue(),
                null, testJiraType);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_NullActionValueType() {
        BreakManagement em = getReconReportDocumentWithNullJiraType().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.JIRA_ASSIGNMENT.getValue(),
                testJiraId, null);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_SameIssueId() {
        BreakManagement em = getReconReportDocument().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.JIRA_ASSIGNMENT.getValue(),
                "TTR-1010", null);
        Assert.assertFalse(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_SameIssueType() {
        BreakManagement em = getReconReportDocument().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.JIRA_ASSIGNMENT.getValue(),
                null, "Under Reporting");
        Assert.assertFalse(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_SameIssueIdAndType() {
        BreakManagement em = getReconReportDocument().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.JIRA_ASSIGNMENT.getValue(),
                "TTR-1010", "Under Reporting");
        Assert.assertFalse(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_DifferentIssueIdAndType() {
        BreakManagement em = getReconReportDocument().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.JIRA_ASSIGNMENT.getValue(),
                "TNTR-1234", "Over Reporting");
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_NullAction() {
        BreakManagement em = getReconReportDocument().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, null, "TNTR-1234", "Over Reporting");
        Assert.assertFalse(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_NewComment() {
        BreakManagement em = getReconReportDocumentWithNullComments().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.ADD_COMMENT.getValue(),
                "Test Comment", null);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_ExistingComment() {
        BreakManagement em = getReconReportDocumentWithExistingComment().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.ADD_COMMENT.getValue(),
                "New Test Comment", null);
        Assert.assertTrue(value);
    }

    @Test
    public void testFilterJiraReferenceForRecon_NullComment() {
        BreakManagement em = getReconReportDocumentWithExistingComment().getReconReport().getBreakManagement();
        boolean value = autoAssignmentService.filterJiraReferenceForRecon(em, ActionType.ADD_COMMENT.getValue(), null,
                null);
        Assert.assertFalse(value);
    }

    @Test
    public void testGetAllTradeRecords_EmptyList() {
        Mockito.when(dfTradePersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());
        List<TradeReportDocument> result = autoAssignmentService.getAllTradeRecords("testWhere", "indexHint", testJiraId, testJiraType);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testGetAllValuationRecords_EmptyList() {
        Mockito.when(dfValuationPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());
        List<ValuationReportDocument> result = autoAssignmentService.getAllValuationRecords("testWhere", "indexHint", testJiraId, testJiraType);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testGetAllCollateralRecords_EmptyList() {
        Mockito.when(dfCollateralPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());
        List<CollateralReportDocument> result = autoAssignmentService.getAllCollateralRecords("testWhere", "indexHint", testJiraId, testJiraType);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testGetAllCollateralLinkRecords_EmptyList() {
        Mockito.when(dfCollateralLinkPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());
        List<CollateralLinkReportDocument> result = autoAssignmentService.getAllCollateralLinkRecords("testWhere", "indexHint", testJiraId, testJiraType);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testGetAllReconRecords_EmptyList() {
        Mockito.when(dfReconciliationPersistManager.getAllRecords(Mockito.any())).thenReturn(Collections.emptyList());
        List<ReconReportDocument> result = autoAssignmentService.getAllReconRecords("testWhere", "indexHint", ActionType.JIRA_ASSIGNMENT.getValue(), testJiraId, testJiraType);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testAssignActionsToTradeRecords_EmptyResults() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(0);
        JiraAssignmentResult result = new JiraAssignmentResult();
        List<TradeReportDocument> docs = Lists.newArrayList(getTradeReportDocument());
        Mockito.when(dfTradePersistManager.saveAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignActionsToTradeRecords", JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, List.class, int.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, ob, result, docs, 1);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(0, result.getRecordModified());
        Assert.assertEquals(Status.SUCCESS, result.getStatus());
    }

    @Test
    public void testAssignActionsToValuationRecords_EmptyResults() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(1);
        JiraAssignmentResult result = new JiraAssignmentResult();
        List<ValuationReportDocument> docs = Lists.newArrayList(getValReportDocument());
        Mockito.when(dfValuationPersistManager.saveAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignActionsToValuationRecord", JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, List.class, int.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, ob, result, docs, 1);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(0, result.getRecordModified());
        Assert.assertEquals(Status.SUCCESS, result.getStatus());
    }

    @Test
    public void testAssignActionsToCollateralRecords_EmptyResults() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(2);
        JiraAssignmentResult result = new JiraAssignmentResult();
        List<CollateralReportDocument> docs = Lists.newArrayList(getColReportDocument());
        Mockito.when(dfCollateralPersistManager.saveAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignActionsToCollateralRecord", JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, List.class, int.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, ob, result, docs, 1);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(0, result.getRecordModified());
        Assert.assertEquals(Status.SUCCESS, result.getStatus());
    }

    @Test
    public void testAssignActionsToCollateralLinkRecords_EmptyResults() {
        JiraAssignmentTriggerEvent ob = getJiraAssignmentTriggerEvent().get(3);
        JiraAssignmentResult result = new JiraAssignmentResult();
        List<CollateralLinkReportDocument> docs = Lists.newArrayList(getColLinkReportDocument());
        Mockito.when(dfCollateralLinkPersistManager.saveAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignActionsToCollateralLinkRecord", JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, List.class, int.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, ob, result, docs, 1);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(0, result.getRecordModified());
        Assert.assertEquals(Status.SUCCESS, result.getStatus());
    }

    @Test
    public void testAssignActionsToReconRecords_EmptyResults() {
        JiraAssignmentTriggerEvent ob = new JiraAssignmentTriggerEvent.Builder()
                .withId("5")
                .withPrimaryKey("Recon_Jira Assignment_GTR-3016_EMIRSFTR_GTR-3016")
                .withExpression("reconType = 'Completeness'")
                .withFlow(FlowType.RECONCILIATION.toString())
                .withAction("Jira Assignment")
                .withActionValue("GTR-3016")
                .withActionValueType(IssueType.MIS_REPORTING.value())
                .withIsActive(true)
                .withLastSuccessDate("2021-11-02T06:38:10.841Z")
                .withIndexHint("recon-type")
                .withMaxVolume(10)
                .create();
        JiraAssignmentResult result = new JiraAssignmentResult();
        List<ReconReportDocument> docs = Lists.newArrayList(getReconReportDocument());
        Mockito.when(dfReconciliationPersistManager.saveAllRecords(Mockito.any())).thenReturn(Collections.emptyList());

        Method method;
        try {
            method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignActionsToReconRecords", JiraAssignmentTriggerEvent.class, JiraAssignmentResult.class, List.class, int.class);
            method.setAccessible(true);
            method.invoke(autoAssignmentService, ob, result, docs, 1);
        } catch (Exception e) {
            Assert.fail("Exception during reflection: " + e.getMessage());
        }

        Assert.assertEquals(0, result.getRecordModified());
        Assert.assertEquals(Status.SUCCESS, result.getStatus());
    }

    @Test
    public void testAssignActionsPerFlow_DefaultCase() throws Exception {
        List<JiraAssignmentTriggerEvent> events = Lists.newArrayList(new JiraAssignmentTriggerEvent.Builder()
                .withId("6")
                .withPrimaryKey("Unknown_Jira Assignment_GTR-3017")
                .withExpression("testExpression")
                .withFlow("Unknown Flow")
                .withAction("Jira Assignment")
                .withActionValue("GTR-3017")
                .withActionValueType(IssueType.MIS_REPORTING.value())
                .withIsActive(true)
                .withLastSuccessDate("2021-11-02T06:38:10.841Z")
                .withIndexHint("index-hint")
                .withMaxVolume(10)
                .create());
        Method method = AutoAssignmentServiceImpl.class.getDeclaredMethod("assignActionsPerFlow", List.class, String.class, FlowType.class);
        method.setAccessible(true);
        List<AutoAssignResult> results = (List<AutoAssignResult>) method.invoke(autoAssignmentService, events, "2021-11-02T06:38:10.841Z", FlowType.UNKNOWN);

        Assert.assertTrue(results.isEmpty());
    }

    private List<JiraAssignmentTriggerEvent> getJiraAssignmentTriggerEvent() {
        List<JiraAssignmentTriggerEvent> ob = Lists.newArrayList();
        JiraAssignmentTriggerEvent obTrade = new JiraAssignmentTriggerEvent.Builder().withId("1")
                .withPrimaryKey("Trade Report_Jira Assignment_GTR-3012_EMIRSFTR_GTR-3012")
                .withExpression(
                        "subjectIdentifier.transactionId = '135705760' and subjectIdentifier.sourceSystem = 'GDS GBLO'")
                .withFlow("Trade Report").withAction("Jira Assignment").withActionValue("GTR-3012")
                .withActionValueType(IssueType.OVER_REPORTING.value()).withIsActive(true)
                .withLastSuccessDate("2021-11-02T06:38:10.841Z").withIndexHint("blotter-tradeId").withMaxVolume(10)
                .create();

        JiraAssignmentTriggerEvent obValuation = new JiraAssignmentTriggerEvent.Builder().withId("2")
                .withPrimaryKey("Valuation Report_Jira Assignment_GTR-3013_EMIRSFTR_GTR-3013")
                .withExpression(
                        "subjectIdentifier.transactionId = '135705761' and subjectIdentifier.sourceSystem = 'GDS GBLO'")
                .withFlow("Valuation Report").withAction("Jira Assignment").withActionValue("GTR-3013")
                .withActionValueType(IssueType.MIS_REPORTING.value()).withIsActive(true)
                .withLastSuccessDate("2021-11-02T06:38:10.841Z").withIndexHint("blotter-tradeId").withMaxVolume(10)
                .create();

        JiraAssignmentTriggerEvent obCollateral = new JiraAssignmentTriggerEvent.Builder().withId("3")
                .withPrimaryKey("Collateral Report_Jira Assignment_GTR-3014_EMIRSFTR_GTR-3014")
                .withExpression(
                        "subjectIdentifier.collateralPortfolioGroup = 'COLL123' and subjectIdentifier.tradeParty1Id = 'tp1'")
                .withFlow("Collateral Report").withAction("Jira Assignment").withActionValue("GTR-3014")
                .withActionValueType(IssueType.OVER_REPORTING.value()).withIsActive(true)
                .withLastSuccessDate("2021-11-02T06:38:10.841Z").withIndexHint("collateral-portfolio").withMaxVolume(10)
                .create();

        JiraAssignmentTriggerEvent obCollateralLink = new JiraAssignmentTriggerEvent.Builder().withId("4")
                .withPrimaryKey("Collateral Link Report_Jira Assignment_GTR-3015_EMIRSFTR_GTR-3015")
                .withExpression(
                        "subjectIdentifier.transactionId = '135705762' and subjectIdentifier.sourceSystem = 'SYSTEM_X'")
                .withFlow("Collateral Link Report").withAction("Jira Assignment").withActionValue("GTR-3015")
                .withActionValueType(IssueType.MIS_REPORTING.value()).withIsActive(true)
                .withLastSuccessDate("2021-11-02T06:38:10.841Z").withIndexHint("collateral-link").withMaxVolume(10)
                .create();

        ob.add(obTrade);
        ob.add(obValuation);
        ob.add(obCollateral);
        ob.add(obCollateralLink);

        return ob;
    }

    private TradeReportDocument getTradeReportWithNullExceptionMgt() {
        DocumentId documentId = DocumentId.from("TradeReportId_123", 1L);
        DocumentLifetime documentLifetime = DocumentLifetime.from(ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC));
        TradeReport tradeReport = TradeReport.newBuilder()
                .withTradeReportId(TradeReportId.newBuilder().withTradeSourceSystemTransactionId("TradeReportId_123")
                        .withTradeSourceSystemId(SourceSystemId.ANVIL_GBLO_LDN).withTradeDocumentVersion(1)
                        .withReportingRegime(ReportingRegime.SECURITIES_FINANCING_TRANSACTION_REGULATION_SELF_REPORTING)
                        .withReportingRegulatoryAuthority(
                                ReportingRegulatoryAuthority.EUROPEAN_SECURITIES_AND_MARKETS_AUTHORITY_EUROPEAN_UNION)
                        .withRegimeImpactType(RegimeImpactType.TRANSACTION_REPORTING)
                        .withReportTriggerType(ReportTriggerType.TRANSACTION_LIFECYCLE)
                        .withReportSubmissionType(ReportSubmissionType.SNAPSHOT).build())
                .withExceptionManagement(ExceptionManagement.newBuilder().build())
                .withNonReportableData(NonReportableData.newBuilder().withLatestVersion(true).build()).build();
        return TradeReportDocument.from(documentId, documentLifetime, tradeReport);
    }

    private TradeReportDocument getTradeReportWithNullJiraId() {
        DocumentId documentId = DocumentId.from("TradeReportId_123", 1L);
        DocumentLifetime documentLifetime = DocumentLifetime.from(ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC));
        TradeReport tradeReport = TradeReport.newBuilder()
                .withTradeReportId(TradeReportId.newBuilder().withTradeSourceSystemTransactionId("TradeReportId_123")
                        .withTradeSourceSystemId(SourceSystemId.ANVIL_GBLO_LDN).withTradeDocumentVersion(1)
                        .withReportingRegime(ReportingRegime.SECURITIES_FINANCING_TRANSACTION_REGULATION_SELF_REPORTING)
                        .withReportingRegulatoryAuthority(
                                ReportingRegulatoryAuthority.EUROPEAN_SECURITIES_AND_MARKETS_AUTHORITY_EUROPEAN_UNION)
                        .withRegimeImpactType(RegimeImpactType.TRANSACTION_REPORTING)
                        .withReportTriggerType(ReportTriggerType.TRANSACTION_LIFECYCLE)
                        .withReportSubmissionType(ReportSubmissionType.SNAPSHOT).build())
                .withExceptionManagement(ExceptionManagement.newBuilder().withApprovedBy("PERSON_1")
                        .withAssignedTo("matbina")
                        .withExceptionStatus(com.nwm.tntr.commons.domain.persistence.constant.ExceptionStatus.OPEN)
                        .withApprovalStatus(ApprovalStatus.OPEN_REQUESTED).withIssueType(IssueType.UNDER_REPORTING)
                        .withLastAction("Replay Edit").withLastActionDateTime(ZonedDateTime.now(ZoneOffset.UTC))
                        .withLastActionUser("USER_1").withLatestVersion(false)
                        .withReportingDeadline(ZonedDateTime.now(ZoneOffset.UTC).plusDays(2))
                        .withUserComment("COMMENT 1").build())
                .withNonReportableData(NonReportableData.newBuilder().withLatestVersion(true).build()).build();
        return TradeReportDocument.from(documentId, documentLifetime, tradeReport);
    }

    private TradeReportDocument getTradeReportWithNullJiraType() {
        DocumentId documentId = DocumentId.from("TradeReportId_123", 1L);
        DocumentLifetime documentLifetime = DocumentLifetime.from(ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC));
        TradeReport tradeReport = TradeReport.newBuilder()
                .withTradeReportId(TradeReportId.newBuilder().withTradeSourceSystemTransactionId("TradeReportId_123")
                        .withTradeSourceSystemId(SourceSystemId.ANVIL_GBLO_LDN).withTradeDocumentVersion(1)
                        .withReportingRegime(ReportingRegime.SECURITIES_FINANCING_TRANSACTION_REGULATION_SELF_REPORTING)
                        .withReportingRegulatoryAuthority(
                                ReportingRegulatoryAuthority.EUROPEAN_SECURITIES_AND_MARKETS_AUTHORITY_EUROPEAN_UNION)
                        .withRegimeImpactType(RegimeImpactType.TRANSACTION_REPORTING)
                        .withReportTriggerType(ReportTriggerType.TRANSACTION_LIFECYCLE)
                        .withReportSubmissionType(ReportSubmissionType.SNAPSHOT).build())
                .withExceptionManagement(ExceptionManagement.newBuilder().withApprovedBy("PERSON_1")
                        .withAssignedTo("matbina").withIssueIds(Lists.newArrayList("TNTR-1234", "TNTR-9875"))
                        .withExceptionStatus(com.nwm.tntr.commons.domain.persistence.constant.ExceptionStatus.OPEN)
                        .withApprovalStatus(ApprovalStatus.OPEN_REQUESTED).withLastAction("Replay Edit")
                        .withLastActionDateTime(ZonedDateTime.now(ZoneOffset.UTC)).withLastActionUser("USER_1")
                        .withLatestVersion(false).withReportingDeadline(ZonedDateTime.now(ZoneOffset.UTC).plusDays(2))
                        .withUserComment("COMMENT 1").build())
                .withNonReportableData(NonReportableData.newBuilder().withLatestVersion(true).build()).build();
        return TradeReportDocument.from(documentId, documentLifetime, tradeReport);
    }

    private TradeReportDocument getTradeReportDocument() {
        DocumentId documentId = DocumentId.from("TradeReportId_123", 1L);
        DocumentLifetime documentLifetime = DocumentLifetime.from(ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC));
        TradeReport tradeReport = TradeReport.newBuilder()
                .withTradeReportId(TradeReportId.newBuilder().withTradeSourceSystemTransactionId("TradeReportId_123")
                        .withTradeSourceSystemId(SourceSystemId.ANVIL_GBLO_LDN).withTradeDocumentVersion(1)
                        .withReportingRegime(ReportingRegime.SECURITIES_FINANCING_TRANSACTION_REGULATION_SELF_REPORTING)
                        .withReportingRegulatoryAuthority(
                                ReportingRegulatoryAuthority.EUROPEAN_SECURITIES_AND_MARKETS_AUTHORITY_EUROPEAN_UNION)
                        .withRegimeImpactType(RegimeImpactType.TRANSACTION_REPORTING)
                        .withReportTriggerType(ReportTriggerType.TRANSACTION_LIFECYCLE)
                        .withReportSubmissionType(ReportSubmissionType.SNAPSHOT).build())
                .withTransactionReportingStatus(TransactionReportingStatus.newBuilder()
                        .withSourceSystemId(SourceSystemId.TN_TR)
                        .withStateTransitionDateTime(ZonedDateTime.now(ZoneOffset.UTC))
                        .withStateTransitionEffectiveDateTime(ZonedDateTime.now(ZoneOffset.UTC))
                        .withTransactionStateValue(TransactionStateValue.SUBMITTED)
                        .withReportSubmissionRepository(SourceSystemId.DTCC).withCommentary("Test TradeReport").build())
                .withExceptionManagement(ExceptionManagement.newBuilder().withApprovedBy("PERSON_1")
                        .withAssignedTo("matbina")
                        .withExceptionStatus(com.nwm.tntr.commons.domain.persistence.constant.ExceptionStatus.OPEN)
                        .withApprovalStatus(ApprovalStatus.OPEN_REQUESTED)
                        .withIssueIds(Lists.newArrayList("TNTR-1234", "TNTR-9875"))
                        .withIssueType(IssueType.MIS_REPORTING).withLastAction("Replay Edit")
                        .withLastActionDateTime(ZonedDateTime.now(ZoneOffset.UTC)).withLastActionUser("USER_1")
                        .withLatestVersion(false).withReportingDeadline(ZonedDateTime.now(ZoneOffset.UTC).plusDays(2))
                        .withUserComment("COMMENT 1").build())
                .withNonReportableData(NonReportableData.newBuilder().withLatestVersion(true).build()).build();
        return TradeReportDocument.from(documentId, documentLifetime, tradeReport);
    }

    private ValuationReportDocument getValReportDocument() {
        DocumentId documentId = DocumentId.from("ValReportId_123", 1L);
        DocumentLifetime documentLifetime = DocumentLifetime.from(ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC));
        ValuationReport tradeReport = ValuationReport.newBuilder()
                .withValuationReportId(ValuationReportId.newBuilder()
                        .withTradeSourceSystemTransactionId("ValReportId_123")
                        .withTradeSourceSystemId(SourceSystemId.ANVIL_GBLO_LDN).withTradeDocumentVersion(1)
                        .withReportingRegime(ReportingRegime.SECURITIES_FINANCING_TRANSACTION_REGULATION_SELF_REPORTING)
                        .withReportingRegulatoryAuthority(
                                ReportingRegulatoryAuthority.EUROPEAN_SECURITIES_AND_MARKETS_AUTHORITY_EUROPEAN_UNION)
                        .withRegimeImpactType(RegimeImpactType.TRANSACTION_REPORTING)
                        .withReportTriggerType(ReportTriggerType.VALUATION_UPDATE)
                        .withReportSubmissionType(ReportSubmissionType.SNAPSHOT)
                        .withBusinessDate(ZonedDateTime.parse("2021-09-07T00:00:00.000Z").toLocalDate()).build())
                .withTransactionReportingStatus(TransactionReportingStatus.newBuilder()
                        .withSourceSystemId(SourceSystemId.TN_TR)
                        .withStateTransitionDateTime(ZonedDateTime.now(ZoneOffset.UTC))
                        .withStateTransitionEffectiveDateTime(ZonedDateTime.now(ZoneOffset.UTC))
                        .withTransactionStateValue(TransactionStateValue.SUBMITTED)
                        .withReportSubmissionRepository(SourceSystemId.DTCC).withCommentary("Test ValReport").build())
                .withExceptionManagement(ExceptionManagement.newBuilder().withApprovedBy("PERSON_3")
                        .withAssignedTo("matbina")
                        .withExceptionStatus(com.nwm.tntr.commons.domain.persistence.constant.ExceptionStatus.OPEN)
                        .withApprovalStatus(ApprovalStatus.OPEN_REQUESTED)
                        .withIssueIds(Lists.newArrayList("TNTR-1235", "TNTR-9876"))
                        .withIssueType(IssueType.MIS_REPORTING).withLastAction("Replay Edit")
                        .withLastActionDateTime(ZonedDateTime.now(ZoneOffset.UTC)).withLastActionUser("USER_1")
                        .withLatestVersion(true).withReportingDeadline(ZonedDateTime.now(ZoneOffset.UTC).plusDays(2))
                        .withUserComment("COMMENT 1").build())
                .withNonReportableData(NonReportableData.newBuilder().withLatestVersion(true).build()).build();
        return ValuationReportDocument.from(documentId, documentLifetime, tradeReport);
    }

    private CollateralReportDocument getColReportDocument() {
        DocumentId documentId = DocumentId.from("CollateralReportId_123", 1L);
        DocumentLifetime documentLifetime = DocumentLifetime.from(ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC));
        CollateralReport tradeReport = CollateralReport.newBuilder()
                .withCollateralReportId(CollateralReportId.newBuilder().withTradeParty1Id("tp1")
                        .withTradeParty2Id("tp2").withCollateralPortfolioGroup("CollateralReportId_123")
                        .withReportingRegime(ReportingRegime.SECURITIES_FINANCING_TRANSACTION_REGULATION_SELF_REPORTING)
                        .withReportingRegulatoryAuthority(
                                ReportingRegulatoryAuthority.EUROPEAN_SECURITIES_AND_MARKETS_AUTHORITY_EUROPEAN_UNION)
                        .withRegimeImpactType(RegimeImpactType.TRANSACTION_REPORTING)
                        .withReportSubmissionType(ReportSubmissionType.SNAPSHOT)
                        .withBusinessDate(ZonedDateTime.parse("2021-09-07T00:00:00.000Z").toLocalDate()).build())
                .withTransactionReportingStatus(TransactionReportingStatus.newBuilder()
                        .withSourceSystemId(SourceSystemId.TN_TR)
                        .withStateTransitionDateTime(ZonedDateTime.now(ZoneOffset.UTC))
                        .withStateTransitionEffectiveDateTime(ZonedDateTime.now(ZoneOffset.UTC))
                        .withTransactionStateValue(TransactionStateValue.SUBMITTED)
                        .withReportSubmissionRepository(SourceSystemId.DTCC).withCommentary("Test ValReport").build())
                .withExceptionManagement(ExceptionManagement.newBuilder().withApprovedBy("PERSON_3")
                        .withAssignedTo("matbina")
                        .withExceptionStatus(com.nwm.tntr.commons.domain.persistence.constant.ExceptionStatus.OPEN)
                        .withApprovalStatus(ApprovalStatus.OPEN_REQUESTED)
                        .withIssueIds(Lists.newArrayList("TNTR-1235", "TNTR-9876"))
                        .withIssueType(IssueType.MIS_REPORTING).withLastAction("Replay Edit")
                        .withLastActionDateTime(ZonedDateTime.now(ZoneOffset.UTC)).withLastActionUser("USER_1")
                        .withLatestVersion(true).withReportingDeadline(ZonedDateTime.now(ZoneOffset.UTC).plusDays(2))
                        .withUserComment("COMMENT 1").build())
                .withNonReportableData(NonReportableData.newBuilder().withLatestVersion(true).build()).build();
        return CollateralReportDocument.from(documentId, documentLifetime, tradeReport);
    }

    private CollateralLinkReportDocument getColLinkReportDocument() {
        DocumentId documentId = DocumentId.from("CollateralLinkReportId_123", 1L);
        DocumentLifetime documentLifetime = DocumentLifetime.from(ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC));
        CollateralLinkReport tradeReport = CollateralLinkReport.newBuilder()
                .withCollateralLinkReportId(CollateralLinkReportId.newBuilder()
                        .withBusinessDate(LocalDate.of(2021, 8, 1))
                        .withTradeSourceSystemTransactionId("CollateralLinkReportId_123")
                        .withTradeSourceSystemId(SourceSystemId.SYSTEM_X).withTradeDocumentVersion(1)
                        .withReportingRegime(ReportingRegime.EUROPEAN_MARKETS_INFRASTRUCTURE_REGULATION)
                        .withReportingRegulatoryAuthority(
                                ReportingRegulatoryAuthority.EUROPEAN_SECURITIES_AND_MARKETS_AUTHORITY_EUROPEAN_UNION)
                        .withRegimeImpactType(RegimeImpactType.TRANSACTION_REPORTING)
                        .withReportTriggerType(ReportTriggerType.COLLATERAL_UPDATE)
                        .withReportSubmissionType(ReportSubmissionType.COLLATERAL_VALUE).build())
                .withTransactionReportingStatus(TransactionReportingStatus.newBuilder()
                        .withSourceSystemId(SourceSystemId.TN_TR)
                        .withStateTransitionDateTime(ZonedDateTime.now(ZoneOffset.UTC))
                        .withStateTransitionEffectiveDateTime(ZonedDateTime.now(ZoneOffset.UTC))
                        .withTransactionStateValue(TransactionStateValue.SUBMITTED)
                        .withReportSubmissionRepository(SourceSystemId.DTCC).withCommentary("Test ValReport").build())
                .withExceptionManagement(ExceptionManagement.newBuilder().withApprovedBy("PERSON_3")
                        .withAssignedTo("matbina")
                        .withExceptionStatus(com.nwm.tntr.commons.domain.persistence.constant.ExceptionStatus.OPEN)
                        .withApprovalStatus(ApprovalStatus.OPEN_REQUESTED)
                        .withIssueIds(Lists.newArrayList("TNTR-1235", "TNTR-9876"))
                        .withIssueType(IssueType.MIS_REPORTING).withLastAction("Replay Edit")
                        .withLastActionDateTime(ZonedDateTime.now(ZoneOffset.UTC)).withLastActionUser("USER_1")
                        .withLatestVersion(true).withReportingDeadline(ZonedDateTime.now(ZoneOffset.UTC).plusDays(2))
                        .withUserComment("COMMENT 1").build())
                .withNonReportableData(NonReportableData.newBuilder().withLatestVersion(true).build()).build();
        return CollateralLinkReportDocument.from(documentId, documentLifetime, tradeReport);
    }

    private ReconReportDocument getReconReportDocument() {
        DocumentId documentId = DocumentId.from("ReconReportId_123", 1L);
        DocumentLifetime documentLifetime = DocumentLifetime.from(ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC));
        ReconReport reconReport = ReconReport.newBuilder()
                .withReconReportId(ReconReportId.newBuilder()
                        .withSourceSystemMatchingKeyId(
                                "RR3QWIC