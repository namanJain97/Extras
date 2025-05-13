package com.rbs.tntr.business.taggingService.repository;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.nwm.tntr.commons.domain.persistence.constant.AssetClass;
import com.nwm.tntr.commons.repository.df.DfConnectionManager;
import com.nwm.tntr.configuration.ItrConfiguration;
import com.nwm.tntr.itr.IQuery;
import com.nwm.tntr.itr.Itr2ParameterisedQuery;
import com.rbs.datafabric.agile.commons.lang.StartableException;
import com.rbs.datafabric.api.ScanResult;
import com.rbs.datafabric.api.exception.ScanException;
import com.rbs.datafabric.api.exception.UpsertException;
import com.rbs.datafabric.client.DataFabricClient;
import com.rbs.datafabric.domain.Document;
import com.rbs.datafabric.domain.JsonDocument;
import com.rbs.datafabric.domain.Record;
import com.rbs.datafabric.domain.RecordId;
import com.rbs.datafabric.domain.client.builder.ScanRequestBuilder;
import com.rbs.datafabric.domain.client.builder.UpsertRequestBuilder;
import com.rbs.tntr.business.taggingService.service.common.DateTimeService;
import com.rbs.tntr.business.taggingService.service.common.ItrClient;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.common.Itr2Query;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.DFQueryMetaData;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.FrontOfficeMIDashboardAnalytics;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.FrontOfficeSubjectIdentifier;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.MIDashboardAnalytics;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.SubjectIdentifier;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.MIConstants.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MIAnalyticsDashboardRepositoryImpl.class, Hashing.class, DateTimeService.class, ObjectMapper.class})
public class MIAnalyticsDashboardRepositoryImplTest {

    @Mock
    private DfConnectionManager dfConnectionManager;

    @Mock
    private ItrConfiguration itrConfiguration;

    @Mock
    private ItrClient itrClient;

    @Mock
    private DataFabricClient dfClient;

    @Mock
    private Logger logger;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ScanRequestBuilder scanRequestBuilder;

    @Mock
    private UpsertRequestBuilder upsertRequestBuilder;

    @Mock
    private ScanResult scanResult;

    @Mock
    private Record record;

    @Mock
    private RecordId recordId;

    @Mock
    private JsonDocument jsonDocument;

    @Mock
    private JsonNode jsonNode;

    @Mock
    private JsonParser jsonParser;

    @Mock
    private InputStream inputStream;

    @Mock
    private MIDashboardAnalytics miDashboardAnalytics;

    @Mock
    private FrontOfficeMIDashboardAnalytics foMiDashboardAnalytics;

    @Mock
    private SubjectIdentifier subjectIdentifier;

    @Mock
    private FrontOfficeSubjectIdentifier foSubjectIdentifier;

    @Mock
    private DFQueryMetaData dfQueryMetaData;

    private MIAnalyticsDashboardRepositoryImpl repository;

    private final String databaseName = "tntr_db";
    private final int readTimeOut = 30;
    private final String miCollection = "mi_analytics";
    private final String foMiCollection = "fo_mi_analytics";
    private final String foEmirUrl = "emir_endpoint";
    private final String foEmirParameterisedUrl = "emir_param_endpoint";
    private final String foMasUrl = "mas_endpoint";
    private final String foMasParameterisedUrl = "mas_param_endpoint";
    private final String foBoiUrl = "boi_endpoint";
    private final String foMifidUrl = "mifid_endpoint";
    private final String foSftrUrl = "sftr_endpoint";
    private final String foSftrAldopUrl = "sftr_aldop_endpoint";
    private final String foCftcUrl = "cftc_endpoint";
    private final Date businessDate = new Date();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        repository = new MIAnalyticsDashboardRepositoryImpl(dfConnectionManager, itrConfiguration, itrClient);

        // Set private fields via reflection
        setField("databaseName", databaseName);
        setField("readTimeOut", readTimeOut);
        setField("miCollection", miCollection);
        setField("foMiCollection", foMiCollection);
        setField("foEmirUrl", foEmirUrl);
        setField("foEmirParameterisedUrl", foEmirParameterisedUrl);
        setField("foMasUrl", foMasUrl);
        setField("foMasParameterisedUrl", foMasParameterisedUrl);
        setField("foBoiUrl", foBoiUrl);
        setField("foMifidUrl", foMifidUrl);
        setField("foSftrUrl", foSftrUrl);
        setField("foSftrAldopUrl", foSftrAldopUrl);
        setField("foCftcUrl", foCftcUrl);

        // Mock static fields
        PowerMockito.mockStatic(MIAnalyticsDashboardRepositoryImpl.class);
        Field loggerField = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredField("LOGGER");
        loggerField.setAccessible(true);
        loggerField.set(null, logger);

        Field mapperField = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredField("objectMapper");
        mapperField.setAccessible(true);
        mapperField.set(null, objectMapper);

        // Mock inherited methods
        when(repository.getDfClient(dfConnectionManager)).thenReturn(dfClient);
        when(repository.getScanRequestBuilder(any(), anyString(), anyString())).thenReturn(scanRequestBuilder);

        // Mock common behavior
        when(subjectIdentifier.getBusinessDate()).thenReturn(businessDate);
        when(subjectIdentifier.getLei()).thenReturn("LEI123");
        when(subjectIdentifier.getAssetClass()).thenReturn("FX");
        when(subjectIdentifier.getEntity()).thenReturn("NWM");
        when(subjectIdentifier.getFlow()).thenReturn("FLOW");
        when(subjectIdentifier.getRegulation()).thenReturn("EMIR");
        when(subjectIdentifier.getMessageType()).thenReturn("REPORT");
        when(foSubjectIdentifier.getBusinessDate()).thenReturn(businessDate);
        when(foSubjectIdentifier.getAssetClass()).thenReturn("FX");
        when(foSubjectIdentifier.getEntity()).thenReturn("NWM");
        when(foSubjectIdentifier.getLei()).thenReturn("LEI123");
        when(foSubjectIdentifier.getRegulation()).thenReturn("EMIR");
        when(miDashboardAnalytics.getSubjectIdentifier()).thenReturn(subjectIdentifier);
        when(foMiDashboardAnalytics.getSubjectIdentifier()).thenReturn(foSubjectIdentifier);
        when(dfQueryMetaData.getCollectionName()).thenReturn(miCollection);
        when(dfQueryMetaData.getStatName()).thenReturn("statName");
        when(dfQueryMetaData.getSelect()).thenReturn("select *");
        when(dfQueryMetaData.getWhere()).thenReturn("where clause");
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(repository, value);
    }

    @Test
    public void testConstructor() {
        assertEquals(dfConnectionManager, repository.dfConnectionManager);
        assertEquals(itrConfiguration, repository.itrConfiguration);
        assertEquals(itrClient, repository.itrClient);
    }

    @Test
    public void testCreateDfClientConnection_Success() throws StartableException {
        repository.createDfClientConnection();
        verify(logger).info("DF Client connection created");
    }

    @Test
    public void testCreateDfClientConnection_Exception() throws StartableException {
        StartableException exception = new StartableException("Connection failed");
        when(repository.getDfClient(dfConnectionManager)).thenThrow(exception);
        repository.createDfClientConnection();
        verify(logger).error("Error while creating df client connection", exception);
    }

    @Test
    public void testSerialize_Success() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(miDashboardAnalytics)).thenReturn("{\"data\":\"test\"}");
        Document result = repository.serialize(miDashboardAnalytics);
        assertTrue(result instanceof JsonDocument);
        assertEquals("{\"data\":\"test\"}", ((JsonDocument) result).getContents());
    }

    @Test
    public void testDeserialize_Success() throws IOException {
        when(jsonDocument.getContents()).thenReturn("{\"data\":\"test\"}");
        when(objectMapper.readValue("{\"data\":\"test\"}", MIDashboardAnalytics.class)).thenReturn(miDashboardAnalytics);
        MIDashboardAnalytics result = repository.deserailze(jsonDocument);
        assertEquals(miDashboardAnalytics, result);
    }

    @Test
    public void testUpsertMiSnapshot_Success() throws JsonProcessingException, UpsertException {
        when(objectMapper.writeValueAsString(miDashboardAnalytics)).thenReturn("{\"data\":\"test\"}");
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("hashedKey");
        when(upsertRequestBuilder.withDocument(any(Document.class))).thenReturn(upsertRequestBuilder);
        when(upsertRequestBuilder.withKey("hashedKey")).thenReturn(upsertRequestBuilder);
        when(dfClient.upsert(upsertRequestBuilder)).thenReturn(recordId);

        RecordId result = repository.upsertMiSnapshot(miDashboardAnalytics);
        assertEquals(recordId, result);
        verify(logger).info("{\"data\":\"test\"}");
        verify(logger).info("Generated Key is : {}", "hashedKey");
    }

    @Test
    public void testUpsertMiSnapshot_JsonProcessingException() throws JsonProcessingException {
        JsonProcessingException exception = new JsonProcessingException("Serialization error") {};
        when(objectMapper.writeValueAsString(miDashboardAnalytics)).thenThrow(exception);
        try {
            repository.upsertMiSnapshot(miDashboardAnalytics);
            fail("Expected TaggingServiceRunTimeException");
        } catch (Exception e) {
            assertTrue(e instanceof com.rbs.tntr.domain.taggingService.jiraTaggingDomain.exceptions.TaggingServiceRunTimeException);
            assertEquals("Error while inserting export scan into DF", e.getMessage());
            verify(logger).error("Error while inserting export scan into DF", exception);
        }
    }

    @Test
    public void testUpsertFoMiSnapshot_Success() throws JsonProcessingException, UpsertException {
        when(objectMapper.writeValueAsString(foMiDashboardAnalytics)).thenReturn("{\"data\":\"fo_test\"}");
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("foHashedKey");
        when(upsertRequestBuilder.withDocument(any(Document.class))).thenReturn(upsertRequestBuilder);
        when(upsertRequestBuilder.withKey("foHashedKey")).thenReturn(upsertRequestBuilder);
        when(dfClient.upsert(upsertRequestBuilder)).thenReturn(recordId);

        RecordId result = repository.upsertFoMiSnapshot(foMiDashboardAnalytics);
        assertEquals(recordId, result);
        verify(logger).info("Generated FO Key is : {}", "foHashedKey");
        verify(logger).info("FO Stat to be persisted with data : {}", "{\"data\":\"fo_test\"}");
    }

    @Test
    public void testFetchStatistics_Success() throws ScanException, IOException {
        when(scanRequestBuilder.withReadTimeoutSeconds(readTimeOut)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(scanResult);
        when(scanResult.iterator()).thenReturn(Collections.singletonList(record).iterator());
        when(record.getDocument()).thenReturn(jsonDocument);
        when(jsonDocument.getContents()).thenReturn("{\"count\":42}");
        when(objectMapper.readTree("{\"count\":42}")).thenReturn(jsonNode);
        when(jsonNode.get("count")).thenReturn(jsonNode);
        when(jsonNode.asInt()).thenReturn(42);

        int result = repository.fetchStatistics(dfQueryMetaData, "count");
        assertEquals(42, result);
    }

    @Test
    public void testFetchStatistics_NoRecords() throws ScanException {
        when(scanRequestBuilder.withReadTimeoutSeconds(readTimeOut)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(null);
        int result = repository.fetchStatistics(dfQueryMetaData, "count");
        assertEquals(0, result);
    }

    @Test
    public void testFetchRecordsFromDf_Success() throws ScanException {
        when(scanRequestBuilder.withReadTimeoutSeconds(readTimeOut)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(scanResult);
        when(scanResult.iterator()).thenReturn(Collections.singletonList(record).iterator());
        when(record.getDocument()).thenReturn(jsonDocument);

        List<Record> result = repository.fetchRecordsFromDf(dfQueryMetaData);
        assertEquals(1, result.size());
        assertEquals(record, result.get(0));
    }

    @Test
    public void testCalculateMiSnapshotKey() {
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("hashedKey");

        String result = invokePrivateMethod("calculateMiSnapshotKey", subjectIdentifier);
        assertEquals("hashedKey", result);
    }

    @Test
    public void testCalculateFoMiSnapshotKey() {
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("foHashedKey");

        String result = invokePrivateMethod("calculateFoMiSnapshotKey", foSubjectIdentifier);
        assertEquals("foHashedKey", result);
    }

    @Test
    public void testProcessFoMiData_Emir_ETD() {
        String assetClass = AssetClass.ETD.value();
        Map<String, Map<String, Integer>> baseDataMap = Collections.singletonMap(EMIR, generateFoMap());
        PowerMockito.mockStatic(System.class);
        when(System.currentTimeMillis()).thenReturn(1000L, 2000L);
        when(FO_ASSETWISE_MAP.get(assetClass)).thenReturn(Collections.singletonList(EMIR));
        doNothing().when(repository).fetchFromItr(eq(dfQueryMetaData), eq(baseDataMap), eq(foEmirParameterisedUrl), eq(EMIR), eq(businessDate), eq(assetClass));
        doNothing().when(repository).persistFoJurisdictionDetermined(eq(assetClass), eq(EMIR), eq(businessDate), anyMap());
        doNothing().when(repository).persistFoJurisdictionNotDetermined(eq(assetClass), eq(businessDate), eq(0), eq(0));

        repository.processFoMiData(dfQueryMetaData, businessDate, assetClass);
        verify(logger).info("Processing FO MI For AssetClass : {} & Jurisdiction : {}", assetClass, EMIR);
        verify(logger).info("Time taken For AssetClass : {} & Jurisdiction : {} calculation is : {} seconds", assetClass, EMIR, 1L);
    }

    @Test
    public void testProcessFoMiData_UnknownJurisdiction() {
        String assetClass = "Unknown";
        when(FO_ASSETWISE_MAP.get(assetClass)).thenReturn(Collections.singletonList("UnknownJurisdiction"));
        try {
            repository.processFoMiData(dfQueryMetaData, businessDate, assetClass);
            fail("Expected TaggingServiceRunTimeException");
        } catch (Exception e) {
            assertTrue(e instanceof com.rbs.tntr.domain.taggingService.jiraTaggingDomain.exceptions.TaggingServiceRunTimeException);
            assertEquals("No Fo Setup Found for assetClass: Unknown and jurisdiction : UnknownJurisdiction", e.getMessage());
        }
    }

    @Test
    public void testFetchFromItr_ParamQuery() {
        Map<String, Map<String, Integer>> foMiDataMap = new HashMap<>();
        doNothing().when(repository).fetchFromItrWithParamQuery(eq(dfQueryMetaData), eq(foMiDataMap), eq("itrUrl"), eq(BOI), eq(businessDate), eq("FX"));
        Map<String, Map<String, Integer>> result = invokePrivateMethod("fetchFromItr", dfQueryMetaData, foMiDataMap, "itrUrl", BOI, businessDate, "FX");
        assertEquals(foMiDataMap, result);
    }

    @Test
    public void testFetchFromItr_NonParamQuery() {
        Map<String, Map<String, Integer>> foMiDataMap = new HashMap<>();
        doNothing().when(repository).fetchFromItrWithoutParamQuery(eq(dfQueryMetaData), eq(foMiDataMap), eq("itrUrl"), eq(EMIR));
        Map<String, Map<String, Integer>> result = invokePrivateMethod("fetchFromItr", dfQueryMetaData, foMiDataMap, "itrUrl", EMIR, businessDate, "FX");
        assertEquals(foMiDataMap, result);
    }

    @Test
    public void testFetchFromItrWithParamQuery_BOI() {
        Map<String, Map<String, Integer>> foMiDataMap = new HashMap<>();
        List<String> queryIds = Collections.singletonList("queryId1");
        when(BOI_ASSETWISE_QUERY_IDS.get("FX")).thenReturn(queryIds);
        IQuery iQuery = mock(IQuery.class);
        when(repository.itrParameterizedQueryForFo(businessDate, "queryId1", BOI_PARAM_QUERY_SELECT)).thenReturn(iQuery);
        when(repository.getURL("itrUrl")).thenReturn("https://itr-service/itrUrl");
        doNothing().when(repository).fetchData(eq(iQuery), eq(foMiDataMap), eq("https://itr-service/itrUrl"), eq(BOI), eq(dfQueryMetaData));

        invokePrivateMethod("fetchFromItrWithParamQuery", dfQueryMetaData, foMiDataMap, "itrUrl", BOI, businessDate, "FX");
        verify(repository).fetchData(iQuery, foMiDataMap, "https://itr-service/itrUrl", BOI, dfQueryMetaData);
    }

    @Test
    public void testFetchData_Success() throws IOException {
        IQuery iQuery = mock(IQuery.class);
        Map<String, Map<String, Integer>> foMiDataMap = new HashMap<>();
        when(objectMapper.getFactory().createParser(inputStream)).thenReturn(jsonParser);
        when(jsonParser.nextToken()).thenReturn(JsonToken.START_ARRAY, JsonToken.START_OBJECT, JsonToken.END_OBJECT, JsonToken.END_ARRAY);
        Map<String, Map<String, Object>> parsedRecord = new HashMap<>();
        when(repository.parseItrRecord(jsonParser, "statName", "BOI")).thenReturn(parsedRecord);
        doNothing().when(repository).processFoDataMap(eq(parsedRecord), eq(foMiDataMap));

        invokePrivateMethod("fetchData", iQuery, foMiDataMap, "url", "BOI", dfQueryMetaData);
        verify(itrClient).fetch(eq("url"), eq(iQuery), any());
    }

    @Test
    public void testItrParameterizedQueryForFo() {
        Map<String, String> params = new HashMap<>();
        when(repository.populateFoLifeTimeParams(businessDate)).thenReturn(params);
        IQuery result = invokePrivateMethod("itrParameterizedQueryForFo", businessDate, "queryId", "select *");
        assertTrue(result instanceof Itr2ParameterisedQuery);
    }

    @Test
    public void testPopulateFoLifeTimeParams() {
        DateTime currentDate = new DateTime(businessDate).withZone(DateTimeZone.UTC);
        PowerMockito.mockStatic(DateTimeService.class);
        when(DateTimeService.getCurrentStartDateTime(currentDate)).thenReturn(currentDate);
        when(DateTimeService.getCurrentEndDateTime(currentDate)).thenReturn(currentDate.plusDays(1));
        when(DateTimeService.asString(currentDate)).thenReturn("2023-01-01");
        when(DateTimeService.asString(currentDate.plusDays(1))).thenReturn("2023-01-02");

        Map<String, String> result = invokePrivateMethod("populateFoLifeTimeParams", businessDate);
        assertEquals("2023-01-01", result.get(DATETIME_FROM));
        assertEquals("2023-01-02", result.get(DATETIME_TO));
    }

    @Test
    public void testFetchFromItrWithoutParamQuery() throws IOException {
        Map<String, Map<String, Integer>> foMiDataMap = new HashMap<>();
        Itr2Query itr2Query = mock(Itr2Query.class);
        when(repository.itrQueryForFO(dfQueryMetaData)).thenReturn(itr2Query);
        when(repository.getURL("itrUrl")).thenReturn("https://itr-service/itrUrl");
        when(objectMapper.getFactory().createParser(inputStream)).thenReturn(jsonParser);
        when(jsonParser.nextToken()).thenReturn(JsonToken.START_ARRAY, JsonToken.END_ARRAY);

        invokePrivateMethod("fetchFromItrWithoutParamQuery", dfQueryMetaData, foMiDataMap, "itrUrl", "EMIR");
        verify(itrClient).fetch(eq("https://itr-service/itrUrl"), eq(itr2Query), any());
    }

    @Test
    public void testProcessFoDataMap() {
        Map<String, Object> parsedItrRecord = new HashMap<>();
        parsedItrRecord.put(TRADING_PARTY_LEI, LEI_NWM_PLC);
        parsedItrRecord.put(TRANSACTION_REPORTABLE, true);
        parsedItrRecord.put(VERSION_REPORTABLE, true);
        Map<String, Map<String, Object>> parsedRecord = Collections.singletonMap(EMIR, parsedItrRecord);
        Map<String, Integer> jurisdictionData = invokePrivateMethod("generateFoMap");
        Map<String, Map<String, Integer>> foMiData = Collections.singletonMap(EMIR, jurisdictionData);

        invokePrivateMethod("processFoDataMap", parsedRecord, foMiData);
        assertEquals(1, foMiData.get(EMIR).get("NWM_PLC_REPORTABLE").intValue());
    }

    @Test
    public void testGetURL() {
        when(itrConfiguration.getItr2ProtocolScheme()).thenReturn("https");
        when(itrConfiguration.getItr2ServiceName()).thenReturn("itr-service");
        String result = repository.getURL("endpoint");
        assertEquals("https://itr-service/endpoint", result);
    }

    @Test
    public void testItrQueryForFO() {
        Itr2Query result = repository.itrQueryForFO(dfQueryMetaData);
        assertTrue(result instanceof Itr2Query);
    }

    @Test
    public void testParseItrRecord_BOI() throws IOException {
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(TRADING_PARTY_LEI, "LEI123");
        jsonMap.put(VERSION_REPORTABLE, true);
        jsonMap.put(TRANSACTION_REPORTABLE, false);
        when(repository.parseRecord(jsonParser)).thenReturn(jsonMap);

        Map<String, Map<String, Object>> result = invokePrivateMethod("parseItrRecord", jsonParser, "FX", BOI);
        assertEquals("LEI123", result.get(BOI).get(TRADING_PARTY_LEI));
    }

    @Test
    public void testFetchItrData() {
        Map<String, Object> itrData = new HashMap<>();
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("lei", "LEI123");
        jsonMap.put("version_reportable", true);
        jsonMap.put("transaction_reportable", false);
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put(TRADING_PARTY_LEI, "lei");
        fieldMap.put(VERSION_REPORTABLE, "version_reportable");
        fieldMap.put(TRANSACTION_REPORTABLE, "transaction_reportable");

        invokePrivateMethod("fetchItrData", itrData, jsonMap, fieldMap);
        assertEquals("LEI123", itrData.get(TRADING_PARTY_LEI));
    }

    @Test
    public void testRenderCftcDataBlock() {
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("cftc", "{version_reportable=true, transaction_reportable=false}");
        Map<String, Object> result = invokePrivateMethod("renderCftcDataBlock", jsonMap, "cftc");
        assertEquals("true", result.get("version_reportable"));
        assertEquals("false", result.get("transaction_reportable"));
    }

    @Test
    public void testParseRecord() throws IOException {
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("key", "value");
        when(objectMapper.readValue(jsonParser, Map.class)).thenReturn(jsonMap);

        Map<String, Object> result = invokePrivateMethod("parseRecord", jsonParser);
        assertEquals("value", result.get("key"));
    }

    @Test
    public void testFlattenMap() {
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("key1", "value1");
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("key2", "value2");
        jsonMap.put("nested", nestedMap);

        Map<String, Object> result = invokePrivateMethod("flattenMap", jsonMap);
        assertEquals("value1", result.get("key1"));
        assertEquals("value2", result.get("nested.key2"));
    }

    @Test
    public void testPersistFoJurisdictionDetermined() {
        Map<String, Integer> itrMiData = invokePrivateMethod("generateFoMap");
        itrMiData.put(PLC_REPORTABLE, 10);
        itrMiData.put(PLC_NONREPORTABLE, 20);
        itrMiData.put(NV_REPORTABLE, 30);
        itrMiData.put(NV_NONREPORTABLE, 40);
        doNothing().when(repository).persistFoMiData(anyString(), anyString(), anyString(), anyString(), any(Date.class), anyInt(), anyInt());

        invokePrivateMethod("persistFoJurisdictionDetermined", "FX", EMIR, businessDate, itrMiData);
        verify(repository).persistFoMiData(NWM_PLC, LEI_NWM_PLC, "FX", EMIR, businessDate, 10, 20);
        verify(repository).persistFoMiData(NWM_NV, LEI_NWM_NV, "FX", EMIR, businessDate, 30, 40);
    }

    @Test
    public void testPersistFoJurisdictionNotDetermined() {
        doNothing().when(repository).persistFoMiData(anyString(), anyString(), anyString(), anyString(), any(Date.class), anyInt(), anyInt());
        invokePrivateMethod("persistFoJurisdictionNotDetermined", "FX", businessDate, 5, 10);
        verify(repository).persistFoMiData(NWM_PLC, LEI_NWM_PLC, "FX", JND, businessDate, 0, 5);
        verify(repository).persistFoMiData(NWM_NV, LEI_NWM_NV, "FX", JND, businessDate, 0, 10);
    }

    @Test
    public void testGenerateFoMap() {
        Map<String, Integer> result = invokePrivateMethod("generateFoMap");
        assertEquals(0, result.get(PLC_REPORTABLE).intValue());
        assertEquals(0, result.get(PLC_NONREPORTABLE).intValue());
        assertEquals(0, result.get(PLC_NOTFOUND).intValue());
        assertEquals(0, result.get(NV_REPORTABLE).intValue());
        assertEquals(0, result.get(NV_NONREPORTABLE).intValue());
        assertEquals(0, result.get(NV_NOTFOUND).intValue());
    }

    @Test
    public void testPersistFoMiData() throws JsonProcessingException, UpsertException {
        when(objectMapper.writeValueAsString(any(FrontOfficeMIDashboardAnalytics.class))).thenReturn("{\"data\":\"fo_test\"}");
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("foHashedKey");
        when(upsertRequestBuilder.withDocument(any(Document.class))).thenReturn(upsertRequestBuilder);
        when(upsertRequestBuilder.withKey("foHashedKey")).thenReturn(upsertRequestBuilder);
        when(dfClient.upsert(upsertRequestBuilder)).thenReturn(recordId);

        invokePrivateMethod("persistFoMiData", NWM_PLC, LEI_NWM_PLC, "FX", EMIR, businessDate, 10, 20);
        verify(dfClient).upsert(any(UpsertRequestBuilder.class));
    }

    private <T> T invokePrivateMethod(String methodName, Object... args) {
        try {
            PowerMockito.spy(repository);
            return (T) PowerMockito.method(MIAnalyticsDashboardRepositoryImpl.class, methodName).withArguments(args).invoke(repository, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke private method: " + methodName, e);
        }
    }

    private Map<String, Integer> generateFoMap() {
        Map<String, Integer> foDataMap = new HashMap<>();
        foDataMap.put(PLC_REPORTABLE, 0);
        foDataMap.put(PLC_NONREPORTABLE, 0);
        foDataMap.put(PLC_NOTFOUND, 0);
        foDataMap.put(NV_REPORTABLE, 0);
        foDataMap.put(NV_NONREPORTABLE, 0);
        foDataMap.put(NV_NOTFOUND, 0);
        return foDataMap;
    }
}