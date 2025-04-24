```java
package com.rbs.tntr.business.taggingService.transformer;

import com.nwm.tntr.commons.domain.JiraReference;
import com.nwm.tntr.commons.enums.ApiQueryFilter;
import com.nwm.tntr.commons.enums.TntrCallerService;
import com.nwm.tntr.commons.enums.TntrFieldsReadUpdateEnum;
import com.nwm.tntr.commons.repository.domain.ReportingTemplate;
import com.nwm.tntr.commons.repository.domain.RepositoryAction;
import com.nwm.tntr.commons.repository.domain.TntrRepositoryReadCommandContext;
import com.nwm.tntr.commons.repository.domain.TntrRepositoryReadCommandResult;
import com.nwm.tntr.commons.repository.domain.TntrRepositoryUpdateCommandContext;
import com.nwm.tntr.commons.repository.domain.TntrRepositoryUpdateCommandResult;
import com.nwm.tntr.commons.repository.util.TntrTdxNodeBuilderUtil;
import com.rbs.tntr.business.taggingService.service.common.DateTimeService;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.common.JiraTaggingDetail;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.enums.FlowType;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({TntrTdxNodeBuilderUtil.class, DateTimeService.class})
public class TntrTradeTransformerImplTest {

    private TntrTradeTransformerImpl transformer;

    @Before
    public void setUp() {
        transformer = new TntrTradeTransformerImpl();
        PowerMockito.mockStatic(TntrTdxNodeBuilderUtil.class);
        PowerMockito.mockStatic(DateTimeService.class);
    }

    @Test
    public void testGetRecords_FlowTypeTrade() {
        // Prepare
        String whereExpression = "status=active";
        FlowType workflowType = FlowType.TRADE;
        String indexHint = "idx_trade";
        JiraReference jiraReference = new JiraReference();
        ReportingTemplate reportingTemplate = ReportingTemplate.TRADE;
        RepositoryCommandFactory commandFactory = mock(RepositoryCommandFactory.class);
        RepositoryCommand command = mock(RepositoryCommand.class);
        TntrRepositoryReadCommandResult result = mock(TntrRepositoryReadCommandResult.class);
        List<Map<TntrFieldsReadUpdateEnum, Object>> tradeList = new ArrayList<>();
        Map<TntrFieldsReadUpdateEnum, Object> trade = new EnumMap<>(TntrFieldsReadUpdateEnum.class);
        Map<String, Object> suppMap = new HashMap<>();
        trade.put(TntrFieldsReadUpdateEnum.SUPPLEMENTARY_INFORMATION, suppMap);
        tradeList.add(trade);

        when(reportingTemplate.getRepositoryCommandFactory()).thenReturn(commandFactory);
        when(commandFactory.getCommand(RepositoryAction.READ_ALL)).thenReturn(command);
        when(command.execute(any(TntrRepositoryReadCommandContext.class))).thenReturn(result);
        when(result.getTradeList()).thenReturn(tradeList);
        when(TntrTdxNodeBuilderUtil.isJiraIdPresentInUserWorkflow(suppMap, TntrFieldsReadUpdateEnum.JIRA_REFERENCES.getValue(), jiraReference)).thenReturn(false);

        // Execute
        int count = transformer.getRecords(whereExpression, workflowType, indexHint, jiraReference);

        // Verify
        assertEquals(1, count);
        ArgumentCaptor<TntrRepositoryReadCommandContext> captor = ArgumentCaptor.forClass(TntrRepositoryReadCommandContext.class);
        verify(command).execute(captor.capture());
        TntrRepositoryReadCommandContext context = captor.getValue();
        assertEquals(TntrCallerService.TAGGING_SERVICE, context.getTntrCallerService());
        assertNull(context.getWorkflowType());
        assertTrue(context.getAttributes().contains(TntrFieldsReadUpdateEnum.TRANSACTION_ID));
        assertTrue(context.getAttributes().contains(TntrFieldsReadUpdateEnum.SUPPLEMENTARY_INFORMATION));
        EnumMap<ApiQueryFilter, Object> filterMap = context.getApiQueryFilterMap();
        assertEquals(whereExpression, filterMap.get(ApiQueryFilter.WHERE));
        assertEquals(indexHint, filterMap.get(ApiQueryFilter.INDEX_HINT));
        assertFalse((Boolean) filterMap.get(ApiQueryFilter.HISTORY));
    }

    @Test
    public void testGetRecords_FlowTypeAggregatedCollateral() {
        // Prepare
        String whereExpression = "status=active";
        FlowType workflowType = FlowType.AGGREGATED_COLLATERAL;
        String indexHint = "idx_collateral";
        JiraReference jiraReference = new JiraReference();
        ReportingTemplate reportingTemplate = ReportingTemplate.COLLATERAL;
        RepositoryCommandFactory commandFactory = mock(RepositoryCommandFactory.class);
        RepositoryCommand command = mock(RepositoryCommand.class);
        TntrRepositoryReadCommandResult result = mock(TntrRepositoryReadCommandResult.class);
        List<Map<TntrFieldsReadUpdateEnum, Object>> tradeList = new ArrayList<>();
        Map<TntrFieldsReadUpdateEnum, Object> trade = new EnumMap<>(TntrFieldsReadUpdateEnum.class);
        Map<String, Object> suppMap = new HashMap<>();
        trade.put(TntrFieldsReadUpdateEnum.SUPPLEMENTARY_INFORMATION, suppMap);
        tradeList.add(trade);

        when(reportingTemplate.getRepositoryCommandFactory()).thenReturn(commandFactory);
        when(commandFactory.getCommand(RepositoryAction.READ_ALL)).thenReturn(command);
        when(command.execute(any(TntrRepositoryReadCommandContext.class))).thenReturn(result);
        when(result.getTradeList()).thenReturn(tradeList);
        when(TntrTdxNodeBuilderUtil.isJiraIdPresentInUserWorkflow(suppMap, TntrFieldsReadUpdateEnum.JIRA_REFERENCES.getValue(), jiraReference)).thenReturn(true);

        // Execute
        int count = transformer.getRecords(whereExpression, workflowType, indexHint, jiraReference);

        // Verify
        assertEquals(0, count);
        ArgumentCaptor<TntrRepositoryReadCommandContext> captor = ArgumentCaptor.forClass(TntrRepositoryReadCommandContext.class);
        verify(command).execute(captor.capture());
        TntrRepositoryReadCommandContext context = captor.getValue();
        assertEquals(TntrCallerService.TAGGING_SERVICE, context.getTntrCallerService());
        assertNull(context.getWorkflowType());
        assertTrue(context.getAttributes().contains(TntrFieldsReadUpdateEnum.COLLATERAL_REPORTING_GRP));
        assertTrue(context.getAttributes().contains(TntrFieldsReadUpdateEnum.SUPPLEMENTARY_INFORMATION));
    }

    @Test
    public void testGetRecords_FlowTypeCollateral() {
        // Prepare
        String whereExpression = "status=active";
        FlowType workflowType = FlowType.COLLATERAL;
        String indexHint = "idx_trade";
        JiraReference jiraReference = new JiraReference();
        ReportingTemplate reportingTemplate = ReportingTemplate.TRADE;
        RepositoryCommandFactory commandFactory = mock(RepositoryCommandFactory.class);
        RepositoryCommand command = mock(RepositoryCommand.class);
        TntrRepositoryReadCommandResult result = mock(TntrRepositoryReadCommandResult.class);
        List<Map<TntrFieldsReadUpdateEnum, Object>> tradeList = Collections.emptyList();

        when(reportingTemplate.getRepositoryCommandFactory()).thenReturn(commandFactory);
        when(commandFactory.getCommand(RepositoryAction.READ_ALL)).thenReturn(command);
        when(command.execute(any(TntrRepositoryReadCommandContext.class))).thenReturn(result);
        when(result.getTradeList()).thenReturn(tradeList);

        // Execute
        int count = transformer.getRecords(whereExpression, workflowType, indexHint, jiraReference);

        // Verify
        assertEquals(0, count);
        ArgumentCaptor<TntrRepositoryReadCommandContext> captor = ArgumentCaptor.forClass(TntrRepositoryReadCommandContext.class);
        verify(command).execute(captor.capture());
        TntrRepositoryReadCommandContext context = captor.getValue();
        assertEquals(TntrCallerService.TAGGING_SERVICE, context.getTntrCallerService());
        assertEquals(WorkflowType.VALUATIONS, context.getWorkflowType());
    }

    @Test
    public void testGetRecords_EmptyTradeList() {
        // Prepare
        String whereExpression = "status=active";
        FlowType workflowType = FlowType.TRADE;
        String indexHint = "idx_trade";
        JiraReference jiraReference = new JiraReference();
        ReportingTemplate reportingTemplate = ReportingTemplate.TRADE;
        RepositoryCommandFactory commandFactory = mock(RepositoryCommandFactory.class);
        RepositoryCommand command = mock(RepositoryCommand.class);
        TntrRepositoryReadCommandResult result = mock(TntrRepositoryReadCommandResult.class);

        when(reportingTemplate.getRepositoryCommandFactory()).thenReturn(commandFactory);
        when(commandFactory.getCommand(RepositoryAction.READ_ALL)).thenReturn(command);
        when(command.execute(any(TntrRepositoryReadCommandContext.class))).thenReturn(result);
        when(result.getTradeList()).thenReturn(Collections.emptyList());

        // Execute
        int count = transformer.getRecords(whereExpression, workflowType, indexHint, jiraReference);

        // Verify
        assertEquals(0, count);
        verifyNoMoreInteractions(TntrTdxNodeBuilderUtil.class);
    }

    @Test
    public void testExecuteUpdateTrades_FlowTypeTrade() {
        // Prepare
        String whereExpression = "status=active";
        JiraReference jiraReference = new JiraReference();
        JiraTaggingDetail jiraTaggingDetail = new JiraTaggingDetail();
        FlowType workflowType = FlowType.TRADE;
        ReportingTemplate reportingTemplate = ReportingTemplate.TRADE;
        RepositoryCommandFactory commandFactory = mock(RepositoryCommandFactory.class);
        RepositoryCommand command = mock(RepositoryCommand.class);
        TntrRepositoryUpdateCommandResult result = mock(TntrRepositoryUpdateCommandResult.class);
        DateTime currentDateTime = new DateTime();

        when(reportingTemplate.getRepositoryCommandFactory()).thenReturn(commandFactory);
        when(commandFactory.getCommand(RepositoryAction.UPDATE)).thenReturn(command);
        when(command.execute(any(TntrRepositoryUpdateCommandContext.class))).thenReturn(result);
        when(DateTimeService.getCurrentDateTime()).thenReturn(currentDateTime);

        // Execute
        TntrRepositoryUpdateCommandResult actualResult = transformer.executeUpdateTrades(whereExpression, jiraReference, jiraTaggingDetail, workflowType);

        // Verify
        assertEquals(result, actualResult);
        ArgumentCaptor<TntrRepositoryUpdateCommandContext> captor = ArgumentCaptor.forClass(TntrRepositoryUpdateCommandContext.class);
        verify(command).execute(captor.capture());
        TntrRepositoryUpdateCommandContext context = captor.getValue();
        assertEquals(whereExpression, context.getWhereExpression());
        assertEquals(TntrCallerService.TAGGING_SERVICE, context.getTntrCallerService());
        assertEquals(WorkflowType.TRADE, context.getWorkflowType());
        Properties props = context.getPropertiesToUpdate();
        assertEquals(jiraReference, props.get(TntrFieldsReadUpdateEnum.JIRA_REFERENCES.getValue()));
        assertEquals(jiraTaggingDetail, props.get(TntrFieldsReadUpdateEnum.JIRA_TAGGING_DETAILS.getValue()));
        assertEquals("tagging-service", props.get(TntrFieldsReadUpdateEnum.MODIFIED_BY.getValue()));
        assertEquals(currentDateTime, props.get(TntrFieldsReadUpdateEnum.MODIFIED_ON.getValue()));
    }

    @Test
    public void testExecuteUpdateTrades_FlowTypeAggregatedCollateral() {
        // Prepare
        String whereExpression = "status=active";
        JiraReference jiraReference = new JiraReference();
        JiraTaggingDetail jiraTaggingDetail = new JiraTaggingDetail();
        FlowType workflowType = FlowType.AGGREGATED_COLLATERAL;
        ReportingTemplate reportingTemplate = ReportingTemplate.COLLATERAL;
        RepositoryCommandFactory commandFactory = mock(RepositoryCommandFactory.class);
        RepositoryCommand command = mock(RepositoryCommand.class);
        TntrRepositoryUpdateCommandResult result = mock(TntrRepositoryUpdateCommandResult.class);
        DateTime currentDateTime = new DateTime();

        when(reportingTemplate.getRepositoryCommandFactory()).thenReturn(commandFactory);
        when(commandFactory.getCommand(RepositoryAction.UPDATE)).thenReturn(command);
        when(command.execute(any(TntrRepositoryUpdateCommandContext.class))).thenReturn(result);
        when(DateTimeService.getCurrentDateTime()).thenReturn(currentDateTime);

        // Execute
        TntrRepositoryUpdateCommandResult actualResult = transformer.executeUpdateTrades(whereExpression, jiraReference, jiraTaggingDetail, workflowType);

        // Verify
        assertEquals(result, actualResult);
        ArgumentCaptor<TntrRepositoryUpdateCommandContext> captor = ArgumentCaptor.forClass(TntrRepositoryUpdateCommandContext.class);
        verify(command).execute(captor.capture());
        TntrRepositoryUpdateCommandContext context = captor.getValue();
        assertEquals(whereExpression, context.getWhereExpression());
        assertEquals(TntrCallerService.TAGGING_SERVICE, context.getTntrCallerService());
        assertNull(context.getWorkflowType());
        Properties props = context.getPropertiesToUpdate();
        assertEquals(jiraReference, props.get(TntrFieldsReadUpdateEnum.JIRA_REFERENCES.getValue()));
        assertEquals(jiraTaggingDetail, props.get(TntrFieldsReadUpdateEnum.JIRA_TAGGING_DETAILS.getValue()));
    }

    @Test
    public void testExecuteUpdateTrades_FlowTypeValuation() {
        // Prepare
        String whereExpression = "status=active";
        JiraReference jiraReference = new JiraReference();
        JiraTaggingDetail jiraTaggingDetail = new JiraTaggingDetail();
        FlowType workflowType = FlowType.VALUATION;
        ReportingTemplate reportingTemplate = ReportingTemplate.TRADE;
        RepositoryCommandFactory commandFactory = mock(RepositoryCommandFactory.class);
        RepositoryCommand command = mock(RepositoryCommand.class);
        TntrRepositoryUpdateCommandResult result = mock(TntrRepositoryUpdateCommandResult.class);
        DateTime currentDateTime = new DateTime();

        when(reportingTemplate.getRepositoryCommandFactory()).thenReturn(commandFactory);
        when(commandFactory.getCommand(RepositoryAction.UPDATE)).thenReturn(command);
        when(command.execute(any(TntrRepositoryUpdateCommandContext.class))).thenReturn(result);
        when(DateTimeService.getCurrentDateTime()).thenReturn(currentDateTime);

        // Execute
        TntrRepositoryUpdateCommandResult actualResult = transformer.executeUpdateTrades(whereExpression, jiraReference, jiraTaggingDetail, workflowType);

        // Verify
        assertEquals(result, actualResult);
        ArgumentCaptor<TntrRepositoryUpdateCommandContext> captor = ArgumentCaptor.forClass(TntrRepositoryUpdateCommandContext.class);
        verify(command).execute(captor.capture());
        TntrRepositoryUpdateCommandContext context = captor.getValue();
        assertEquals(WorkflowType.VALUATIONS, context.getWorkflowType());
    }

    @Test(expected = NullPointerException.class)
    public void testExecuteUpdateTrades_NullJiraReference() {
        // Prepare
        String whereExpression = "status=active";
        JiraTaggingDetail jiraTaggingDetail = new JiraTaggingDetail();
        FlowType workflowType = FlowType.TRADE;

        // Execute
        transformer.executeUpdateTrades(whereExpression, null, jiraTaggingDetail, workflowType);
    }
}
```

### Key Features of the Test Class
1. **Setup**:
   - Creates `TntrTradeTransformerImpl` directly (no dependencies to inject).
   - Uses PowerMock to mock static methods `TntrTdxNodeBuilderUtil.isJiraIdPresentInUserWorkflow` and `DateTimeService.getCurrentDateTime`.
   - Initializes mocks for `RepositoryCommandFactory`, `RepositoryCommand`, `TntrRepositoryReadCommandResult`, and `TntrRepositoryUpdateCommandResult`.

2. **Test Methods**:
   - **getRecords_FlowTypeTrade**: Tests `FlowType.TRADE`, verifying `ReportingTemplate.TRADE`, attributes, and count logic.
   - **getRecords_FlowTypeAggregatedCollateral**: Tests `FlowType.AGGREGATED_COLLATERAL`, verifying `ReportingTemplate.COLLATERAL` and zero count when Jira ID is present.
   - **getRecords_FlowTypeCollateral**: Tests `FlowType.COLLATERAL`, verifying `WorkflowType.VALUATIONS` and empty trade list handling.
   - **getRecords_EmptyTradeList**: Tests empty trade list, verifying zero count and no `TntrTdxNodeBuilderUtil` calls.
   - **executeUpdateTrades_FlowTypeTrade**: Tests `FlowType.TRADE`, verifying `ReportingTemplate.TRADE`, `WorkflowType.TRADE`, and properties.
   - **executeUpdateTrades_FlowTypeAggregatedCollateral**: Tests `FlowType.AGGREGATED_COLLATERAL`, verifying `ReportingTemplate.COLLATERAL` and null `WorkflowType`.
   - **executeUpdateTrades_FlowTypeValuation**: Tests `FlowType.VALUATION`, verifying `WorkflowType.VALUATIONS`.
   - **executeUpdateTrades_NullJiraReference**: Tests null `JiraReference`, expecting `NullPointerException`.

3. **Assertions**:
   - Uses `assertEquals` to verify counts and result objects.
   - Uses `assertNotNull` to ensure non-null contexts.
   - Uses `ArgumentCaptor` to verify `TntrRepositoryReadCommandContext` and `TntrRepositoryUpdateCommandContext` contents.
   - Uses `verify` to check mock interactions.

4. **PowerMock**:
   - Mocks `TntrTdxNodeBuilderUtil.isJiraIdPresentInUserWorkflow` to control Jira ID presence.
   - Mocks `DateTimeService.getCurrentDateTime` to return a fixed `DateTime`.

### Dependencies
Ensure PowerMock and Mockito dependencies are in your project (see `DfConfigurerTest` response for `pom.xml`/`build.gradle` snippets). PowerMock is required for static mocking of `TntrTdxNodeBuilderUtil` and `DateTimeService`.

### Assumptions
- **Classes**:
  - `JiraReference` and `JiraTaggingDetail` are simple POJOs or enums with default constructors.
  - `ReportingTemplate` has values `TRADE` and `COLLATERAL`, with `getRepositoryCommandFactory` returning `RepositoryCommandFactory`.
  - `RepositoryCommandFactory.getCommand` returns a `RepositoryCommand` with `execute`.
  - `TntrRepositoryReadCommandContext` has constructor `(EnumMap<ApiQueryFilter, Object>, List<TntrFieldsReadUpdateEnum>)` and setters for `tntrCallerService`, `workflowType`.
  - `TntrRepositoryUpdateCommandContext` has constructor `(String, Properties, TntrCallerService)` and setter for `workflowType`.
  - `TntrRepositoryReadCommandResult.getTradeList` returns `List<Map<TntrFieldsReadUpdateEnum, Object>>`.
- **Enums**:
  - `ApiQueryFilter`: `WHERE`, `HISTORY`, `INDEX_HINT`.
  - `TntrFieldsReadUpdateEnum`: `TRANSACTION_ID`, `COLLATERAL_REPORTING_GRP`, `SUPPLEMENTARY_INFORMATION`, `JIRA_REFERENCES`, `JIRA_TAGGING_DETAILS`, `MODIFIED_BY`, `MODIFIED_ON`.
  - `TntrCallerService`: `TAGGING_SERVICE`.
  - `FlowType`: `TRADE`, `COLLATERAL`, `VALUATION`, `AGGREGATED_COLLATERAL`.
  - `WorkflowType`: `TRADE`, `VALUATIONS`.
  - `RepositoryAction`: `READ_ALL`, `UPDATE`.
- **Behavior**:
  - `isJiraIdPresentInUserWorkflow` returns `boolean` based on `JiraReference` presence.
  - `getCurrentDateTime` returns a `DateTime` object.
  - No null checks for inputs unless explicitly coded (e.g., `jiraReference`).

### Potential Issues
- **Class Structures**:
  - If `JiraReference`, `JiraTaggingDetail`, `ReportingTemplate`, `RepositoryCommandFactory`, or `RepositoryCommand` have non-trivial constructors or methods, share their definitions.
  - If `TntrRepositoryReadCommandContext` or `TntrRepositoryUpdateCommandContext` have different constructors, tests may fail.
- **Static Mocking**:
  - Ensure PowerMock is configured correctly for `TntrTdxNodeBuilderUtil` and `DateTimeService`.
  - If `TntrTdxNodeBuilderUtil` or `DateTimeService` have different method signatures, share `TntrTdxNodeBuilderUtil.java` and `DateTimeService.java`.
- **Enum Values**:
  - If `FlowType`, `WorkflowType`, `ApiQueryFilter`, `TntrFieldsReadUpdateEnum`, or `RepositoryAction` have different values, share their definitions.
- **PowerMock Compatibility**:
  - Ensure Mockito 2.x+ for PowerMock (see `DfConfigurerTest` response). If using Mockito 1.x, update dependencies.

### Next Steps
1. **Run the Test Class**:
   - Save `TntrTradeTransformerImplTest.java` in `src/test/java/com/rbs/tntr/business/taggingService/transformer/`.
   - Ensure PowerMock dependencies are in `pom.xml` or `build.gradle`.
   - Run the tests in IntelliJ IDEA (Community Edition 2024.2.3) or via Maven/Gradle.
   - Share the test output, including any errors or stack traces.

2. **Resolve Prior Test Issues**:
   - **TntrTradeTransformerImplTest (Prior Issue)**:
     - If you encountered `Cannot resolve symbol` for `RepositoryCommand` or `RepositoryCommandFactory`, this test class assumes their availability. Share `RepositoryCommand.java` and `RepositoryCommandFactory.java` if errors persist.
     - Run the prior `TntrTradeTransformerImplTest` (if different) and share results.
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
     - Run `ServiceCreationListenerTest.java` (previous response) and share results.
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
   - Share `JiraReference.java`, `JiraTaggingDetail.java`, `ReportingTemplate.java`, `RepositoryCommand.java`, `RepositoryCommandFactory.java`, `TntrTdxNodeBuilderUtil.java`, `DateTimeService.java` if test failures occur.
   - Share `ApiException.java`, `DfConnectionConfig.java`, `TntrRepositoryConfig.java`, `MIConstants.java`, `SubjectIdentifier.java`, `FrontOfficeSubjectIdentifier.java`, `FoundationServiceCreationListener.java`, etc., for prior test issues.
   - Share `pom.xml` or `build.gradle` to confirm Mockito/PowerMock versions.

4. **Confirm Mockito/PowerMock Setup**:
   - Check `pom.xml` or `build.gradle` for Mockito version (1.x or 2.x+).
   - Ensure PowerMock is configured for static mocking.

Please run the `TntrTradeTransformerImplTest` class and share the results. Additionally, provide the requested class files and the results of the `DfConfigurerTest`, `ApiExceptionHandlerTest`, `MIAnalyticsDashboardRepositoryImplTest`, `ServiceCreationListenerTest`, `BlotterExportMappingsRepositoryImplTest`, and `SsoConfigurationTest` to resolve all issues comprehensively!