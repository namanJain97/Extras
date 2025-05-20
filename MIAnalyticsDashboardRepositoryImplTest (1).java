package com.rbs.tntr.business.taggingService.repository;

import static com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.MIConstants.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.nwm.tntr.commons.domain.persistence.constant.AssetClass;
import com.nwm.tntr.commons.repository.df.DfConnectionManager;
import com.nwm.tntr.configuration.ItrConfiguration;
import com.rbs.datafabric.agile.commons.lang.StartableException;
import com.rbs.datafabric.api.ScanResult;
import com.rbs.datafabric.api.exception.UpsertException;
import com.rbs.datafabric.client.DataFabricClient;
import com.rbs.datafabric.domain.Document;
import com.rbs.datafabric.domain.JsonDocument;
import com.rbs.datafabric.domain.Record;
import com.rbs.datafabric.domain.client.builder.ScanRequestBuilder;
import com.rbs.datafabric.domain.client.builder.UpsertRequestBuilder;
import com.rbs.tntr.business.taggingService.service.common.ItrClient;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.common.Itr2Query;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.exceptions.TaggingServiceRunTimeException;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

public class MIAnalyticsDashboardRepositoryImplTest {

    @InjectMocks
    MIAnalyticsDashboardRepositoryImpl repo;

    @Mock
    DfConnectionManager dfConn;

    @Mock
    ItrConfiguration itrConfig;

    @Mock
    DataFabricClient dfClient;

    @Mock
    ItrClient itrClient;

    private static final ObjectMapper REAL_OBJECT_MAPPER = new ObjectMapper();
    private static final Date TEST_DATE;

    static {
        try {
            TEST_DATE = new SimpleDateFormat("yyyy-MM-dd").parse("2025-05-19");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Set configuration properties via reflection
        ReflectionTestUtils.setField(repo, "databaseName", "testDatabase");
        ReflectionTestUtils.setField(repo, "miCollection", "miCollection");
        ReflectionTestUtils.setField(repo, "foMiCollection", "foMiCollection");
        ReflectionTestUtils.setField(repo, "readTimeOut", 60);

        // Set URLs for various endpoints
        ReflectionTestUtils.setField(repo, "foEmirUrl", "emirUrl");
        ReflectionTestUtils.setField(repo, "foEmirParameterisedUrl", "emirParamUrl");
        ReflectionTestUtils.setField(repo, "foMasUrl", "masUrl");
        ReflectionTestUtils.setField(repo, "foMasParameterisedUrl", "masParamUrl");
        ReflectionTestUtils.setField(repo, "foBoiUrl", "boiUrl");
        ReflectionTestUtils.setField(repo, "foMifidUrl", "mifidUrl");
        ReflectionTestUtils.setField(repo, "foSftrUrl", "sftrUrl");
        ReflectionTestUtils.setField(repo, "foSftrAldopUrl", "sftrAldopUrl");
        ReflectionTestUtils.setField(repo, "foCftcUrl", "cftcUrl");

        // Mock common behaviors
        when(itrConfig.getItr2ProtocolScheme()).thenReturn("http");
        when(itrConfig.getItr2ServiceName()).thenReturn("testService");
    }

    // Tests for createDfClientConnection
    @Test
    public void testCreateDfClientConnection_Success() throws StartableException {
        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doReturn(dfClient).when(spyRepo).getDfClient(any(DfConnectionManager.class));

        spyRepo.createDfClientConnection();

        verify(spyRepo).getDfClient(dfConn);
    }

    @Test
    public void testCreateDfClientConnection_Exception() throws StartableException {
        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doThrow(new StartableException("Test exception")).when(spyRepo).getDfClient(any(DfConnectionManager.class));

        spyRepo.createDfClientConnection();

        verify(spyRepo).getDfClient(dfConn);
    }

    // Tests for upsertMiSnapshot
    @Test
    public void testUpsertMiSnapshot_Success() throws Exception {
        SubjectIdentifier identifier = new SubjectIdentifier("testLei", "testEntity", "testFlow", "testAsset", "testMessage", "testRegulation", TEST_DATE);
        MIDashboardAnalytics analytics = new MIDashboardAnalytics();
        analytics.setSubjectIdentifier(identifier);

        UpsertRequestBuilder mockBuilder = mock(UpsertRequestBuilder.class);
        when(mockBuilder.withDocument(any(Document.class))).thenReturn(mockBuilder);
        when(mockBuilder.withKey(anyString())).thenReturn(mockBuilder);
        when(dfClient.upsert(any(UpsertRequestBuilder.class))).thenReturn(mock(RecordId.class));

        RecordId result = repo.upsertMiSnapshot(analytics);

        assertNotNull(result);
        verify(dfClient).upsert(any(UpsertRequestBuilder.class));
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testUpsertMiSnapshot_JsonProcessingException() throws Exception {
        SubjectIdentifier identifier = new SubjectIdentifier("testLei", "testEntity", "testFlow", "testAsset", "testMessage", "testRegulation", TEST_DATE);
        MIDashboardAnalytics analytics = new MIDashboardAnalytics();
        analytics.setSubjectIdentifier(identifier);

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("Test JSON exception") {}).when(spyRepo).serialize(any());

        spyRepo.upsertMiSnapshot(analytics);
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testUpsertMiSnapshot_UpsertException() throws Exception {
        MIDashboardAnalytics stats = mock(MIDashboardAnalytics.class);
        when(stats.getSubjectIdentifier()).thenReturn(mock(SubjectIdentifier.class));

        UpsertRequestBuilder mockBuilder = mock(UpsertRequestBuilder.class);
        when(mockBuilder.withKey(anyString())).thenReturn(mockBuilder);
        when(dfClient.upsert(any(UpsertRequestBuilder.class))).thenThrow(new UpsertException(""));

        repo.upsertMiSnapshot(stats);
    }

    // Tests for upsertFoMiSnapshot
    @Test
    public void testUpsertFoMiSnapshot_Success() throws Exception {
        FrontOfficeSubjectIdentifier identifier = new FrontOfficeSubjectIdentifier("testEntity", "testLei", "testAsset", "testRegulation", TEST_DATE);
        FrontOfficeMIDashboardAnalytics analytics = new FrontOfficeMIDashboardAnalytics();
        analytics.setSubjectIdentifier(identifier);

        UpsertRequestBuilder mockBuilder = mock(UpsertRequestBuilder.class);
        when(mockBuilder.withDocument(any(Document.class))).thenReturn(mockBuilder);
        when(mockBuilder.withKey(anyString())).thenReturn(mockBuilder);
        when(dfClient.upsert(any(UpsertRequestBuilder.class))).thenReturn(mock(RecordId.class));

        RecordId result = repo.upsertFoMiSnapshot(analytics);

        assertNotNull(result);
        verify(dfClient).upsert(any(UpsertRequestBuilder.class));
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testUpsertFoMiSnapshot_JsonProcessingException() throws Exception {
        FrontOfficeSubjectIdentifier identifier = new FrontOfficeSubjectIdentifier("testEntity", "testLei", "testAsset", "testRegulation", TEST_DATE);
        FrontOfficeMIDashboardAnalytics analytics = new FrontOfficeMIDashboardAnalytics();
        analytics.setSubjectIdentifier(identifier);

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("Test JSON exception") {}).when(spyRepo).serialize(any());

        spyRepo.upsertFoMiSnapshot(analytics);
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testUpsertFoMiSnapshot_UpsertException() throws Exception {
        FrontOfficeMIDashboardAnalytics stats = mock(FrontOfficeMIDashboardAnalytics.class);
        when(stats.getSubjectIdentifier()).thenReturn(mock(FrontOfficeSubjectIdentifier.class));

        UpsertRequestBuilder mockBuilder = mock(UpsertRequestBuilder.class);
        when(mockBuilder.withDocument(any(Document.class))).thenReturn(mockBuilder);
        when(mockBuilder.withKey(anyString())).thenReturn(mockBuilder);
        when(dfClient.upsert(any(UpsertRequestBuilder.class))).thenThrow(new UpsertException(""));

        repo.upsertFoMiSnapshot(stats);
    }

    // Tests for fetchStatistics
    @Test
    public void testFetchStatistics_Success() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setCollectionName("testCollection");
        metadata.setStatName("testStat");
        String selectField = "testField";

        ScanResult mockScanResult = mock(ScanResult.class);
        Iterator<Record> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(true, false);
        Record mockRecord = mock(Record.class);
        when(mockIterator.next()).thenReturn(mockRecord);
        when(mockScanResult.iterator()).thenReturn(mockIterator);

        Document mockDocument = mock(JsonDocument.class);
        when(mockRecord.getDocument()).thenReturn(mockDocument);
        when(((JsonDocument) mockDocument).getContents()).thenReturn("{\"testField\": 42}");

        ScanRequestBuilder mockScanRequestBuilder = mock(ScanRequestBuilder.class);
        when(mockScanRequestBuilder.withReadTimeoutSeconds(anyInt())).thenReturn(mockScanRequestBuilder);
        when(dfClient.scan(mockScanRequestBuilder)).thenReturn(mockScanResult);

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doReturn(mockScanRequestBuilder).when(spyRepo).getScanRequestBuilder(any(), anyString(), anyString());

        int result = spyRepo.fetchStatistics(metadata, selectField);
        assertEquals(42, result);
    }

    @Test
    public void testFetchStatistics_NoRecords() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setCollectionName("testCollection");
        metadata.setStatName("testStat");
        String selectField = "testField";

        ScanResult mockScanResult = mock(ScanResult.class);
        Iterator<Record> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(false);
        when(mockScanResult.iterator()).thenReturn(mockIterator);

        ScanRequestBuilder mockScanRequestBuilder = mock(ScanRequestBuilder.class);
        when(mockScanRequestBuilder.withReadTimeoutSeconds(anyInt())).thenReturn(mockScanRequestBuilder);
        when(dfClient.scan(mockScanRequestBuilder)).thenReturn(mockScanResult);

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doReturn(mockScanRequestBuilder).when(spyRepo).getScanRequestBuilder(any(), anyString(), anyString());

        int result = spyRepo.fetchStatistics(metadata, selectField);
        assertEquals(0, result);
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testFetchStatistics_ScanException() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setCollectionName("testCollection");
        metadata.setStatName("testStat");
        String selectField = "testField";

        ScanRequestBuilder mockScanRequestBuilder = mock(ScanRequestBuilder.class);
        when(mockScanRequestBuilder.withReadTimeoutSeconds(anyInt())).thenReturn(mockScanRequestBuilder);
        when(dfClient.scan(mockScanRequestBuilder)).thenThrow(new RuntimeException("Test scan exception"));

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doReturn(mockScanRequestBuilder).when(spyRepo).getScanRequestBuilder(any(), anyString(), anyString());

        spyRepo.fetchStatistics(metadata, selectField);
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testFetchStatistics_JsonParseException() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setCollectionName("testCollection");
        metadata.setStatName("testStat");
        String selectField = "testField";

        ScanResult mockScanResult = mock(ScanResult.class);
        Iterator<Record> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(true, false);
        Record mockRecord = mock(Record.class);
        when(mockIterator.next()).thenReturn(mockRecord);
        when(mockScanResult.iterator()).thenReturn(mockIterator);

        Document mockDocument = mock(JsonDocument.class);
        when(mockRecord.getDocument()).thenReturn(mockDocument);
        when(((JsonDocument) mockDocument).getContents()).thenReturn("invalid json");

        ScanRequestBuilder mockScanRequestBuilder = mock(ScanRequestBuilder.class);
        when(mockScanRequestBuilder.withReadTimeoutSeconds(anyInt())).thenReturn(mockScanRequestBuilder);
        when(dfClient.scan(mockScanRequestBuilder)).thenReturn(mockScanResult);

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doReturn(mockScanRequestBuilder).when(spyRepo).getScanRequestBuilder(any(), anyString(), anyString());

        spyRepo.fetchStatistics(metadata, selectField);
    }

    // Tests for fetchRecordsFromDf
    @Test
    public void testFetchRecordsFromDf_Success() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setCollectionName("testCollection");
        metadata.setStatName("testStat");

        ScanResult mockScanResult = mock(ScanResult.class);
        Iterator<Record> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(true, false);
        Record mockRecord = mock(Record.class);
        when(mockIterator.next()).thenReturn(mockRecord);
        when(mockScanResult.iterator()).thenReturn(mockIterator);

        Document mockDocument = mock(JsonDocument.class);
        when(mockRecord.getDocument()).thenReturn(mockDocument);

        ScanRequestBuilder mockScanRequestBuilder = mock(ScanRequestBuilder.class);
        when(mockScanRequestBuilder.withReadTimeoutSeconds(anyInt())).thenReturn(mockScanRequestBuilder);
        when(dfClient.scan(mockScanRequestBuilder)).thenReturn(mockScanResult);

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doReturn(mockScanRequestBuilder).when(spyRepo).getScanRequestBuilder(any(), anyString(), anyString());

        List<Record> result = spyRepo.fetchRecordsFromDf(metadata);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mockRecord, result.get(0));
    }

    @Test
    public void testFetchRecordsFromDf_NoRecords() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setCollectionName("testCollection");
        metadata.setStatName("testStat");

        ScanResult mockScanResult = mock(ScanResult.class);
        Iterator<Record> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(false);
        when(mockScanResult.iterator()).thenReturn(mockIterator);

        ScanRequestBuilder mockScanRequestBuilder = mock(ScanRequestBuilder.class);
        when(mockScanRequestBuilder.withReadTimeoutSeconds(anyInt())).thenReturn(mockScanRequestBuilder);
        when(dfClient.scan(mockScanRequestBuilder)).thenReturn(mockScanResult);

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doReturn(mockScanRequestBuilder).when(spyRepo).getScanRequestBuilder(any(), anyString(), anyString());

        List<Record> result = spyRepo.fetchRecordsFromDf(metadata);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testFetchRecordsFromDf_Exception() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setCollectionName("testCollection");
        metadata.setStatName("testStat");

        ScanRequestBuilder mockScanRequestBuilder = mock(ScanRequestBuilder.class);
        when(mockScanRequestBuilder.withReadTimeoutSeconds(anyInt())).thenReturn(mockScanRequestBuilder);
        when(dfClient.scan(mockScanRequestBuilder)).thenThrow(new RuntimeException("Test scan exception"));

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        doReturn(mockScanRequestBuilder).when(spyRepo).getScanRequestBuilder(any(), anyString(), anyString());

        spyRepo.fetchRecordsFromDf(metadata);
    }

    // Tests for processFoMiData
    @Test
    public void testProcessFoMiData_EMIR_ETD() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setStatName("testStat");
        String assetClass = AssetClass.ETD.value();

        String jsonResponse = "[{\"tradingPartyLei\":\"" + LEI_NWM_PLC + "\",\"transactionReportable\":true,\"versionReportable\":true}," +
                              "{\"tradingPartyLei\":\"" + LEI_NWM_NV + "\",\"transactionReportable\":false,\"versionReportable\":false}]";
        InputStream mockInputStream = new ByteArrayInputStream(jsonResponse.getBytes());

        doAnswer(invocation -> {
            Consumer<InputStream> consumer = invocation.getArgument(2);
            consumer.accept(mockInputStream);
            return null;
        }).when(itrClient).fetch(anyString(), any(), any(Consumer.class));

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        ArgumentCaptor<FrontOfficeMIDashboardAnalytics> captor = ArgumentCaptor.forClass(FrontOfficeMIDashboardAnalytics.class);
        doNothing().when(spyRepo).upsertFoMiSnapshot(captor.capture());

        spyRepo.processFoMiData(metadata, TEST_DATE, assetClass);

        List<FrontOfficeMIDashboardAnalytics> analyticsList = captor.getAllValues();
        assertEquals(4, analyticsList.size()); // 2 for jurisdiction determined + 2 for JND

        FrontOfficeMIDashboardAnalytics plcReportable = analyticsList.stream()
                .filter(a -> a.getSubjectIdentifier().getEntity().equals(NWM_PLC) && a.getSubjectIdentifier().getRegulation().equals(EMIR))
                .findFirst().orElse(null);
        assertNotNull(plcReportable);
        assertEquals(1, plcReportable.getMiAnalyticsState().getReportableCount());
        assertEquals(0, plcReportable.getMiAnalyticsState().getNonReportableCount());

        FrontOfficeMIDashboardAnalytics nvNonReportable = analyticsList.stream()
                .filter(a -> a.getSubjectIdentifier().getEntity().equals(NWM_NV) && a.getSubjectIdentifier().getRegulation().equals(EMIR))
                .findFirst().orElse(null);
        assertNotNull(nvNonReportable);
        assertEquals(0, nvNonReportable.getMiAnalyticsState().getReportableCount());
        assertEquals(1, nvNonReportable.getMiAnalyticsState().getNonReportableCount());
    }

    @Test
    public void testProcessFoMiData_EMIR_FX() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setStatName("testStat");
        String assetClass = AssetClass.FOREIGN_EXCHANGE.value();

        String jsonResponse = "[{\"tradingPartyLei\":\"" + LEI_NWM_PLC + "\",\"transactionReportable\":true,\"versionReportable\":false}]";
        InputStream mockInputStream = new ByteArrayInputStream(jsonResponse.getBytes());

        doAnswer(invocation -> {
            Consumer<InputStream> consumer = invocation.getArgument(2);
            consumer.accept(mockInputStream);
            return null;
        }).when(itrClient).fetch(anyString(), any(Itr2Query.class), any(Consumer.class));

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        ArgumentCaptor<FrontOfficeMIDashboardAnalytics> captor = ArgumentCaptor.forClass(FrontOfficeMIDashboardAnalytics.class);
        doNothing().when(spyRepo).upsertFoMiSnapshot(captor.capture());

        spyRepo.processFoMiData(metadata, TEST_DATE, assetClass);

        List<FrontOfficeMIDashboardAnalytics> analyticsList = captor.getAllValues();
        assertEquals(4, analyticsList.size());

        FrontOfficeMIDashboardAnalytics plcNonReportable = analyticsList.stream()
                .filter(a -> a.getSubjectIdentifier().getEntity().equals(NWM_PLC) && a.getSubjectIdentifier().getRegulation().equals(EMIR))
                .findFirst().orElse(null);
        assertNotNull(plcNonReportable);
        assertEquals(0, plcNonReportable.getMiAnalyticsState().getReportableCount());
        assertEquals(1, plcNonReportable.getMiAnalyticsState().getNonReportableCount());
    }

    @Test
    public void testProcessFoMiData_MAS_IR() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setStatName("testStat");
        String assetClass = AssetClass.INTEREST_RATE.value();

        String jsonResponse = "[{\"partyLeiCode\":\"" + LEI_NWM_PLC + "\",\"isEligible\":true,\"versionReportable\":true}]";
        InputStream mockInputStream = new ByteArrayInputStream(jsonResponse.getBytes());

        doAnswer(invocation -> {
            Consumer<InputStream> consumer = invocation.getArgument(2);
            consumer.accept(mockInputStream);
            return null;
        }).when(itrClient).fetch(anyString(), any(), any(Consumer.class));

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        ArgumentCaptor<FrontOfficeMIDashboardAnalytics> captor = ArgumentCaptor.forClass(FrontOfficeMIDashboardAnalytics.class);
        doNothing().when(spyRepo).upsertFoMiSnapshot(captor.capture());

        spyRepo.processFoMiData(metadata, TEST_DATE, assetClass);

        List<FrontOfficeMIDashboardAnalytics> analyticsList = captor.getAllValues();
        assertEquals(4, analyticsList.size());

        FrontOfficeMIDashboardAnalytics plcReportable = analyticsList.stream()
                .filter(a -> a.getSubjectIdentifier().getEntity().equals(NWM_PLC) && a.getSubjectIdentifier().getRegulation().equals(MAS))
                .findFirst().orElse(null);
        assertNotNull(plcReportable);
        assertEquals(1, plcReportable.getMiAnalyticsState().getReportableCount());
    }

    @Test
    public void testProcessFoMiData_CFTC_CSA_FX() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setStatName("testStat");
        String assetClass = AssetClass.FOREIGN_EXCHANGE.value();

        String jsonResponse = "[{\"tradingPartyLei\":\"" + LEI_NWM_PLC + "\",\"cftc\":{\"transactionReportable\":true,\"versionReportable\":true}," +
                              "\"canada\":{\"transactionReportable\":false,\"versionReportable\":false}}]";
        InputStream mockInputStream = new ByteArrayInputStream(jsonResponse.getBytes());

        doAnswer(invocation -> {
            Consumer<InputStream> consumer = invocation.getArgument(2);
            consumer.accept(mockInputStream);
            return null;
        }).when(itrClient).fetch(anyString(), any(Itr2Query.class), any(Consumer.class));

        MIAnalyticsDashboardRepositoryImpl spyRepo = spy(repo);
        ArgumentCaptor<FrontOfficeMIDashboardAnalytics> captor = ArgumentCaptor.forClass(FrontOfficeMIDashboardAnalytics.class);
        doNothing().when(spyRepo).upsertFoMiSnapshot(captor.capture());

        spyRepo.processFoMiData(metadata, TEST_DATE, assetClass);

        List<FrontOfficeMIDashboardAnalytics> analyticsList = captor.getAllValues();
        assertEquals(6, analyticsList.size()); // 2 for DF, 2 for CSA, 2 for JND

        FrontOfficeMIDashboardAnalytics plcCftc = analyticsList.stream()
                .filter(a -> a.getSubjectIdentifier().getRegulation().equals(DF))
                .findFirst().orElse(null);
        assertNotNull(plcCftc);
        assertEquals(1, plcCftc.getMiAnalyticsState().getReportableCount());
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testProcessFoMiData_InvalidJurisdiction() throws Exception {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setStatName("testStat");
        String assetClass = "INVALID_ASSET";

        Map<String, List<String>> foAssetWiseMap = Maps.newHashMap();
        foAssetWiseMap.put(assetClass, Collections.singletonList("INVALID_JURISDICTION"));
        ReflectionTestUtils.setField(repo, "FO_ASSETWISE_MAP", foAssetWiseMap);

        repo.processFoMiData(metadata, TEST_DATE, assetClass);
    }

    // Utility method tests
    @Test
    public void testCalculateMiSnapshotKey() throws Exception {
        SubjectIdentifier identifier = new SubjectIdentifier("testLei", "testEntity", "testFlow", "testAsset", "testMessage", "testRegulation", TEST_DATE);
        String key = ReflectionTestUtils.invokeMethod(repo, "calculateMiSnapshotKey", identifier);
        assertNotNull(key);
        assertEquals(40, key.length());
        assertTrue(key.matches("[0-9a-f]+"));
    }

    @Test
    public void testCalculateFoMiSnapshotKey() throws Exception {
        FrontOfficeSubjectIdentifier identifier = new FrontOfficeSubjectIdentifier("testEntity", "testLei", "testAsset", "testRegulation", TEST_DATE);
        String key = ReflectionTestUtils.invokeMethod(repo, "calculateFoMiSnapshotKey", identifier);
        assertNotNull(key);
        assertEquals(40, key.length());
        assertTrue(key.matches("[0-9a-f]+"));
    }

    @Test
    public void testSerialize() throws Exception {
        MIDashboardAnalytics analytics = new MIDashboardAnalytics();
        Document result = repo.serialize(analytics);
        assertNotNull(result);
        assertTrue(result instanceof JsonDocument);
    }

    @Test
    public void testDeserialize() throws Exception {
        SubjectIdentifier identifier = new SubjectIdentifier("testLei", "testEntity", "testFlow", "testAsset", "testMessage", "testRegulation", TEST_DATE);
        MIDashboardAnalytics analytics = new MIDashboardAnalytics();
        analytics.setSubjectIdentifier(identifier);

        Document document = repo.serialize(analytics);
        MIDashboardAnalytics deserialized = repo.deserailze(document);

        assertEquals(identifier.getLei(), deserialized.getSubjectIdentifier().getLei());
    }

    @Test(expected = IOException.class)
    public void testDeserialize_InvalidJson() throws Exception {
        JsonDocument invalidDocument = new JsonDocument();
        invalidDocument.setContents("invalid json");
        repo.deserailze(invalidDocument);
    }

    @Test
    public void testGetURL() {
        String result = repo.getURL("testEndpoint");
        assertEquals("http://testService/testEndpoint", result);
    }

    @Test
    public void testItrQueryForFO_WithSelectAndWhere() {
        DFQueryMetaData metadata = new DFQueryMetaData();
        metadata.setSelect("select field");
        metadata.setWhere("where condition");

        Itr2Query result = repo.itrQueryForFO(metadata);
        assertNotNull(result);
        assertEquals("select field", result.getSelect());
        assertEquals("where condition", result.getWhere());
    }

    @Test
    public void testParseRecord() throws Exception {
        String json = "{\"key\":\"value\",\"nested\":{\"nestedKey\":\"nestedValue\"}}";
        JsonParser parser = REAL_OBJECT_MAPPER.getFactory().createParser(json);

        Map<String, Object> result = repo.parseRecord(parser);
        assertEquals("value", result.get("key"));
        assertEquals("nestedValue", result.get("nested.nestedKey"));
    }

    @Test
    public void testFlattenMap() throws Exception {
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("key1", "value1");
        Map<String, Object> nested = new HashMap<>();
        nested.put("nestedKey", "nestedValue");
        nestedMap.put("nested", nested);

        Method flattenMapMethod = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredMethod("flattenMap", Map.class);
        flattenMapMethod.setAccessible(true);
        Map<String, Object> result = (Map<String, Object>) flattenMapMethod.invoke(repo, nestedMap);

        assertEquals("value1", result.get("key1"));
        assertEquals("nestedValue", result.get("nested.nestedKey"));
    }

    @Test
    public void testRenderCftcDataBlock() throws Exception {
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("cftc", "{transactionReportable=true, versionReportable=false}");

        Method method = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredMethod("renderCftcDataBlock", Map.class, String.class);
        method.setAccessible(true);
        Map<String, Object> result = (Map<String, Object>) method.invoke(repo, jsonMap, "cftc");

        assertEquals("true", result.get("transactionReportable"));
        assertEquals("false", result.get("versionReportable"));
    }

    // Additional parseItrRecord tests for coverage
    @Test
    public void testParseItrRecord_BOI() throws Exception {
        String json = "{\"tradingPartyLei\":\"" + LEI_NWM_PLC + "\",\"transactionReportable\":true,\"versionReportable\":true}";
        JsonParser parser = REAL_OBJECT_MAPPER.getFactory().createParser(json);

        Map<String, Map<String, Object>> result = repo.parseItrRecord(parser, AssetClass.ETD.value(), BOI);
        assertEquals(LEI_NWM_PLC, result.get(BOI).get(TRADING_PARTY_LEI));
        assertEquals(true, result.get(BOI).get(TRANSACTION_REPORTABLE));
    }

    @Test
    public void testParseItrRecord_MIFID_ETD() throws Exception {
        String json = "{\"tradingCapacity\":\"Dealing On Own Account\",\"reportingPartyBookId\":\"RANL01\",\"transactionReportable\":true,\"versionReportable\":true}";
        JsonParser parser = REAL_OBJECT_MAPPER.getFactory().createParser(json);

        Map<String, Map<String, Object>> result = repo.parseItrRecord(parser, AssetClass.ETD.value(), MIFID);
        assertEquals(LEI_NWM_PLC, result.get(MIFID).get(TRADING_PARTY_LEI));
    }

    @Test
    public void testParseItrRecord_SFTR() throws Exception {
        String json = "{\"tradingPartyLei\":\"" + LEI_NWM_NV + "\",\"transactionReportable\":false,\"versionReportable\":false}";
        JsonParser parser = REAL_OBJECT_MAPPER.getFactory().createParser(json);

        Map<String, Map<String, Object>> result = repo.parseItrRecord(parser, AssetClass.ETD.value(), SFTR);
        assertEquals(LEI_NWM_NV, result.get(SFTR).get(TRADING_PARTY_LEI));
    }
}