```java
package com.rbs.tntr.business.taggingService.repository;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nwm.tntr.commons.domain.persistence.constant.AssetClass;
import com.nwm.tntr.commons.repository.df.DfConnectionManager;
import com.nwm.tntr.configuration.ItrConfiguration;
import com.nwm.tntr.itr.IQuery;
import com.nwm.tntr.itr.Itr2ParameterisedQuery;
import com.rbs.datafabric.agile.commons.lang.StartableException;
import com.rbs.datafabric.api.ScanResult;
import com.rbs.datafabric.api.exception.OptimisticLockException;
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
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.exceptions.TaggingServiceRunTimeException;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.DFQueryMetaData;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.FrontOfficeMIDashboardAnalytics;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.FrontOfficeMIAalyticsState;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.FrontOfficeSubjectIdentifier;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.MIDashboardAnalytics;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.SubjectIdentifier;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.MIConstants.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DateTimeService.class, MIAnalyticsDashboardRepositoryImpl.class})
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
    private ObjectMapper objectMapper;

    @Mock
    private ScanExpressionUtility scanExpressionUtility;

    @InjectMocks
    private MIAnalyticsDashboardRepositoryImpl repository;

    private static final String DATABASE_NAME = "tntr_db";
    private static final String MI_COLLECTION = "mi_analytics";
    private static final String FO_MI_COLLECTION = "fo_mi_analytics";
    private static final int READ_TIMEOUT = 30;

    @Before
    public void setUp() throws Exception {
        // Set @Value fields via reflection
        setField("databaseName", DATABASE_NAME);
        setField("miCollection", MI_COLLECTION);
        setField("foMiCollection", FO_MI_COLLECTION);
        setField("readTimeOut", READ_TIMEOUT);
        setField("foEmirUrl", "emir_endpoint");
        setField("foEmirParameterisedUrl", "emir_param_endpoint");
        setField("foMasUrl", "mas_endpoint");
        setField("foMasParameterisedUrl", "mas_param_endpoint");
        setField("foBoiUrl", "boi_endpoint");
        setField("foMifidUrl", "mifid_endpoint");
        setField("foSftrUrl", "sftr_endpoint");
        setField("foSftrAldopUrl", "sftr_aldop_endpoint");
        setField("foCftcUrl", "cftc_endpoint");

        // Mock ScanExpressionUtility methods
        when(scanExpressionUtility.getDfClient(dfConnectionManager)).thenReturn(dfClient);
        repository = spy(new MIAnalyticsDashboardRepositoryImpl(dfConnectionManager, itrConfiguration, itrClient));
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(repository, value);
    }

    private Method getPrivateMethod(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    @Test
    public void testCreateDfClientConnection_Success() throws StartableException {
        // Execute
        repository.createDfClientConnection();

        // Verify
        verify(dfConnectionManager).getDfClient();
        verifyNoMoreInteractions(dfClient);
    }

    @Test
    public void testCreateDfClientConnection_StartableException() throws StartableException {
        // Prepare
        StartableException exception = new StartableException("Connection error");
        when(scanExpressionUtility.getDfClient(dfConnectionManager)).thenThrow(exception);

        // Execute
        repository.createDfClientConnection();

        // Verify
        verify(dfConnectionManager).getDfClient();
        verifyNoMoreInteractions(dfClient);
    }

    @Test
    public void testUpsertMiSnapshot_Success() throws JsonProcessingException {
        // Prepare
        MIDashboardAnalytics stats = new MIDashboardAnalytics();
        SubjectIdentifier subjectIdentifier = new SubjectIdentifier();
        subjectIdentifier.setBusinessDate(new Date());
        subjectIdentifier.setLei("LEI123");
        subjectIdentifier.setAssetClass("FX");
        subjectIdentifier.setEntity("NWM");
        subjectIdentifier.setFlow("Trade");
        subjectIdentifier.setRegulation("EMIR");
        subjectIdentifier.setMessageType("New");
        stats.setSubjectIdentifier(subjectIdentifier);

        JsonDocument document = new JsonDocument();
        document.withContents("{\"key\":\"value\"}");
        RecordId recordId = new RecordId("record123");

        when(objectMapper.writeValueAsString(stats)).thenReturn("{\"key\":\"value\"}");
        when(dfClient.upsert(any(UpsertRequestBuilder.class))).thenReturn(recordId);

        // Execute
        RecordId result = repository.upsertMiSnapshot(stats);

        // Verify
        assertEquals(recordId, result);
        ArgumentCaptor<UpsertRequestBuilder> captor = ArgumentCaptor.forClass(UpsertRequestBuilder.class);
        verify(dfClient).upsert(captor.capture());
        UpsertRequestBuilder builder = captor.getValue();
        assertEquals(DATABASE_NAME, builder.getDatabaseName());
        assertEquals(MI_COLLECTION, builder.getCollectionName());
        assertNotNull(builder.getDocument());
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testUpsertMiSnapshot_JsonProcessingException() throws JsonProcessingException {
        // Prepare
        MIDashboardAnalytics stats = new MIDashboardAnalytics();
        when(objectMapper.writeValueAsString(stats)).thenThrow(new JsonProcessingException("Serialization error") {});

        // Execute
        repository.upsertMiSnapshot(stats);
    }

    @Test
    public void testUpsertFoMiSnapshot_Success() throws JsonProcessingException {
        // Prepare
        FrontOfficeMIDashboardAnalytics stats = new FrontOfficeMIDashboardAnalytics();
        FrontOfficeSubjectIdentifier subjectIdentifier = new FrontOfficeSubjectIdentifier("NWM", "LEI123", "FX", "EMIR", new Date());
        stats.setSubjectIdentifier(subjectIdentifier);

        JsonDocument document = new JsonDocument();
        document.withContents("{\"key\":\"value\"}");
        RecordId recordId = new RecordId("record123");

        when(objectMapper.writeValueAsString(stats)).thenReturn("{\"key\":\"value\"}");
        when(dfClient.upsert(any(UpsertRequestBuilder.class))).thenReturn(recordId);

        // Execute
        RecordId result = repository.upsertFoMiSnapshot(stats);

        // Verify
        assertEquals(recordId, result);
        ArgumentCaptor<UpsertRequestBuilder> captor = ArgumentCaptor.forClass(UpsertRequestBuilder.class);
        verify(dfClient).upsert(captor.capture());
        UpsertRequestBuilder builder = captor.getValue();
        assertEquals(DATABASE_NAME, builder.getDatabaseName());
        assertEquals(FO_MI_COLLECTION, builder.getCollectionName());
        assertNotNull(builder.getDocument());
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testUpsertFoMiSnapshot_UpsertException() throws JsonProcessingException {
        // Prepare
        FrontOfficeMIDashboardAnalytics stats = new FrontOfficeMIDashboardAnalytics();
        when(objectMapper.writeValueAsString(stats)).thenReturn("{\"key\":\"value\"}");
        when(dfClient.upsert(any(UpsertRequestBuilder.class))).thenThrow(new UpsertException("Upsert error"));

        // Execute
        repository.upsertFoMiSnapshot(stats);
    }

    @Test
    public void testFetchStatistics_Success() throws IOException, ScanException {
        // Prepare
        DFQueryMetaData dfScanParameters = new DFQueryMetaData();
        dfScanParameters.setCollectionName(MI_COLLECTION);
        String selectField = "count";
        ScanRequestBuilder scanRequestBuilder = mock(ScanRequestBuilder.class);
        ScanResult scanResult = mock(ScanResult.class);
        Iterator<Record> iterator = mock(Iterator.class);
        Record record = mock(Record.class);
        JsonDocument document = new JsonDocument();
        document.withContents("{\"count\":42}");
        JsonNode jsonNode = mock(JsonNode.class);

        when(scanExpressionUtility.getScanRequestBuilder(dfScanParameters, DATABASE_NAME, MI_COLLECTION))
                .thenReturn(scanRequestBuilder);
        when(scanRequestBuilder.withReadTimeoutSeconds(READ_TIMEOUT)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(scanResult);
        when(scanResult.iterator()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.next()).thenReturn(record);
        when(record.getDocument()).thenReturn(document);
        when(objectMapper.readTree("{\"count\":42}")).thenReturn(jsonNode);
        when(jsonNode.get(selectField)).thenReturn(mock(JsonNode.class));
        when(jsonNode.get(selectField).asInt()).thenReturn(42);

        // Execute
        int result = repository.fetchStatistics(dfScanParameters, selectField);

        // Verify
        assertEquals(42, result);
        verify(dfClient).scan(scanRequestBuilder);
    }

    @Test
    public void testFetchStatistics_EmptyResult() throws ScanException {
        // Prepare
        DFQueryMetaData dfScanParameters = new DFQueryMetaData();
        dfScanParameters.setCollectionName(MI_COLLECTION);
        String selectField = "count";
        ScanRequestBuilder scanRequestBuilder = mock(ScanRequestBuilder.class);
        ScanResult scanResult = mock(ScanResult.class);

        when(scanExpressionUtility.getScanRequestBuilder(dfScanParameters, DATABASE_NAME, MI_COLLECTION))
                .thenReturn(scanRequestBuilder);
        when(scanRequestBuilder.withReadTimeoutSeconds(READ_TIMEOUT)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(scanResult);
        when(scanResult.iterator()).thenReturn(Collections.emptyIterator());

        // Execute
        int result = repository.fetchStatistics(dfScanParameters, selectField);

        // Verify
        assertEquals(0, result);
        verify(dfClient).scan(scanRequestBuilder);
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testFetchStatistics_IOException() throws IOException, ScanException {
        // Prepare
        DFQueryMetaData dfScanParameters = new DFQueryMetaData();
        dfScanParameters.setCollectionName(MI_COLLECTION);
        String selectField = "count";
        ScanRequestBuilder scanRequestBuilder = mock(ScanRequestBuilder.class);
        ScanResult scanResult = mock(ScanResult.class);
        Iterator<Record> iterator = mock(Iterator.class);
        Record record = mock(Record.class);
        JsonDocument document = new JsonDocument();
        document.withContents("{\"count\":42}");

        when(scanExpressionUtility.getScanRequestBuilder(dfScanParameters, DATABASE_NAME, MI_COLLECTION))
                .thenReturn(scanRequestBuilder);
        when(scanRequestBuilder.withReadTimeoutSeconds(READ_TIMEOUT)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(scanResult);
        when(scanResult.iterator()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true);
        when(iterator.next()).thenReturn(record);
        when(record.getDocument()).thenReturn(document);
        when(objectMapper.readTree("{\"count\":42}")).thenThrow(new IOException("Parse error"));

        // Execute
        repository.fetchStatistics(dfScanParameters, selectField);
    }

    @Test
    public void testFetchRecordsFromDf_Success() throws ScanException {
        // Prepare
        DFQueryMetaData dfScanParameters = new DFQueryMetaData();
        dfScanParameters.setCollectionName(MI_COLLECTION);
        ScanRequestBuilder scanRequestBuilder = mock(ScanRequestBuilder.class);
        ScanResult scanResult = mock(ScanResult.class);
        Iterator<Record> iterator = mock(Iterator.class);
        Record record = mock(Record.class);
        Document document = new JsonDocument();

        when(scanExpressionUtility.getScanRequestBuilder(dfScanParameters, DATABASE_NAME, MI_COLLECTION))
                .thenReturn(scanRequestBuilder);
        when(scanRequestBuilder.withReadTimeoutSeconds(READ_TIMEOUT)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(scanResult);
        when(scanResult.iterator()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.next()).thenReturn(record);
        when(record.getDocument()).thenReturn(document);

        // Execute
        List<Record> result = repository.fetchRecordsFromDf(dfScanParameters);

        // Verify
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(record, result.get(0));
        verify(dfClient).scan(scanRequestBuilder);
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testFetchRecordsFromDf_Exception() throws ScanException {
        // Prepare
        DFQueryMetaData dfScanParameters = new DFQueryMetaData();
        dfScanParameters.setCollectionName(MI_COLLECTION);
        ScanRequestBuilder scanRequestBuilder = mock(ScanRequestBuilder.class);

        when(scanExpressionUtility.getScanRequestBuilder(dfScanParameters, DATABASE_NAME, MI_COLLECTION))
                .thenReturn(scanRequestBuilder);
        when(scanRequestBuilder.withReadTimeoutSeconds(READ_TIMEOUT)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenThrow(new ScanException("Scan error"));

        // Execute
        repository.fetchRecordsFromDf(dfScanParameters);
    }

    @Test
    public void testCalculateMiSnapshotKey() throws Exception {
        // Prepare
        SubjectIdentifier subjectIdentifier = new SubjectIdentifier();
        subjectIdentifier.setBusinessDate(new Date(2023 - 1900, 9, 18)); // 18-10-2023
        subjectIdentifier.setLei("LEI123");
        subjectIdentifier.setAssetClass("FX");
        subjectIdentifier.setEntity("NWM");
        subjectIdentifier.setFlow("Trade");
        subjectIdentifier.setRegulation("EMIR");
        subjectIdentifier.setMessageType("New");

        Method method = getPrivateMethod("calculateMiSnapshotKey", SubjectIdentifier.class);

        // Execute
        String key = (String) method.invoke(repository, subjectIdentifier);

        // Verify
        assertNotNull(key);
        assertEquals(40, key.length()); // SHA-1 hash length
    }

    @Test
    public void testCalculateFoMiSnapshotKey() throws Exception {
        // Prepare
        FrontOfficeSubjectIdentifier subjectIdentifier = new FrontOfficeSubjectIdentifier("NWM", "LEI123", "FX", "EMIR", new Date(2023 - 1900, 9, 18));

        Method method = getPrivateMethod("calculateFoMiSnapshotKey", FrontOfficeSubjectIdentifier.class);

        // Execute
        String key = (String) method.invoke(repository, subjectIdentifier);

        // Verify
        assertNotNull(key);
        assertEquals(40, key.length()); // SHA-1 hash length
    }

    @Test
    public void testProcessFoMiData_Success() throws Exception {
        // Prepare
        DFQueryMetaData dfQueryMetaData = new DFQueryMetaData();
        dfQueryMetaData.setSelect("select *");
        dfQueryMetaData.setStatName("stat");
        Date businessDate = new Date(2023 - 1900, 9, 18);
        String assetClass = AssetClass.FOREIGN_EXCHANGE.value();
        List<String> jurisdictions = Arrays.asList(EMIR);
        Map<String, List<String>> foAssetWiseMap = Collections.singletonMap(assetClass, jurisdictions);
        Field foAssetWiseMapField = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredField("FO_ASSETWISE_MAP");
        foAssetWiseMapField.setAccessible(true);
        foAssetWiseMapField.set(null, foAssetWiseMap);

        when(itrConfiguration.getItr2ProtocolScheme()).thenReturn("https");
        when(itrConfiguration.getItr2ServiceName()).thenReturn("itr-service");
        doNothing().when(repository).persistFoJurisdictionDetermined(anyString(), anyString(), any(Date.class), anyMap());
        doNothing().when(repository).persistFoJurisdictionNotDetermined(anyString(), any(Date.class), anyInt(), anyInt());

        // Execute
        repository.processFoMiData(dfQueryMetaData, businessDate, assetClass);

        // Verify
        verify(repository).fetchFromItr(eq(dfQueryMetaData), anyMap(), eq("https://itr-service/emir_endpoint"), eq(EMIR), eq(businessDate), eq(assetClass));
        verify(repository).persistFoJurisdictionDetermined(eq(assetClass), eq(EMIR), eq(businessDate), anyMap());
        verify(repository).persistFoJurisdictionNotDetermined(eq(assetClass), eq(businessDate), anyInt(), anyInt());
    }

    @Test(expected = TaggingServiceRunTimeException.class)
    public void testProcessFoMiData_UnsupportedJurisdiction() throws Exception {
        // Prepare
        DFQueryMetaData dfQueryMetaData = new DFQueryMetaData();
        Date businessDate = new Date();
        String assetClass = AssetClass.FOREIGN_EXCHANGE.value();
        List<String> jurisdictions = Arrays.asList("UNKNOWN");
        Map<String, List<String>> foAssetWiseMap = Collections.singletonMap(assetClass, jurisdictions);
        Field foAssetWiseMapField = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredField("FO_ASSETWISE_MAP");
        foAssetWiseMapField.setAccessible(true);
        foAssetWiseMapField.set(null, foAssetWiseMap);

        // Execute
        repository.processFoMiData(dfQueryMetaData, businessDate, assetClass);
    }

    @Test
    public void testFetchFromItr_ParameterizedQuery() throws Exception {
        // Prepare
        DFQueryMetaData dfQueryMetaData = new DFQueryMetaData();
        dfQueryMetaData.setStatName("stat");
        Map<String, Map<String, Integer>> foMiDataMap = new HashMap<>();
        String itrUrl = "https://itr-service/emir_param_endpoint";
        String jurisdiction = EMIR;
        Date businessDate = new Date();
        String assetClass = AssetClass.ETD.value();
        Map<String, List<String>> emirQueryIds = Collections.singletonMap(assetClass, Arrays.asList("query1"));
        Field emirQueryIdsField = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredField("EMIR_ASSETWISE_QUERY_IDS");
        emirQueryIdsField.setAccessible(true);
        emirQueryIdsField.set(null, emirQueryIds);

        Method method = getPrivateMethod("fetchFromItr", DFQueryMetaData.class, Map.class, String.class, String.class, Date.class, String.class);
        doNothing().when(repository).fetchFromItrWithParamQuery(eq(dfQueryMetaData), eq(foMiDataMap), eq(itrUrl), eq(jurisdiction), eq(businessDate), eq(assetClass));

        // Execute
        Map<String, Map<String, Integer>> result = (Map<String, Map<String, Integer>>) method.invoke(repository, dfQueryMetaData, foMiDataMap, itrUrl, jurisdiction, businessDate, assetClass);

        // Verify
        assertEquals(foMiDataMap, result);
        verify(repository).fetchFromItrWithParamQuery(eq(dfQueryMetaData), eq(foMiDataMap), eq(itrUrl), eq(jurisdiction), eq(businessDate), eq(assetClass));
    }

    @Test
    public void testParseItrRecord_CftcCsa_ForeignExchange() throws Exception {
        // Prepare
        JsonParser jsonParser = mock(JsonParser.class);
        String assetClass = AssetClass.FOREIGN_EXCHANGE.value();
        String jurisdiction = CFTC_CSA;
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("cftc", "isVersionReportable=true, isTransactionReportable=true");
        jsonMap.put("canada", "isVersionReportable=false, isTransactionReportable=false");
        jsonMap.put(TRADING_PARTY_LEI, "LEI123");

        when(objectMapper.readValue(jsonParser, Map.class)).thenReturn(jsonMap);

        Method method = getPrivateMethod("parseItrRecord", JsonParser.class, String.class, String.class);

        // Execute
        Map<String, Map<String, Object>> result = (Map<String, Map<String, Object>>) method.invoke(repository, jsonParser, assetClass, jurisdiction);

        // Verify
        assertNotNull(result);
        assertEquals(2, result.size());
        Map<String, Object> cftcData = result.get(DF);
        assertTrue((Boolean) cftcData.get(VERSION_REPORTABLE));
        assertTrue((Boolean) cftcData.get(TRANSACTION_REPORTABLE));
        assertEquals("LEI123", cftcData.get(TRADING_PARTY_LEI));
        Map<String, Object> csaData = result.get(CSA);
        assertFalse((Boolean) csaData.get(VERSION_REPORTABLE));
        assertFalse((Boolean) csaData.get(TRANSACTION_REPORTABLE));
        assertEquals("LEI123", csaData.get(TRADING_PARTY_LEI));
    }

    @Test
    public void testPersistFoJurisdictionDetermined() throws Exception {
        // Prepare
        String assetClass = AssetClass.FOREIGN_EXCHANGE.value();
        String jurisdiction = EMIR;
        Date businessDate = new Date();
        Map<String, Integer> itrMiData = new HashMap<>();
        itrMiData.put(PLC_REPORTABLE, 10);
        itrMiData.put(PLC_NONREPORTABLE, 5);
        itrMiData.put(NV_REPORTABLE, 8);
        itrMiData.put(NV_NONREPORTABLE, 3);

        Method method = getPrivateMethod("persistFoJurisdictionDetermined", String.class, String.class, Date.class, Map.class);
        doReturn(new RecordId("record1")).when(repository).upsertFoMiSnapshot(any(FrontOfficeMIDashboardAnalytics.class));

        // Execute
        method.invoke(repository, assetClass, jurisdiction, businessDate, itrMiData);

        // Verify
        ArgumentCaptor<FrontOfficeMIDashboardAnalytics> captor = ArgumentCaptor.forClass(FrontOfficeMIDashboardAnalytics.class);
        verify(repository, times(2)).upsertFoMiSnapshot(captor.capture());
        List<FrontOfficeMIDashboardAnalytics> analytics = captor.getAllValues();
        assertEquals(2, analytics.size());
        FrontOfficeMIDashboardAnalytics plcAnalytics = analytics.get(0);
        assertEquals(NWM_PLC, plcAnalytics.getSubjectIdentifier().getEntity());
        assertEquals(10, plcAnalytics.getMiAnalyticsState().getReportableCount());
        assertEquals(5, plcAnalytics.getMiAnalyticsState().getNonReportableCount());
        FrontOfficeMIDashboardAnalytics nvAnalytics = analytics.get(1);
        assertEquals(NWM_NV, nvAnalytics.getSubjectIdentifier().getEntity());
        assertEquals(8, nvAnalytics.getMiAnalyticsState().getReportableCount());
        assertEquals(3, nvAnalytics.getMiAnalyticsState().getNonReportableCount());
    }
}
```

### Key Features of the Test Class
1. **Setup**:
   - Mocks dependencies (`DfConnectionManager`, `ItrConfiguration`, `ItrClient`, `DataFabricClient`, `ObjectMapper`).
   - Uses reflection to set `@Value` fields (`databaseName`, `miCollection`, etc.).
   - Mocks `ScanExpressionUtility` methods (`getDfClient`, `getScanRequestBuilder`).
   - Spies on `MIAnalyticsDashboardRepositoryImpl` to stub internal method calls.
   - Uses PowerMock to prepare `DateTimeService` for static method mocking (though not used in these tests).

2. **Test Methods**:
   - **createDfClientConnection**: Tests successful connection and `StartableException` handling.
   - **upsertMiSnapshot**: Tests successful upsert and `JsonProcessingException` handling.
   - **upsertFoMiSnapshot**: Tests successful upsert and `UpsertException` handling.
   - **fetchStatistics**: Tests successful fetch, empty result, and `IOException` handling.
   - **fetchRecordsFromDf**: Tests successful fetch and `ScanException` handling.
   - **calculateMiSnapshotKey**: Tests SHA-1 key generation for `SubjectIdentifier`.
   - **calculateFoMiSnapshotKey**: Tests SHA-1 key generation for `FrontOfficeSubjectIdentifier`.
   - **processFoMiData**: Tests jurisdiction processing and unsupported jurisdiction error.
   - **fetchFromItr**: Tests parameterized query path.
   - **parseItrRecord**: Tests `CFTC_CSA` jurisdiction parsing for `FOREIGN_EXCHANGE`.
   - **persistFoJurisdictionDetermined**: Tests persistence for `NWM_PLC` and `NWM_NV`.

3. **Assertions**:
   - Uses `assertNotNull` to verify non-null results.
   - Uses `assertEquals` to check expected values (e.g., `RecordId`, counts, keys).
   - Uses `ArgumentCaptor` to verify arguments passed to `dfClient.upsert` and `upsertFoMiSnapshot`.
   - Uses `verify` to check mock interactions.

4. **PowerMock**:
   - Annotates with `@RunWith(PowerMockRunner.class)` and `@PrepareForTest` for static mocking (though minimal in these tests).
   - Ready to mock `DateTimeService` for `fetchFromItrWithParamQuery` if needed.

### Dependencies
Ensure PowerMock and Mockito dependencies are in your project (see `DfConfigurerTest` response for `pom.xml`/`build.gradle` snippets). PowerMock is required for static mocking of `DateTimeService` in future tests of `fetchFromItrWithParamQuery`.

### Assumptions
- **MIConstants**:
  - Contains `BOI`, `MIFID`, `EMIR`, `MAS`, `SFTR`, `CFTC_CSA`, `DF`, `CSA`, `LEI_NWM_PLC`, `LEI_NWM_NV`, `NWM_PLC`, `NWM_NV`, `PLC_REPORTABLE`, `PLC_NONREPORTABLE`, `PLC_NOTFOUND`, `NV_REPORTABLE`, `NV_NONREPORTABLE`, `NV_NOTFOUND`, `TRADING_PARTY_LEI`, `VERSION_REPORTABLE`, `TRANSACTION_REPORTABLE`, `JND`.
  - `FO_ASSETWISE_MAP`, `EMIR_ASSETWISE_QUERY_IDS`, etc., are static fields in `MIConstants`.
- **Classes**:
  - `SubjectIdentifier` has getters for `businessDate`, `lei`, `assetClass`, `entity`, `flow`, `regulation`, `messageType`.
  - `FrontOfficeSubjectIdentifier` has constructor `(String entity, String lei, String assetClass, String regulation, Date businessDate)` and getters.
  - `MIDashboardAnalytics` and `FrontOfficeMIDashboardAnalytics` have `getSubjectIdentifier` and `setMiAnalyticsState`.
  - `DFQueryMetaData` has `getCollectionName`, `getStatName`, `setSelect`, `getSelect`, `getWhere`.
  - `ItrConfiguration` has `getItr2ProtocolScheme`, `getItr2ServiceName`.
  - `ItrClient.fetch` accepts `(String url, IQuery query, Consumer<InputStream> callback)`.
  - `ScanExpressionUtility` has `getDfClient` and `getScanRequestBuilder`.
- **Behavior**:
  - `dfClient` is set via `createDfClientConnection` but assumed non-null in tests for simplicity.
  - Exceptions are wrapped in `TaggingServiceRunTimeException` as shown.
  - No null checks on inputs unless specified.

### Potential Issues
- **MIConstants**: If constants like `EMIR`, `FO_ASSETWISE_MAP`, or `EMIR_ASSETWISE_QUERY_IDS` are missing or different, share `MIConstants.java`.
- **Class Structures**: If `SubjectIdentifier`, `FrontOfficeSubjectIdentifier`, `MIDashboardAnalytics`, etc., have different constructors or getters, share their definitions.
- **ScanExpressionUtility**: If `getDfClient` or `getScanRequestBuilder` have different signatures, share `ScanExpressionUtility.java`.
- **Reflection Errors**: If `@Value` field names or private method signatures are incorrect, a `NoSuchFieldException` or `NoSuchMethodException` will occur.
- **PowerMock Compatibility**: Ensure Mockito 2.x+ for PowerMock (see `DfConfigurerTest` response). If using Mockito 1.x, update dependencies.

### Next Steps
1. **Run the Test Class**:
   - Save `MIAnalyticsDashboardRepositoryImplTest.java` in `src/test/java/com/rbs/tntr/business/taggingService/repository/`.
   - Ensure PowerMock dependencies are in `pom.xml` or `build.gradle`.
   - Run the tests in IntelliJ IDEA (Community Edition 2024.2.3) or via Maven/Gradle.
   - Share the test output, including any errors or stack traces.

2. **Resolve Prior Test Issues**:
   - **TntrTradeTransformerImplTest**:
     - Update `TntrTradeTransformerImplTest.java` with correct imports for `RepositoryCommand` and `RepositoryCommandFactory` (see response for `Cannot resolve symbol`).
     - Share `RepositoryCommand.java`, `RepositoryCommandFactory.java`, `ReportingTemplate.java`.
     - Run the test and share results.
   - **DfConfigurerTest**:
     - Run `DfConfigurerTest.java` (response for `DfConfigurer`) and share results.
     - Ensure PowerMock dependencies are added.
   - **ApiExceptionHandlerTest**:
     - Run `ApiExceptionHandlerTest.java` (previous response) and share results.
     - Share `ApiException.java` if its structure differs.
   - **BlotterExportMappingsRepositoryImplTest**:
     - Run the corrected version (first response) and share results.
     - Provide `BlotterExportMappings.java`, `BlotterExportMappingsResult.java`, `ScanExpressionUtility.java`, `StringConstants.java`, and a sample `BlotterExportMappings.json`.
   - **SsoConfigurationTest**:
     - Run the provided test (second response) and share results.
     - Confirm any validation for `@Value` fields.
   - **Other Tests**:
     - Share results or errors for `ReconDetailsServiceImplTest`, `DataFabricExportMiReportsUtilTest`, `DfFieldMapperTest`, `DfTradePersistManagerTest`, etc.
     - For `DataFabricExportMiReportsUtilTest`, provide `WhereClause.java`.

3. **Provide Missing Classes**:
   - Share `MIConstants.java`, `SubjectIdentifier.java`, `FrontOfficeSubjectIdentifier.java`, `MIDashboardAnalytics.java`, `FrontOfficeMIDashboardAnalytics.java`, `DFQueryMetaData.java`, `ScanExpressionUtility.java`, `ItrConfiguration.java`, `ItrClient.java`, `Itr2Query.java`, `Itr2ParameterisedQuery.java` if test failures occur.
   - Share `RepositoryCommand.java`, `RepositoryCommandFactory.java`, `ReportingTemplate.java`, `ApiException.java`, `DfConnectionConfig.java`, `TntrRepositoryConfig.java`, etc., for prior test issues.
   - Share `pom.xml` or `build.gradle` to confirm Mockito/PowerMock versions.

4. **Confirm Mockito/PowerMock Setup**:
   - Check `pom.xml` or `build.gradle` for Mockito version (1.x or 2.x+).
   - Ensure PowerMock is configured for static mocking.

Please run the `MIAnalyticsDashboardRepositoryImplTest` class and share the results. Additionally, provide the requested class files and the results of the `TntrTradeTransformerImplTest`, `DfConfigurerTest`, `ApiExceptionHandlerTest`, `BlotterExportMappingsRepositoryImplTest`, and `SsoConfigurationTest` to resolve all issues comprehensively!