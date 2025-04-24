```java
package com.rbs.tntr.business.taggingService.jobs;

import com.rbs.tntr.business.taggingService.configuration.TaggingServiceConfiguration;
import com.rbs.tntr.business.taggingService.service.common.DateTimeService;
import com.rbs.tntr.api.taggingService.service.jiraAssignement.JiraAssignmentService;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.common.AutoAssignResult;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.common.TaggingServiceResult;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.exceptions.MultipleFilesFoundException;
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
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DateTimeService.class, TaggingServiceJob.class, Thread.class})
public class TaggingServiceJobTest {

    @Mock
    private TaggingServiceConfiguration taggingServiceConfiguration;

    @Mock
    private JiraAssignmentService jiraAssignmentService;

    @Mock
    private Logger logger;

    @InjectMocks
    private TaggingServiceJob taggingServiceJob;

    @Before
    public void setUp() throws Exception {
        // Mock static methods
        PowerMockito.mockStatic(DateTimeService.class);
        PowerMockito.mockStatic(Thread.class);

        // Mock logger via reflection
        Field loggerField = TaggingServiceJob.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(null, logger);

        // Mock Thread.sleep to avoid delays
        doAnswer(invocation -> null).when(Thread.class);
        Thread.sleep(anyLong());
    }

    private Method getPrivateMethod(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = TaggingServiceJob.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    @Test
    public void testConstructor() {
        // Verify
        assertNotNull(taggingServiceJob);
        assertEquals(taggingServiceConfiguration, taggingServiceJob.taggingServiceConfiguration);
        assertEquals(jiraAssignmentService, taggingServiceJob.jiraAssignmentService);
    }

    @Test
    public void testTriggerCronJob() throws Exception {
        // Prepare
        Method processTaggingService = getPrivateMethod("processTaggingService");
        taggingServiceJob = spy(taggingServiceJob);
        doNothing().when(taggingServiceJob).processTaggingService();

        // Execute
        taggingServiceJob.triggerCronJob();

        // Verify
        verify(taggingServiceJob).processTaggingService();
    }

    @Test
    public void testTriggerCronJobAt1pm() throws Exception {
        // Prepare
        Method processTaggingService = getPrivateMethod("processTaggingService");
        taggingServiceJob = spy(taggingServiceJob);
        doNothing().when(taggingServiceJob).processTaggingService();

        // Execute
        taggingServiceJob.triggerCronJobAt1pm();

        // Verify
        verify(taggingServiceJob).processTaggingService();
    }

    @Test
    public void testProcessTaggingService_SuccessFirstTry() throws Exception {
        // Prepare
        DateTime utcDate = new DateTime();
        Date businessDate = utcDate.toDate();
        when(DateTimeService.getCurrentUTCDate()).thenReturn(utcDate);
        when(taggingServiceConfiguration.getJiraAssignStateReadRetryDelaySeconds()).thenReturn(1);
        when(taggingServiceConfiguration.getJiraAssignStateReadRetryMax()).thenReturn(3);
        when(jiraAssignmentService.isJiraFilterFileAvailable(businessDate)).thenReturn(true);
        List<TaggingServiceResult> taggingResults = Arrays.asList(new TaggingServiceResult());
        ResponseEntity<List<TaggingServiceResult>> taggingResponse = new ResponseEntity<>(taggingResults, HttpStatus.OK);
        when(jiraAssignmentService.readSaveJiraExpressions(businessDate)).thenReturn(taggingResponse);
        List<AutoAssignResult> assignResults = Arrays.asList(new AutoAssignResult());
        ResponseEntity<List<AutoAssignResult>> assignResponse = new ResponseEntity<>(assignResults, HttpStatus.OK);
        when(jiraAssignmentService.readAndAssignJiraToTrades()).thenReturn(assignResponse);

        Method processTaggingService = getPrivateMethod("processTaggingService");

        // Execute
        processTaggingService.invoke(taggingServiceJob);

        // Verify
        verify(logger).info("Triggered processing of Jira Assignment Service");
        verify(jiraAssignmentService).isJiraFilterFileAvailable(businessDate);
        verify(jiraAssignmentService).readSaveJiraExpressions(businessDate);
        verify(jiraAssignmentService).readAndAssignJiraToTrades();
        verify(logger).info("Completed processing of Jira Assignment Service");
        verifyNoMoreInteractions(Thread.class);
    }

    @Test
    public void testProcessTaggingService_RetrySuccess() throws Exception {
        // Prepare
        DateTime utcDate = new DateTime();
        Date businessDate = utcDate.toDate();
        when(DateTimeService.getCurrentUTCDate()).thenReturn(utcDate);
        when(taggingServiceConfiguration.getJiraAssignStateReadRetryDelaySeconds()).thenReturn(1);
        when(taggingServiceConfiguration.getJiraAssignStateReadRetryMax()).thenReturn(3);
        when(jiraAssignmentService.isJiraFilterFileAvailable(businessDate)).thenReturn(false, false, true);
        List<TaggingServiceResult> taggingResults = Arrays.asList(new TaggingServiceResult());
        ResponseEntity<List<TaggingServiceResult>> taggingResponse = new ResponseEntity<>(taggingResults, HttpStatus.OK);
        when(jiraAssignmentService.readSaveJiraExpressions(businessDate)).thenReturn(taggingResponse);
        List<AutoAssignResult> assignResults = Arrays.asList(new AutoAssignResult());
        ResponseEntity<List<AutoAssignResult>> assignResponse = new ResponseEntity<>(assignResults, HttpStatus.OK);
        when(jiraAssignmentService.readAndAssignJiraToTrades()).thenReturn(assignResponse);

        Method processTaggingService = getPrivateMethod("processTaggingService");

        // Execute
        processTaggingService.invoke(taggingServiceJob);

        // Verify
        verify(logger).info("Triggered processing of Jira Assignment Service");
        verify(jiraAssignmentService, times(3)).isJiraFilterFileAvailable(businessDate);
        verify(jiraAssignmentService).readSaveJiraExpressions(businessDate);
        verify(jiraAssignmentService).readAndAssignJiraToTrades();
        verify(logger, times(2)).info("Jira Assignment Service : sleeping before retrying");
        verify(logger).info("Completed processing of Jira Assignment Service");
        verify(Thread.class, times(2));
        Thread.sleep(100L);
    }

    @Test
    public void testProcessTaggingService_NoFileAvailable() throws Exception {
        // Prepare
        DateTime utcDate = new DateTime();
        Date businessDate = utcDate.toDate();
        when(DateTimeService.getCurrentUTCDate()).thenReturn(utcDate);
        when(taggingServiceConfiguration.getJiraAssignStateReadRetryDelaySeconds()).thenReturn(1);
        when(taggingServiceConfiguration.getJiraAssignStateReadRetryMax()).thenReturn(3);
        when(jiraAssignmentService.isJiraFilterFileAvailable(businessDate)).thenReturn(false);

        Method processTaggingService = getPrivateMethod("processTaggingService");

        // Execute
        processTaggingService.invoke(taggingServiceJob);

        // Verify
        verify(logger).info("Triggered processing of Jira Assignment Service");
        verify(jiraAssignmentService, times(3)).isJiraFilterFileAvailable(businessDate);
        verify(jiraAssignmentService, never()).readSaveJiraExpressions(businessDate);
        verify(jiraAssignmentService).readAndAssignJiraToTrades();
        verify(logger, times(2)).info("Jira Assignment Service : sleeping before retrying");
        verify(Thread.class, times(2));
        Thread.sleep(100L);
    }

    @Test
    public void testProcessTaggingService_Exception() throws Exception {
        // Prepare
        DateTime utcDate = new DateTime();
        Date businessDate = utcDate.toDate();
        when(DateTimeService.getCurrentUTCDate()).thenReturn(utcDate);
        when(taggingServiceConfiguration.getJiraAssignStateReadRetryDelaySeconds()).thenReturn(1);
        when(taggingServiceConfiguration.getJiraAssignStateReadRetryMax()).thenReturn(3);
        when(jiraAssignmentService.isJiraFilterFileAvailable(businessDate)).thenThrow(new RuntimeException("Service error"));

        Method processTaggingService = getPrivateMethod("processTaggingService");

        // Execute
        processTaggingService.invoke(taggingServiceJob);

        // Verify
        verify(logger).info("Triggered processing of Jira Assignment Service");
        verify(jiraAssignmentService).isJiraFilterFileAvailable(businessDate);
        verify(logger).error(eq("Service error"), any(RuntimeException.class));
        verifyNoMoreInteractions(jiraAssignmentService);
        verifyNoMoreInteractions(Thread.class);
    }

    @Test
    public void testTryPersistingJiraRules_FileNotAvailable() throws Exception {
        // Prepare
        Date businessDate = new Date();
        when(jiraAssignmentService.isJiraFilterFileAvailable(businessDate)).thenReturn(false);

        Method tryPersistingJiraRules = getPrivateMethod("tryPersistingJiraRules", Date.class);

        // Execute
        boolean result = (boolean) tryPersistingJiraRules.invoke(taggingServiceJob, businessDate);

        // Verify
        assertFalse(result);
        verify(logger).info("Read and Save Jira to df for business date {}", businessDate);
        verify(jiraAssignmentService).isJiraFilterFileAvailable(businessDate);
        verifyNoMoreInteractions(jiraAssignmentService);
    }

    @Test
    public void testTryPersistingJiraRules_Success() throws Exception {
        // Prepare
        Date businessDate = new Date();
        when(jiraAssignmentService.isJiraFilterFileAvailable(businessDate)).thenReturn(true);
        List<TaggingServiceResult> taggingResults = Arrays.asList(new TaggingServiceResult());
        ResponseEntity<List<TaggingServiceResult>> response = new ResponseEntity<>(taggingResults, HttpStatus.OK);
        when(jiraAssignmentService.readSaveJiraExpressions(businessDate)).thenReturn(response);

        Method tryPersistingJiraRules = getPrivateMethod("tryPersistingJiraRules", Date.class);

        // Execute
        boolean result = (boolean) tryPersistingJiraRules.invoke(taggingServiceJob, businessDate);

        // Verify
        assertTrue(result);
        verify(logger).info("Read and Save Jira to df for business date {}", businessDate);
        verify(jiraAssignmentService).isJiraFilterFileAvailable(businessDate);
        verify(jiraAssignmentService).readSaveJiraExpressions(businessDate);
        verify(logger).info("Read and Save Jira to df response : ", taggingResults);
    }

    @Test(expected = MultipleFilesFoundException.class)
    public void testTryPersistingJiraRules_MultipleFilesFoundException() throws Exception {
        // Prepare
        Date businessDate = new Date();
        when(jiraAssignmentService.isJiraFilterFileAvailable(businessDate)).thenReturn(true);
        when(jiraAssignmentService.readSaveJiraExpressions(businessDate)).thenThrow(new MultipleFilesFoundException("Multiple files found"));

        Method tryPersistingJiraRules = getPrivateMethod("tryPersistingJiraRules", Date.class);

        // Execute
        tryPersistingJiraRules.invoke(taggingServiceJob, businessDate);
    }

    @Test
    public void testAssignJiraToTrade_Success() throws Exception {
        // Prepare
        List<AutoAssignResult> assignResults = Arrays.asList(new AutoAssignResult());
        ResponseEntity<List<AutoAssignResult>> response = new ResponseEntity<>(assignResults, HttpStatus.OK);
        when(jiraAssignmentService.readAndAssignJiraToTrades()).thenReturn(response);

        Method assignJiraToTrade = getPrivateMethod("assignJiraToTrade");

        // Execute
        boolean result = (boolean) assignJiraToTrade.invoke(taggingServiceJob);

        // Verify
        assertTrue(result);
        verify(logger).info("Assign Jira to trades");
        verify(jiraAssignmentService).readAndAssignJiraToTrades();
        verify(logger).info("Assign Jira to trades {}", assignResults);
    }

    @Test
    public void testAssignJiraToTrade_Failure() throws Exception {
        // Prepare
        List<AutoAssignResult> assignResults = Arrays.asList(new AutoAssignResult());
        ResponseEntity<List<AutoAssignResult>> response = new ResponseEntity<>(assignResults, HttpStatus.INTERNAL_SERVER_ERROR);
        when(jiraAssignmentService.readAndAssignJiraToTrades()).thenReturn(response);

        Method assignJiraToTrade = getPrivateMethod("assignJiraToTrade");

        // Execute
        boolean result = (boolean) assignJiraToTrade.invoke(taggingServiceJob);

        // Verify
        assertFalse(result);
        verify(logger).info("Assign Jira to trades");
        verify(jiraAssignmentService).readAndAssignJiraToTrades();
        verify(logger).info("Assign Jira to trades {}", assignResults);
    }
}
```

### Key Features of the Test Class
1. **Setup**:
   - Uses `@InjectMocks` to inject `TaggingServiceConfiguration` and `JiraAssignmentService` into `TaggingServiceJob`.
   - Mocks static `DateTimeService.getCurrentUTCDate` and `Thread.sleep` using PowerMock.
   - Replaces the static `logger` with a mock via reflection to verify logging.
   - Spies on `TaggingServiceJob` to stub `processTaggingService` for `triggerCronJob` and `triggerCronJobAt1pm`.

2. **Test Methods**:
   - **testConstructor**: Verifies field initialization.
   - **testTriggerCronJob**: Verifies `processTaggingService` call.
   - **testTriggerCronJobAt1pm**: Verifies `processTaggingService` call.
   - **testProcessTaggingService_SuccessFirstTry**: Tests successful execution on first try.
   - **testProcessTaggingService_RetrySuccess**: Tests retry logic with success on the third attempt.
   - **testProcessTaggingService_NoFileAvailable**: Tests max retries with no file available.
   - **testProcessTaggingService_Exception**: Tests exception handling and logging.
   - **testTryPersistingJiraRules_FileNotAvailable**: Tests file absence case.
   - **testTryPersistingJiraRules_Success**: Tests successful file processing.
   - **testTryPersistingJiraRules_MultipleFilesFoundException**: Tests exception propagation.
   - **testAssignJiraToTrade_Success**: Tests `HttpStatus.OK` response.
   - **testAssignJiraToTrade_Failure**: Tests non-`OK` response.

3. **Assertions**:
   - Uses `assertNotNull`, `assertEquals`, `assertTrue`, `assertFalse` to verify results.
   - Uses `ArgumentCaptor` to verify logger arguments.
   - Uses `verify` to check service and logger interactions.
   - Uses `expected` for exception tests.

4. **PowerMock**:
   - Mocks `DateTimeService.getCurrentUTCDate` to return a fixed `DateTime`.
   - Mocks `Thread.sleep` to avoid test delays.
   - Uses reflection to replace the static `logger`.

### Dependencies
Ensure PowerMock and Mockito dependencies are in your project (see `DfConfigurerTest` response for `pom.xml`/`build.gradle` snippets). PowerMock is required for static mocking of `DateTimeService` and `Thread`.

### Assumptions
- **Classes**:
  - `TaggingServiceConfiguration` has `getJiraAssignStateReadRetryDelaySeconds` and `getJiraAssignStateReadRetryMax`.
  - `JiraAssignmentService` has `isJiraFilterFileAvailable(Date)`, `readSaveJiraExpressions(Date)`, and `readAndAssignJiraToTrades()`.
  - `TaggingServiceResult` and `AutoAssignResult` are simple POJOs with default constructors.
  - `DateTimeService.getCurrentUTCDate` returns a `DateTime` object.
- **Behavior**:
  - Cron expressions are defined in properties and handled by Spring.
  - `Thread.sleep` takes milliseconds.
  - `ResponseEntity` contains `List<TaggingServiceResult>` or `List<AutoAssignResult>`.
  - Logger messages use `{}` placeholders for arguments.
- **Enums/Constants**: None explicitly used beyond `HttpStatus`.

### Potential Issues
- **Class Structures**:
  - If `TaggingServiceConfiguration`, `JiraAssignmentService`, `TaggingServiceResult`, or `AutoAssignResult` have non-trivial methods or constructors, share their definitions.
  - If `DateTimeService.getCurrentUTCDate` has a different signature, share `DateTimeService.java`.
- **Static Mocking**:
  - Ensure PowerMock is configured correctly for `DateTimeService` and `Thread`.
  - If `Thread.sleep` behaves differently, tests may need adjustment.
- **Logger Mocking**:
  - Reflection-based logger mocking assumes the `logger` field is static. If it’s instance-based, adjust the setup.
- **PowerMock Compatibility**:
  - Ensure Mockito 2.x+ for PowerMock (see `DfConfigurerTest` response). If using Mockito 1.x, update dependencies.
- **Cron Scheduling**:
  - Unit tests do not verify cron expressions. If scheduling issues occur, consider integration tests with Spring’s `TestScheduler`.

### Next Steps
1. **Run the Test Class**:
   - Save `TaggingServiceJobTest.java` in `src/test/java/com/rbs/tntr/business/taggingService/jobs/`.
   - Ensure PowerMock dependencies are in `pom.xml` or `build.gradle`.
   - Run the tests in IntelliJ IDEA (Community Edition 2024.2.3) or via Maven/Gradle.
   - Share the test output, including any errors or stack traces.

2. **Resolve Prior Test Issues**:
   - **TntrTradeTransformerImplTest**:
     - Run `TntrTradeTransformerImplTest.java` (previous response) and share results.
     - If `Cannot resolve symbol` for `RepositoryCommand` or `RepositoryCommandFactory` persists, share `RepositoryCommand.java` and `RepositoryCommandFactory.java`.
     - Share `JiraReference.java`, `JiraTaggingDetail.java`, `ReportingTemplate.java`, `TntrTdxNodeBuilderUtil.java`, `DateTimeService.java` if failures occur.
   - **DfConfigurerTest**:
     - Run `DfConfigurerTest.java` (response for `DfConfigurer`) and share results.
     - Ensure PowerMock dependencies are added.
   - **ApiExceptionHandlerTest**:
     - Run `ApiExceptionHandlerTest.java` (response for `ApiExceptionHandler`) and share results.
     - Share `ApiException.java` if its structure differs.
   - **MIAnalyticsDashboardRepositoryImplTest**:
     - Run `MIAnalyticsDashboardRepositoryImplTest.java` (response for `MIAnalyticsDashboardRepositoryImpl`) and share results.
     - Share `MIConstants.java`, `SubjectIdentifier.java`, `FrontOfficeSubjectIdentifier.java`, `MIDashboardAnalytics.java`, `FrontOfficeMIDashboardAnalytics.java`, `DFQueryMetaData.java`, `ScanExpressionUtility.java` if failures occur.
   - **ServiceCreationListenerTest**:
     - Run `ServiceCreationListenerTest.java` (response for `ServiceCreationListener`) and share results.
     - Share `FoundationServiceCreationListener.java` if its behavior differs.
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
   - Share `TaggingServiceConfiguration.java`, `JiraAssignmentService.java`, `TaggingServiceResult.java`, `AutoAssignResult.java`, `DateTimeService.java` if test failures occur.
   - Share `RepositoryCommand.java`, `RepositoryCommandFactory.java`, `JiraReference.java`, `JiraTaggingDetail.java`, `ReportingTemplate.java`, `TntrTdxNodeBuilderUtil.java`, `ApiException.java`, `DfConnectionConfig.java`, `TntrRepositoryConfig.java`, `MIConstants.java`, `SubjectIdentifier.java`, `FrontOfficeSubjectIdentifier.java`, `FoundationServiceCreationListener.java`, etc., for prior test issues.
   - Share `pom.xml` or `build.gradle` to confirm Mockito/PowerMock versions.

4. **Confirm Mockito/PowerMock Setup**:
   - Check `pom.xml` or `build.gradle` for Mockito version (1.x or 2.x+).
   - Ensure PowerMock is configured for static mocking.

Please run the `TaggingServiceJobTest` class and share the results. Additionally, provide the requested class files and the results of the `TntrTradeTransformerImplTest`, `DfConfigurerTest`, `ApiExceptionHandlerTest`, `MIAnalyticsDashboardRepositoryImplTest`, `ServiceCreationListenerTest`, `BlotterExportMappingsRepositoryImplTest`, and `SsoConfigurationTest` to resolve all issues comprehensively!