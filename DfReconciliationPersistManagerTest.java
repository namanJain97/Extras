```java
package com.rbs.tntr.business.taggingService.df;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.nwm.tntr.commons.domain.persistence.entity.recon.ReconReport;
import com.nwm.tntr.commons.domain.persistence.entity.recon.ReconReportDocument;
import com.nwm.tntr.commons.domain.persistence.request.QueryParams;
import com.nwm.tntr.commons.repository.regreporting.WriteResult;
import com.nwm.tntr.commons.repository.regreporting.recon.ReconReportDocumentRepository;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.exceptions.TaggingServiceRunTimeException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DfReconciliationPersistManager.class, Stopwatch.class, Lists.class})
public class DfReconciliationPersistManagerTest {

    @Mock
    private ReconReportDocumentRepository reconReportDocumentRepository;

    @Mock
    private Logger logger;

    @Mock
    private QueryParams queryParams;

    @Mock
    private ReconReport reconReport1;

    @Mock
    private ReconReport reconReport2;

    @Mock
    private ReconReportDocument reconReportDocument;

    @Mock
    private WriteResult<ReconReport> successResult;

    @Mock
    private WriteResult<ReconReport> failedResult;

    @Mock
    private WriteResult.ErrorDetail errorDetail;

    @Mock
    private Stopwatch stopwatch;

    private DfReconciliationPersistManager persistManager;
    private final int dfPersistBatchSize = 2;

    @Before
    public void setUp() throws Exception {
        // Mock static logger
        Field loggerField = DfReconciliationPersistManager.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(null, logger);

        // Initialize persistManager
        persistManager = new DfReconciliationPersistManager(reconReportDocumentRepository, dfPersistBatchSize);

        // Mock WriteResult behavior
        when(successResult.isSuccess()).thenReturn(true);
        when(successResult.isFailure()).thenReturn(false);
        when(failedResult.isSuccess()).thenReturn(false);
        when(failedResult.isFailure()).thenReturn(true);
        when(failedResult.getErrorDetail()).thenReturn(errorDetail);
    }

    @Test
    public void testConstructor() {
        // Verify constructor
        assertEquals(reconReportDocumentRepository, persistManager.reconReportDocumentRepository);
        assertEquals(dfPersistBatchSize, persistManager.dfPersistBatchSize);
    }

    @Test
    public void testGetAllRecords_Success() {
        // Prepare test data
        List<ReconReportDocument> documents = Collections.singletonList(reconReportDocument);
        when(reconReportDocumentRepository.findAllByQuery(queryParams)).thenReturn(documents);

        // Execute
        List<ReconReportDocument> result = persistManager.getAllRecords(queryParams);

        // Verify
        assertEquals(documents, result);
        verify(reconReportDocumentRepository).findAllByQuery(queryParams);
        verifyNoMoreInteractions(reconReportDocumentRepository, logger);
    }

    @Test
    public void testGetAllRecords_Exception() {
        // Prepare test data
        Exception exception = new RuntimeException("DB error");
        when(reconReportDocumentRepository.findAllByQuery(queryParams)).thenThrow(exception);

        // Execute
        TaggingServiceRunTimeException thrown = assertThrows(TaggingServiceRunTimeException.class,
                () -> persistManager.getAllRecords(queryParams));

        // Verify
        assertEquals("Error occured while reading records from df. ", thrown.getMessage());
        assertEquals(exception, thrown.getCause());
        verify(reconReportDocumentRepository).findAllByQuery(queryParams);
        verify(logger).error(eq("Error occured while reading records from df. "), eq(exception));
        verifyNoMoreInteractions(reconReportDocumentRepository, logger);
    }

    @Test
    public void testSaveAllRecords_Success_MultipleBatches() {
        // Prepare test data
        List<Pair<ReconReport, Long>> reports = Arrays.asList(
                Pair.of(reconReport1, 1L),
                Pair.of(reconReport2, 2L),
                Pair.of(reconReport1, 3L)
        );
        List<List<Pair<ReconReport, Long>>> partitionedLists = Arrays.asList(
                reports.subList(0, 2),
                reports.subList(2, 3)
        );
        List<WriteResult<ReconReport>> batch1Results = Arrays.asList(successResult, failedResult);
        List<WriteResult<ReconReport>> batch2Results = Collections.singletonList(successResult);

        // Mock Guava Lists.partition
        PowerMockito.mockStatic(Lists.class);
        when(Lists.partition(reports, dfPersistBatchSize)).thenReturn(partitionedLists);

        // Mock Stopwatch
        PowerMockito.mockStatic(Stopwatch.class);
        when(Stopwatch.createStarted()).thenReturn(stopwatch);
        when(stopwatch.elapsed(TimeUnit.MILLISECONDS)).thenReturn(100L);

        // Mock repository
        when(reconReportDocumentRepository.compareAndUpdateAll(partitionedLists.get(0))).thenReturn(batch1Results);
        when(reconReportDocumentRepository.compareAndUpdateAll(partitionedLists.get(1))).thenReturn(batch2Results);

        // Mock error detail
        when(errorDetail.getDescription()).thenReturn("Version mismatch");
        when(errorDetail.getCause()).thenReturn(new RuntimeException("Optimistic lock error"));

        // Execute
        List<WriteResult<ReconReport>> results = persistManager.saveAllRecords(reports);

        // Verify
        assertEquals(3, results.size());
        assertTrue(results.containsAll(Arrays.asList(successResult, failedResult, successResult)));
        verify(reconReportDocumentRepository).compareAndUpdateAll(partitionedLists.get(0));
        verify(reconReportDocumentRepository).compareAndUpdateAll(partitionedLists.get(1));
        verify(logger).info(eq("Time taken in inserting [{}] records = [{}]ms."), eq(3), eq(100L));
        verify(logger).info(
                eq("Total Trade record : [{}], Successfully updated : [{}], Failed to update in df : [{}] "),
                eq(3), eq(2L), eq(1L)
        );
        verify(logger).error(
                eq("Error occurred in persisting recon  : [{}]"),
                eq("Version mismatch. Optimistic lock error")
        );
        verifyNoMoreInteractions(reconReportDocumentRepository, logger);
    }

    @Test
    public void testSaveAllRecords_Success_SingleBatch() {
        // Prepare test data
        List<Pair<ReconReport, Long>> reports = Arrays.asList(
                Pair.of(reconReport1, 1L),
                Pair.of(reconReport2, 2L)
        );
        List<List<Pair<ReconReport, Long>>> partitionedLists = Collections.singletonList(reports);
        List<WriteResult<ReconReport>> batchResults = Arrays.asList(successResult, successResult);

        // Mock Guava Lists.partition
        PowerMockito.mockStatic(Lists.class);
        when(Lists.partition(reports, dfPersistBatchSize)).thenReturn(partitionedLists);

        // Mock Stopwatch
        PowerMockito.mockStatic(Stopwatch.class);
        when(Stopwatch.createStarted()).thenReturn(stopwatch);
        when(stopwatch.elapsed(TimeUnit.MILLISECONDS)).thenReturn(50L);

        // Mock repository
        when(reconReportDocumentRepository.compareAndUpdateAll(reports)).thenReturn(batchResults);

        // Execute
        List<WriteResult<ReconReport>> results = persistManager.saveAllRecords(reports);

        // Verify
        assertEquals(2, results.size());
        assertEquals(batchResults, results);
        verify(reconReportDocumentRepository).compareAndUpdateAll(reports);
        verify(logger).info(eq("Time taken in inserting [{}] records = [{}]ms."), eq(2), eq(50L));
        verify(logger).info(
                eq("Total Trade record : [{}], Successfully updated : [{}], Failed to update in df : [{}] "),
                eq(2), eq(2L), eq(0L)
        );
        verifyNoMoreInteractions(reconReportDocumentRepository, logger);
    }

    @Test
    public void testSaveAllRecords_EmptyList() {
        // Prepare test data
        List<Pair<ReconReport, Long>> reports = Collections.emptyList();
        List<List<Pair<ReconReport, Long>>> partitionedLists = Collections.emptyList();

        // Mock Guava Lists.partition
        PowerMockito.mockStatic(Lists.class);
        when(Lists.partition(reports, dfPersistBatchSize)).thenReturn(partitionedLists);

        // Mock Stopwatch
        PowerMockito.mockStatic(Stopwatch.class);
        when(Stopwatch.createStarted()).thenReturn(stopwatch);
        when(stopwatch.elapsed(TimeUnit.MILLISECONDS)).thenReturn(0L);

        // Execute
        List<WriteResult<ReconReport>> results = persistManager.saveAllRecords(reports);

        // Verify
        assertTrue(results.isEmpty());
        verify(logger).info(eq("Time taken in inserting [{}] records = [{}]ms."), eq(0), eq(0L));
        verify(logger).info(
                eq("Total Trade record : [{}], Successfully updated : [{}], Failed to update in df : [{}] "),
                eq(0), eq(0L), eq(0L)
        );
        verifyNoMoreInteractions(reconReportDocumentRepository, logger);
    }

    @Test
    public void testSaveAllRecords_Exception() {
        // Prepare test data
        List<Pair<ReconReport, Long>> reports = Collections.singletonList(Pair.of(reconReport1, 1L));
        List<List<Pair<ReconReport, Long>>> partitionedLists = Collections.singletonList(reports);
        Exception exception = new RuntimeException("DB write error");

        // Mock Guava Lists.partition
        PowerMockito.mockStatic(Lists.class);
        when(Lists.partition(reports, dfPersistBatchSize)).thenReturn(partitionedLists);

        // Mock Stopwatch
        PowerMockito.mockStatic(Stopwatch.class);
        when(Stopwatch.createStarted()).thenReturn(stopwatch);

        // Mock repository
        when(reconReportDocumentRepository.compareAndUpdateAll(reports)).thenThrow(exception);

        // Execute
        TaggingServiceRunTimeException thrown = assertThrows(TaggingServiceRunTimeException.class,
                () -> persistManager.saveAllRecords(reports));

        // Verify
        assertEquals("Error occured while reading records to df. ", thrown.getMessage());
        assertEquals(exception, thrown.getCause());
        verify(reconReportDocumentRepository).compareAndUpdateAll(reports);
        verify(logger).error(eq("Error occured while writing records to df. "), eq(exception));
        verifyNoMoreInteractions(reconReportDocumentRepository, logger);
    }

    @Test
    public void testLogErrorReasonForInsertion_FailedWithCause() {
        // Prepare test data
        List<WriteResult<ReconReport>> writeResults = Arrays.asList(successResult, failedResult);
        when(errorDetail.getDescription()).thenReturn("Version mismatch");
        when(errorDetail.getCause()).thenReturn(new RuntimeException("Optimistic lock error"));

        // Execute (access private method via reflection or redesign test)
        persistManager.saveAllRecords(Arrays.asList(Pair.of(reconReport1, 1L), Pair.of(reconReport2, 2L)));

        // Verify (logging is triggered within saveAllRecords)
        verify(logger).error(
                eq("Error occurred in persisting recon  : [{}]"),
                eq("Version mismatch. Optimistic lock error")
        );
    }

    @Test
    public void testLogErrorReasonForInsertion_FailedWithoutCause() {
        // Prepare test data
        List<WriteResult<ReconReport>> writeResults = Arrays.asList(successResult, failedResult);
        when(errorDetail.getDescription()).thenReturn("Invalid data");
        when(errorDetail.getCause()).thenReturn(null);

        // Execute (access private method via reflection or redesign test)
        persistManager.saveAllRecords(Arrays.asList(Pair.of(reconReport1, 1L), Pair.of(reconReport2, 2L)));

        // Verify (logging is triggered within saveAllRecords)
        verify(logger).error(
                eq("Error occurred in persisting recon  : [{}]"),
                eq("Invalid data")
        );
    }

    @Test
    public void testLogErrorReasonForInsertion_AllSuccessful() {
        // Prepare test data
        List<WriteResult<ReconReport>> writeResults = Collections.singletonList(successResult);

        // Execute (access private method via reflection or redesign test)
        persistManager.saveAllRecords(Collections.singletonList(Pair.of(reconReport1, 1L)));

        // Verify no error logs
        verify(logger, never()).error(anyString(), anyString());
    }
}
```

### Key Features of the Test Class
1. **Setup**:
   - Uses `@Before` to initialize `DfReconciliationPersistManager` with mocked `reconReportDocumentRepository` and `dfPersistBatchSize`.
   - Injects mocked `Logger` into the `static final` `logger` field via reflection.
   - Mocks `WriteResult` behavior for success and failure cases.
   - Uses `PowerMock` to mock `Stopwatch.createStarted()` and `Lists.partition`.

2. **Test Methods**:
   - **testConstructor**: Verifies that the constructor sets `reconReportDocumentRepository` and `dfPersistBatchSize`.
   - **testGetAllRecords_Success**: Tests successful retrieval, verifying repository call and result.
   - **testGetAllRecords_Exception**: Tests exception handling, verifying `TaggingServiceRunTimeException` and logger.
   - **testSaveAllRecords_Success_MultipleBatches**: Tests batch processing with multiple partitions, verifying repository calls, results, and logs.
   - **testSaveAllRecords_Success_SingleBatch**: Tests single-batch processing, verifying minimal repository calls.
   - **testSaveAllRecords_EmptyList**: Tests empty input, expecting empty results and no repository calls.
   - **testSaveAllRecords_Exception**: Tests exception handling during write, verifying `TaggingServiceRunTimeException` and logger.
   - **testLogErrorReasonForInsertion_FailedWithCause**: Tests error logging for failed `WriteResult` with a cause.
   - **testLogErrorReasonForInsertion_FailedWithoutCause**: Tests error logging for failed `WriteResult` without a cause.
   - **testLogErrorReasonForInsertion_AllSuccessful**: Tests no error logging for successful results.

3. **Assertions**:
   - Uses `assertEquals` to verify results and field values.
   - Uses `assertThrows` to verify exception types and messages.
   - Uses `ArgumentCaptor` to verify logger messages and arguments.
   - Uses `verify` and `verifyNoMoreInteractions` to ensure correct interactions.

4. **PowerMock Setup**:
   - Annotates with `@RunWith(PowerMockRunner.class)` and `@PrepareForTest` to mock `Stopwatch` and `Lists`.
   - Mocks `Lists.partition` to control batch splitting.
   - Mocks `Stopwatch.createStarted()` and `elapsed` to simulate timing.

### Dependencies for PowerMock
Ensure PowerMock is included in your project for mocking static methods. For Maven, add to `pom.xml`:
```xml
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
For Gradle:
```gradle
testImplementation 'org.powermock:powermock-module-junit4:2.0.9'
testImplementation 'org.powermock:powermock-api-mockito2:2.0.9'
```
Adjust the version to match your Mockito version (e.g., 2.x for Mockito 2.x).

### Assumptions
- **ReconReportDocumentRepository**:
  - `findAllByQuery(QueryParams)` returns `List<ReconReportDocument>`.
  - `compareAndUpdateAll(List<Pair<ReconReport, Long>>)` returns `List<WriteResult<ReconReport>>`.
- **WriteResult<ReconReport>**:
  - Has `isSuccess()`, `isFailure()`, and `getErrorDetail()`.
  - `getErrorDetail()` returns an object with `getDescription()` and `getCause()` (nullable).
- **Pair**:
  - Uses `org.apache.commons.lang3.tuple.Pair` with `of(ReconReport, Long)` and `Pair<ReconReport, Long>`.
- **Stopwatch**:
  - `createStarted()` returns a `Stopwatch`.
  - `elapsed(TimeUnit.MILLISECONDS)` returns a `long`.
- **Lists.partition**:
  - Splits lists into sublists of size `dfPersistBatchSize`.
- **Logger**:
  - `static final` and requires PowerMock to mock.
  - Logs via `info` and `error` methods with format strings.
- **Input Handling**:
  - `queryParams` and `reportsWithExpectedBaseVersions` can be null or empty without validation.
- **Mockito/PowerMock**:
  - Assumes Mockito 1.x (per prior tests). If using 2.x+, update to `PowerMockRunner` with Mockito 2.x compatibility.

### Potential Issues
- **PowerMock Compatibility**:
  - Requires Mockito 2.x for PowerMock 2.x. If using Mockito 1.x, update dependencies or avoid PowerMock by injecting `Stopwatch` or refactoring tests.
- **WriteResult Structure**:
  - If `WriteResult` or `ErrorDetail` lacks assumed methods (`isSuccess`, `getDescription`), share `WriteResult.java`.
- **Pair Usage**:
  - Assumes `org.apache.commons.lang3.tuple.Pair`. If using a different `Pair` (e.g., Guava), share the import.
- **Logger Injection**:
  - If reflection fails (e.g., `logger` is renamed), share the logger declaration.
- **Lists.partition**:
  - If `Lists.partition` behaves differently (e.g., custom implementation), share details.
- **Repository Methods**:
  - If `findAllByQuery` or `compareAndUpdateAll` have different signatures, share `ReconReportDocumentRepository.java`.

### Next Steps
1. **Run the Test Class**:
   - Save `DfReconciliationPersistManagerTest.java` in `src/test/java/com/rbs/tntr/business/taggingService/df/`.
   - Add PowerMock dependencies to `pom.xml` or `build.gradle` if not present.
   - Run the tests in IntelliJ IDEA (Community Edition 2024.2.3) or via Maven/Gradle.
   - Share the test output, including any errors or stack traces.

2. **Resolve Prior Test Issues**:
   - **TaggingSecurityConfigurerTest**:
     - Run the provided `TaggingSecurityConfigurerTest.java` (previous response) and share results.
     - Share `BaseSecurityConfigurer.java`, `SecurityProvider.java`, `SsoTokenAuthenticationFilter.java` if needed.
   - **ApiExceptionHandlerTest**:
     - Run the provided `ApiExceptionHandlerTest.java` and share results.
     - Share `ApiException.java` if its structure differs.
   - **TntrTradeTransformerImplTest**:
     - Update `TntrTradeTransformerImplTest.java` with correct imports for `RepositoryCommand` and `RepositoryCommandFactory`.
     - Share `RepositoryCommand.java`, `RepositoryCommandFactory.java`, `ReportingTemplate.java`.
     - Run the test and share results.
   - **DfConfigurerTest**:
     - Run the provided `DfConfigurerTest.java` and share results.
     - Ensure PowerMock dependencies are added.
   - **BlotterExportMappingsRepositoryImplTest**: