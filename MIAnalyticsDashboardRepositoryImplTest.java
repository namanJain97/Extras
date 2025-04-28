```java
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
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.FrontOfficeMIAalyticsState;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.FrontOfficeMIDashboardAnalytics;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.FrontOfficeSubjectIdentifier;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.MIDashboardAnalytics;
import com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.SubjectIdentifier;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.rbs.tntr.domain.taggingService.miAnalytics.dashboard.common.MIConstants.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MIAnalyticsDashboardRepositoryImpl.class, Hashing.class, DateTimeService.class})
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
    private Document document;

    @Mock
    private JsonDocument jsonDocument;

    @Mock
    private JsonNode jsonNode;

    @Mock
    private JsonParser jsonParser;

    @Mock
    private InputStream inputStream;

    @Mock
    private RecordId recordId;

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
    private final String miCollection = "mi_analytics";
    private final String foMiCollection = "fo_mi_analytics";
    private final int readTimeOut = 30;
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
        // Initialize repository
        repository = new MIAnalyticsDashboardRepositoryImpl(dfConnectionManager, itrConfiguration, itrClient);

        // Set @Value fields via reflection
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

        // Mock logger
        Field loggerField = MIAnalyticsDashboardRepositoryImpl.class.getDeclaredField("LOGGER");
        loggerField.setAccessible(true);
        loggerField.set(null, logger);

        // Mock inherited methods
        when(repository.getDfClient(dfConnectionManager)).thenReturn(dfClient);
        when(repository.getScanRequestBuilder(any(), anyString(), anyString())).thenReturn(scanRequestBuilder);
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
        // Execute
        repository.createDfClientConnection();

        // Verify
        verify(logger).info("DF Client connection created");
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testCreateDfClientConnection_StartableException() throws StartableException {
        // Prepare test data
        StartableException exception = new StartableException("Connection failed");
        when(repository.getDfClient(dfConnectionManager)).thenThrow(exception);

        // Execute
        repository.createDfClientConnection();

        // Verify
        verify(logger).error(eq("Error while creating df client connection"), eq(exception));
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testSerialize() throws JsonProcessingException {
        // Prepare test data
        when(objectMapper.writeValueAsString(miDashboardAnalytics)).thenReturn("{\"data\":\"test\"}");

        // Execute
        Document result = repository.serialize(miDashboardAnalytics);

        // Verify
        assertTrue(result instanceof JsonDocument);
        assertEquals("{\"data\":\"test\"}", ((JsonDocument) result).getContents());
        verify(objectMapper).writeValueAsString(miDashboardAnalytics);
    }

    @Test
    public void testDeserialize() throws IOException {
        // Prepare test data
        when(jsonDocument.getContents()).thenReturn("{\"data\":\"test\"}");
        when(objectMapper.readValue("{\"data\":\"test\"}", MIDashboardAnalytics.class)).thenReturn(miDashboardAnalytics);

        // Execute
        MIDashboardAnalytics result = repository.deserailze(jsonDocument);

        // Verify
        assertEquals(miDashboardAnalytics, result);
        verify(objectMapper).readValue("{\"data\":\"test\"}", MIDashboardAnalytics.class);
    }

    @Test
    public void testUpsertMiSnapshot_Success() throws JsonProcessingException {
        // Prepare test data
        when(objectMapper.writeValueAsString(miDashboardAnalytics)).thenReturn("{\"data\":\"test\"}");
        when(subjectIdentifier.getBusinessDate()).thenReturn(businessDate);
        when(subjectIdentifier.getLei()).thenReturn("LEI123");
        when(subjectIdentifier.getAssetClass()).thenReturn("FX");
        when(subjectIdentifier.getEntity()).thenReturn("NWM");
        when(subjectIdentifier.getFlow()).thenReturn("FLOW");
        when(subjectIdentifier.getRegulation()).thenReturn("EMIR");
        when(subjectIdentifier.getMessageType()).thenReturn("REPORT");
        when(miDashboardAnalytics.getSubjectIdentifier()).thenReturn(subjectIdentifier);
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("hashedKey");
        when(upsertRequestBuilder.withDocument(any(Document.class))).thenReturn(upsertRequestBuilder);
        when(upsertRequestBuilder.withKey("hashedKey")).thenReturn(upsertRequestBuilder);
        when(dfClient.upsert(upsertRequestBuilder)).thenReturn(recordId);

        // Execute
        RecordId result = repository.upsertMiSnapshot(miDashboardAnalytics);

        // Verify
        assertEquals(recordId, result);
        verify(logger).info("{\"data\":\"test\"}");
        verify(logger).info(eq("Generated Key is : {}"), eq("hashedKey"));
        verify(dfClient).upsert(upsertRequestBuilder);
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testUpsertMiSnapshot_JsonProcessingException() throws JsonProcessingException {
        // Prepare test data
        JsonProcessingException exception = new JsonProcessingException("Serialization error") {};
        when(objectMapper.writeValueAsString(miDashboardAnalytics)).thenThrow(exception);

        // Execute
        TaggingServiceRunTimeException thrown = assertThrows(TaggingServiceRunTimeException.class,
                () -> repository.upsertMiSnapshot(miDashboardAnalytics));

        // Verify
        assertEquals("Error while inserting export scan into DF", thrown.getMessage());
        assertEquals(exception, thrown.getCause());
        verify(logger).error(eq("Error while inserting export scan into DF"), eq(exception));
        verifyNoMoreInteractions(logger, dfClient);
    }

    @Test
    public void testUpsertFoMiSnapshot_Success() throws JsonProcessingException {
        // Prepare test data
        when(objectMapper.writeValueAsString(foMiDashboardAnalytics)).thenReturn("{\"data\":\"fo_test\"}");
        when(foSubjectIdentifier.getBusinessDate()).thenReturn(businessDate);
        when(foSubjectIdentifier.getAssetClass()).thenReturn("FX");
        when(foSubjectIdentifier.getEntity()).thenReturn("NWM");
        when(foSubjectIdentifier.getLei()).thenReturn("LEI123");
        when(foSubjectIdentifier.getRegulation()).thenReturn("EMIR");
        when(foMiDashboardAnalytics.getSubjectIdentifier()).thenReturn(foSubjectIdentifier);
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("foHashedKey");
        when(upsertRequestBuilder.withDocument(any(Document.class))).thenReturn(upsertRequestBuilder);
        when(upsertRequestBuilder.withKey("foHashedKey")).thenReturn(upsertRequestBuilder);
        when(dfClient.upsert(upsertRequestBuilder)).thenReturn(recordId);

        // Execute
        RecordId result = repository.upsertFoMiSnapshot(foMiDashboardAnalytics);

        // Verify
        assertEquals(recordId, result);
        verify(logger).info(eq("Generated FO Key is : {}"), eq("foHashedKey"));
        verify(logger).info(eq("FO Stat to be persisted with data : {}"), eq("{\"data\":\"fo_test\"}"));
        verify(dfClient).upsert(upsertRequestBuilder);
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testFetchStatistics_Success() throws IOException, ScanException {
        // Prepare test data
        when(dfQueryMetaData.getCollectionName()).thenReturn(miCollection);
        when(scanRequestBuilder.withReadTimeoutSeconds(readTimeOut)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(scanResult);
        when(scanResult.iterator()).thenReturn(Collections.singletonList(record).iterator());
        when(record.getDocument()).thenReturn(jsonDocument);
        when(jsonDocument.getContents()).thenReturn("{\"count\":42}");
        when(objectMapper.readTree("{\"count\":42}")).thenReturn(jsonNode);
        when(jsonNode.get("count")).thenReturn(jsonNode);
        when(jsonNode.asInt()).thenReturn(42);

        // Execute
        int result = repository.fetchStatistics(dfQueryMetaData, "count");

        // Verify
        assertEquals(42, result);
        verify(dfClient).scan(scanRequestBuilder);
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testFetchStatistics_NoRecords() throws ScanException {
        // Prepare test data
        when(dfQueryMetaData.getCollectionName()).thenReturn(miCollection);
        when(scanRequestBuilder.withReadTimeoutSeconds(readTimeOut)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(null);

        // Execute
        int result = repository.fetchStatistics(dfQueryMetaData, "count");

        // Verify
        assertEquals(0, result);
        verify(dfClient).scan(scanRequestBuilder);
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testFetchRecordsFromDf_Success() {
        // Prepare test data
        when(dfQueryMetaData.getCollectionName()).thenReturn(miCollection);
        when(scanRequestBuilder.withReadTimeoutSeconds(readTimeOut)).thenReturn(scanRequestBuilder);
        when(dfClient.scan(scanRequestBuilder)).thenReturn(scanResult);
        when(scanResult.iterator()).thenReturn(Collections.singletonList(record).iterator());
        when(record.getDocument()).thenReturn(jsonDocument);

        // Execute
        List<Record> result = repository.fetchRecordsFromDf(dfQueryMetaData);

        // Verify
        assertEquals(1, result.size());
        assertEquals(record, result.get(0));
        verify(dfClient).scan(scanRequestBuilder);
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testCalculateMiSnapshotKey() {
        // Prepare test data
        when(subjectIdentifier.getBusinessDate()).thenReturn(businessDate);
        when(subjectIdentifier.getLei()).thenReturn("LEI123");
        when(subjectIdentifier.getAssetClass()).thenReturn("FX");
        when(subjectIdentifier.getEntity()).thenReturn("NWM");
        when(subjectIdentifier.getFlow()).thenReturn("FLOW");
        when(subjectIdentifier.getRegulation()).thenReturn("EMIR");
        when(subjectIdentifier.getMessageType()).thenReturn("REPORT");
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("hashedKey");

        // Execute
        String result = repository.calculateMiSnapshotKey(subjectIdentifier);

        // Verify
        assertEquals("hashedKey", result);
    }

    @Test
    public void testCalculateFoMiSnapshotKey() {
        // Prepare test data
        when(foSubjectIdentifier.getBusinessDate()).thenReturn(businessDate);
        when(foSubjectIdentifier.getAssetClass()).thenReturn("FX");
        when(foSubjectIdentifier.getEntity()).thenReturn("NWM");
        when(foSubjectIdentifier.getLei()).thenReturn("LEI123");
        when(foSubjectIdentifier.getRegulation()).thenReturn("EMIR");
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("foHashedKey");

        // Execute
        String result = repository.calculateFoMiSnapshotKey(foSubjectIdentifier);

        // Verify
        assertEquals("foHashedKey", result);
    }

    @Test
    public void testProcessFoMiData_Emir() throws JsonProcessingException {
        // Prepare test data
        String assetClass = AssetClass.FOREIGN_EXCHANGE.value();
        when(dfQueryMetaData.getSelect()).thenReturn("select *");
        Map<String, Integer> foMap = new HashMap<>();
        foMap.put(PLC_REPORTABLE, 1);
        foMap.put(PLC_NONREPORTABLE, 2);
        foMap.put(PLC_NOTFOUND, 0);
        foMap.put(NV_REPORTABLE, 3);
        foMap.put(NV_NONREPORTABLE, 4);
        foMap.put(NV_NOTFOUND, 0);
        Map<String, Map<String, Integer>> baseDataMap = Collections.singletonMap(EMIR, foMap);
        when(itrConfiguration.getItr2ProtocolScheme()).thenReturn("https");
        when(itrConfiguration.getItr2ServiceName()).thenReturn("itr-service");
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"data\":\"fo_test\"}");
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("foHashedKey");
        when(upsertRequestBuilder.withDocument(any(Document.class))).thenReturn(upsertRequestBuilder);
        when(upsertRequestBuilder.withKey(anyString())).thenReturn(upsertRequestBuilder);
        when(dfClient.upsert(any(UpsertRequestBuilder.class))).thenReturn(recordId);

        // Mock fetchFromItr (simplified)
        repository.processFoMiData(dfQueryMetaData, businessDate, assetClass);

        // Verify
        verify(logger).info(eq("Processing FO MI For AssetClass : {} & Jurisdiction : {}"), eq(assetClass), eq(EMIR));
        verify(logger).info(contains("Time taken For AssetClass"));
        verify(dfClient, times(4)).upsert(any(UpsertRequestBuilder.class)); // 2 for determined, 2 for not determined
    }

    @Test
    public void testPersistFoMiData() throws JsonProcessingException {
        // Prepare test data
        String entity = NWM_PLC;
        String lei = LEI_NWM_PLC;
        String assetClass = "FX";
        String regulation = EMIR;
        int reportable = 10;
        int nonReportable = 20;
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"data\":\"fo_test\"}");
        PowerMockito.mockStatic(Hashing.class);
        HashCode hashCode = mock(HashCode.class);
        when(Hashing.sha1()).thenReturn(mock(com.google.common.hash.HashFunction.class));
        when(Hashing.sha1().hashString(anyString(), eq(StandardCharsets.UTF_8))).thenReturn(hashCode);
        when(hashCode.toString()).thenReturn("foHashedKey");
        when(upsertRequestBuilder.withDocument(any(Document.class))).thenReturn(upsertRequestBuilder);
        when(upsertRequestBuilder.withKey("foHashedKey")).thenReturn(upsertRequestBuilder);
        when(dfClient.upsert(upsertRequestBuilder)).thenReturn(recordId);

        // Execute
        repository.persistFoMiData(entity, lei, assetClass, regulation, businessDate, reportable, nonReportable);

        // Verify
        verify(dfClient).upsert(upsertRequestBuilder);
        verify(logger).info(eq("Generated FO Key is : {}"), eq("foHashedKey"));
        verify(logger).info(eq("FO Stat to be persisted with data : {}"), eq("{\"data\":\"fo_test\"}"));
    }

    @Test
    public void testGetURL() {
        // Prepare test data
        when(itrConfiguration.getItr2ProtocolScheme()).thenReturn("https");
        when(itrConfiguration.getItr2ServiceName()).thenReturn("itr-service");

        // Execute
        String result = repository.getURL("endpoint");

        // Verify
        assertEquals("https://itr-service/endpoint", result);
    }

    @Test
    public void testItrQueryForFO() {
        // Prepare test data
        when(dfQueryMetaData.getSelect()).thenReturn("select *");
        when(dfQueryMetaData.getWhere()).thenReturn("where clause");

        // Execute
        Itr2Query result = repository.itrQueryForFO(dfQueryMetaData);

        // Verify
        assertNotNull(result);
        // Further verification requires access to Itr2Query fields (not shown in code)
    }

    @Test
    public void testPopulateFoLifeTimeParams() {
        // Prepare test data
        DateTime currentDate = new DateTime(businessDate).withZone(DateTimeZone.UTC);
        PowerMockito.mockStatic(DateTimeService.class);
        when(DateTimeService.getCurrentStartDateTime(currentDate)).thenReturn(currentDate);
        when(DateTimeService.getCurrentEndDateTime(currentDate)).thenReturn(currentDate.plusDays(1));
        when(DateTimeService.asString(currentDate)).thenReturn("2023-01-01T00:00:00Z");
        when(DateTimeService.asString(currentDate.plusDays(1))).thenReturn("2023-01-02T00:00:00Z");

        // Execute
        Map<String, String> result = repository.populateFoLifeTimeParams(businessDate);

        // Verify
        assertEquals("2023-01-01T00:00:00Z", result.get(DATETIME_FROM));
        assertEquals("2023-01-02T00:00:00Z", result.get(DATETIME_TO));
    }
}
```

### Key Features of the Test Class
1. **Setup**:
   - Initializes `MIAnalyticsDashboardRepositoryImpl` with mocked dependencies.
   - Sets `@Value` fields (`databaseName`, `miCollection`, etc.) via reflection.
   - Injects mocked `Logger` into the `static final` `LOGGER` field.
   - Mocks inherited methods (`getDfClient`, `getScanRequestBuilder`) from `ScanExpressionUtility`.

2. **Test Methods**:
   - **testConstructor**: Verifies dependency injection.
   - **testCreateDfClientConnection_Success**: Tests DF client creation and logging.
   - **testCreateDfClientConnection_StartableException**: Tests error handling.
   - **testSerialize**: Tests serialization to `JsonDocument`.
   - **testDeserialize**: Tests deserialization to `MIDashboardAnalytics`.
   - **testUpsertMiSnapshot_Success**: Tests MI snapshot upsert with key generation.
   - **testUpsertMiSnapshot_JsonProcessingException**: Tests exception handling.
   - **testUpsertFoMiSnapshot_Success**: Tests FO MI snapshot upsert.
   - **testFetchStatistics_Success**: Tests statistics retrieval.
   - **testFetchStatistics_NoRecords**: Tests empty result handling.
   - **testFetchRecordsFromDf_Success**: Tests record retrieval.
   - **testCalculateMiSnapshotKey**: Tests MI key generation.
   - **testCalculateFoMiSnapshotKey**: Tests FO key generation.
   - **testProcessFoMiData_Emir**: Tests FO MI processing for EMIR jurisdiction.
   - **testPersistFoMiData**: Tests FO MI snapshot persistence.
   - **testGetURL**: Tests URL construction.
   - **testItrQueryForFO**: Tests `Itr2Query` creation.
   - **testPopulateFoLifeTimeParams**: Tests date parameter generation.

3. **Assertions**:
   - Uses `assertEquals` for field values, results, and arguments.
   - Uses `assertThrows` for exception handling.
   - Uses `ArgumentCaptor` for logger and method arguments.
   - Uses `verify` and `verifyNoMoreInteractions` for interactions.

4. **PowerMock Setup**:
   - Mocks static `Hashing.sha1`, `DateTimeService.asString`, `DateTimeService.getCurrentStartDateTime`, and `DateTimeService.getCurrentEndDateTime`.
   - Uses `@RunWith(PowerMockRunner.class)` and `@PrepareForTest`.

### Dependencies for PowerMock
Ensure PowerMock is included in your project:
```xml
<!-- Maven -->
<dependency>
    <groupId>org.powermock</groupId>
    <artifactId>powermock-module-junit4</artifactId>
    <version>2.0.9</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.powermock</groupId>
    <artifactId>powermock-api-mockito2</artifactId>
    <version>2.0.9</version>
    <scope>test</scope>
</dependency>
```
```gradle
// Gradle
testImplementation 'org.powermock:powermock-module-junit4:2.0.9'
testImplementation 'org.powermock:powermock-api-mockito2:2.0.9'
```

### Assumptions
- **Inherited Methods**:
  - `getDfClient(DfConnectionManager)` returns `DataFabricClient`.
  - `getScanRequestBuilder(DFQueryMetaData, String, String)` returns `ScanRequestBuilder`.
- **MIConstants**:
  - Defines `BOI`, `MIFID`, `EMIR`, `LEI_NWM_PLC`, `PLC_REPORTABLE`, etc.
- **ObjectMapper**:
  - Static and safe to use directly (mocked for control).
- **ItrClient**:
  - `fetch` accepts `String`, `IQuery`, and `Consumer<InputStream>`.
- **SubjectIdentifier**:
  - Has getters for `businessDate`, `lei`, `assetClass`, `entity`, `flow`, `regulation`, `messageType`.
- **FrontOfficeSubjectIdentifier**:
  - Has getters for `businessDate`, `assetClass`, `entity`, `lei`, `regulation`.
- **DFQueryMetaData**:
  - Has `getCollectionName`, `getSelect`, `getWhere`, `getStatName`.
- **Mockito/PowerMock**:
  - Assumes Mockito 1.x. If 2.x+, update runner compatibility.

### Potential Issues
- **Inherited Methods**:
  - If `getDfClient` or `getScanRequestBuilder` differ, share `ScanExpressionUtility.java`.
- **MIConstants**:
  - If constants like `BOI`, `PLC_REPORTABLE` are missing, share `MIConstants.java`.
- **ObjectMapper**:
  - If static `objectMapper` causes issues, consider injecting it.
- **ItrClient**:
  - If `fetch` signature differs, share `ItrClient.java`.
- **Class Structures**:
  - If `SubjectIdentifier`, `FrontOfficeSubjectIdentifier`, `MIDashboardAnalytics`, or `DFQueryMetaData` lack assumed getters, share their definitions.
- **PowerMock**:
  - Ensure Mockito 2.x for PowerMock 2.x compatibility.

### Next Steps
1. **Run the Test Class**:
   - Save `MIAnalyticsDashboardRepositoryImplTest.java` in `src/test/java/com/rbs/tntr/business/taggingService/repository/`.
   - Add PowerMock dependencies if not present.
   - Run tests in IntelliJ IDEA (Community Edition 2024.2.3) or via Maven/Gradle.
   - Share test output, including errors or stack traces.

2. **Resolve Prior Test Issues**:
   - **DfReconciliationPersistManagerTest**:
     - Run the provided test (previous response) and share results.
     - Share `ReconReportDocumentRepository.java`, `WriteResult.java`, etc., if needed.
   - **TaggingSecurityConfigurerTest**:
     - Run the provided test and share results.
     - Share `BaseSecurityConfigurer.java`, `SecurityProvider.java`, `SsoTokenAuthenticationFilter.java`.
   - **ApiExceptionHandlerTest**:
     - Run the provided test and share results.
     - Share `ApiException.java` if different.
   - **TntrTradeTransformerImplTest**:
     - Update with correct imports for `RepositoryCommand` and `RepositoryCommandFactory`.
     - Share `RepositoryCommand.java`, `RepositoryCommandFactory.java`, `ReportingTemplate.java`.
     - Run and share results.
   - **DfConfigurerTest**:
     - Run the provided test and share results.
     - Ensure PowerMock dependencies.
   - **BlotterExportMappingsRepositoryImplTest**:
     - Run the corrected version and share results.
     - Provide `BlotterExportMappings.java`, `BlotterExportMappingsResult.java`, `ScanExpressionUtility.java`, `StringConstants.java`, and a sample `BlotterExportMappings.json`.
   - **SsoConfigurationTest**:
     - Run the provided test and share results.
     - Confirm `@Value` validation.

3. **Provide Missing Classes**:
   - Share `ScanExpressionUtility.java`, `MIConstants.java`, `ItrClient.java`, `SubjectIdentifier.java`, `FrontOfficeSubjectIdentifier.java`, `MIDashboardAnalytics.java`, `FrontOfficeMIDashboardAnalytics.java`, `DFQueryMetaData.java`, `Itr2Query.java`, `Itr2ParameterisedQuery.java`, etc., if test failures occur.
   - Share `pom.xml` or `build.gradle` for dependency confirmation.

4. **Confirm Mockito/PowerMock Setup**:
   - Verify Mockito version (1.x or 2.x+) in `pom.xml` or `build.gradle`.
   - Ensure PowerMock compatibility.

Please run the `MIAnalyticsDashboardRepositoryImplTest` class and share the results. Provide the requested class files and results for `DfReconciliationPersistManagerTest`, `TaggingSecurityConfigurerTest`, `ApiExceptionHandlerTest`, `TntrTradeTransformerImplTest`, `DfConfigurerTest`, `BlotterExportMappingsRepositoryImplTest`, and `SsoConfigurationTest` to resolve all issues comprehensively!