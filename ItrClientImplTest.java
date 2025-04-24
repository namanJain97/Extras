```java
package com.rbs.tntr.business.taggingService.service.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nwm.tntr.configuration.ItrConfiguration;
import com.nwm.tntr.itr.IQuery;
import com.nwm.tntr.utiil.RequestIdGenerator;
import com.rbs.tntr.business.taggingService.configuration.TaggingAuthenticationService;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.common.Itr2Query;
import com.rbs.tntr.domain.taggingService.jiraTaggingDomain.exceptions.TaggingServiceRunTimeException;
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
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({LoggerFactory.class, RequestIdGenerator.class, JsonRequestCallback.class, ParameterizedTypeReferenceBuilder.class})
public class ItrClientImplTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TaggingAuthenticationService authenticationService;

    @Mock
    private ItrConfiguration itrConfiguration;

    @Mock
    private Logger logger;

    @InjectMocks
    private ItrClientImpl itrClient;

    private ObjectMapper objectMapper;

    @Before
    public void setUp() throws Exception {
        // Mock static LoggerFactory
        PowerMockito.mockStatic(LoggerFactory.class);
        when(LoggerFactory.getLogger(ItrClientImpl.class)).thenReturn(logger);

        // Mock static RequestIdGenerator
        PowerMockito.mockStatic(RequestIdGenerator.class);
        when(RequestIdGenerator.getRequestId()).thenReturn("req123");

        // Mock ItrConfiguration and TaggingAuthenticationService
        when(itrConfiguration.getItr2SsoPermission()).thenReturn("itr2Permission");
        when(authenticationService.getApplicationSsoToken("itr2Permission")).thenReturn("ssoToken123");

        // Initialize ObjectMapper
        objectMapper = new ObjectMapper();

        // Mock static JsonRequestCallback and ParameterizedTypeReferenceBuilder
        PowerMockito.mockStatic(JsonRequestCallback.class);
        PowerMockito.mockStatic(ParameterizedTypeReferenceBuilder.class);
    }

    @Test
    public void testFetch_URL_Itr2Query_Success() throws Exception {
        // Prepare test data
        URL url = new URL("http://example.com/api");
        Itr2Query query = mock(Itr2Query.class);
        String jsonBody = objectMapper.writeValueAsString(query);
        List<String> responseBody = Arrays.asList("item1", "item2");
        ResponseEntity<List<String>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);

        // Mock RestTemplate
        when(restTemplate.exchange(
                eq(url.toString()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // Execute
        List<String> result = itrClient.fetch(url, query, String.class);

        // Verify
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("item1", result.get(0));
        assertEquals("item2", result.get(1));

        // Verify RestTemplate call
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(url.toString()),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                any(ParameterizedTypeReference.class)
        );
        HttpEntity capturedEntity = entityCaptor.getValue();
        assertEquals(jsonBody, capturedEntity.getBody());
        HttpHeaders headers = capturedEntity.getHeaders();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertEquals("ssoToken123", headers.getFirst("sso-token"));
        assertEquals("req123", headers.getFirst("RequestId"));

        // Verify logging
        verify(logger).info("fetch reqeuestId:{} url:{}  body:{}", "req123", url, jsonBody);
        verify(logger).info("fetch reqeuestId:{} recordCount:{}", "req123", 2);
    }

    @Test
    public void testFetch_URL_Itr2Query_HttpClientErrorException() {
        // Prepare test data
        URL url = new URL("http://example.com/api");
        Itr2Query query = mock(Itr2Query.class);
        String jsonBody = "{}";
        HttpClientErrorException exception = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request", "Error response".getBytes(), null);

        // Mock RestTemplate
        when(restTemplate.exchange(
                eq(url.toString()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(exception);

        // Execute and verify exception
        HttpClientErrorException thrown = assertThrows(HttpClientErrorException.class, () -> {
            itrClient.fetch(url, query, String.class);
        });
        assertEquals(exception, thrown);

        // Verify logging
        verify(logger).info("fetch reqeuestId:{} url:{}  body:{}", "req123", url, jsonBody);
        verify(logger).error("fetch reqeuestId:{} error:{}  response:{}", "req123", exception.getMessage(), "Error response");
    }

    @Test
    public void testFetch_URL_Itr2Query_NonOkStatus() {
        // Prepare test data
        URL url = new URL("http://example.com/api");
        Itr2Query query = mock(Itr2Query.class);
        List<String> responseBody = Arrays.asList("item1");
        ResponseEntity<List<String>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.CREATED);

        // Mock RestTemplate
        when(restTemplate.exchange(
                eq(url.toString()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // Execute and verify exception
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            itrClient.fetch(url, query, String.class);
        });
        assertEquals("Unable to fetch data from Itr", thrown.getMessage());
    }

    @Test
    public void testFetch_String_Itr2Query_Streaming_Success() throws Exception {
        // Prepare test data
        String url = "http://example.com/api";
        Itr2Query query = mock(Itr2Query.class);
        String jsonBody = objectMapper.writeValueAsString(query);
        Consumer<InputStream> streamConsumer = mock(Consumer.class);
        InputStream inputStream = new ByteArrayInputStream("stream data".getBytes());

        // Mock RestTemplate
        ResponseExtractor<List<String>> responseExtractor = clientHttpResponse -> {
            streamConsumer.accept(clientHttpResponse.getBody());
            return null;
        };
        when(restTemplate.execute(
                eq(url),
                eq(HttpMethod.POST),
                any(),
                any(ResponseExtractor.class)
        )).thenAnswer(invocation -> {
            ResponseExtractor<List<String>> extractor = invocation.getArgument(3);
            extractor.extractData(mock(ClientHttpResponse.class, withSettings().defaultAnswer(RETURNS_MOCKS).extraInterfaces(ClientHttpResponse.class)));
            return null;
        });

        // Mock ClientHttpResponse
        ClientHttpResponse clientHttpResponse = mock(ClientHttpResponse.class);
        when(clientHttpResponse.getBody()).thenReturn(inputStream);

        // Execute
        List<String> result = itrClient.fetch(url, query, streamConsumer);

        // Verify
        assertNull(result);
        verify(streamConsumer).accept(inputStream);

        // Verify RestTemplate call
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).execute(
                eq(url),
                eq(HttpMethod.POST),
                any(),
                any(ResponseExtractor.class)
        );

        // Verify logging
        verify(logger).info("fetch reqeuestId:{} url:{}  body:{}", "req123", url, jsonBody);
    }

    @Test
    public void testFetch_String_Itr2Query_Streaming_HttpClientErrorException() {
        // Prepare test data
        String url = "http://example.com/api";
        Itr2Query query = mock(Itr2Query.class);
        String jsonBody = "{}";
        Consumer<InputStream> streamConsumer = mock(Consumer.class);
        HttpClientErrorException exception = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request", "Error response".getBytes(), null);

        // Mock RestTemplate
        when(restTemplate.execute(
                eq(url),
                eq(HttpMethod.POST),
                any(),
                any(ResponseExtractor.class)
        )).thenThrow(exception);

        // Execute and verify exception
        HttpClientErrorException thrown = assertThrows(HttpClientErrorException.class, () -> {
            itrClient.fetch(url, query, streamConsumer);
        });
        assertEquals(exception, thrown);

        // Verify logging
        verify(logger).info("fetch reqeuestId:{} url:{}  body:{}", "req123", url, jsonBody);
        verify(logger).error("fetch reqeuestId:{} error:{}  response:{}", "req123", exception.getMessage(), "Error response");
    }

    @Test
    public void testFetch_String_IQuery_Streaming_Success() throws Exception {
        // Prepare test data
        String url = "http://example.com/api";
        IQuery query = mock(IQuery.class);
        String jsonBody = objectMapper.writeValueAsString(query);
        Consumer<InputStream> streamConsumer = mock(Consumer.class);
        InputStream inputStream = new ByteArrayInputStream("stream data".getBytes());

        // Mock RestTemplate
        ResponseExtractor<List<String>> responseExtractor = clientHttpResponse -> {
            streamConsumer.accept(clientHttpResponse.getBody());
            return null;
        };
        when(restTemplate.execute(
                eq(url),
                eq(HttpMethod.POST),
                any(),
                any(ResponseExtractor.class)
        )).thenAnswer(invocation -> {
            ResponseExtractor<List<String>> extractor = invocation.getArgument(3);
            extractor.extractData(mock(ClientHttpResponse.class, withSettings().defaultAnswer(RETURNS_MOCKS).extraInterfaces(ClientHttpResponse.class)));
            return null;
        });

        // Mock ClientHttpResponse
        ClientHttpResponse clientHttpResponse = mock(ClientHttpResponse.class);
        when(clientHttpResponse.getBody()).thenReturn(inputStream);

        // Execute
        List<String> result = itrClient.fetch(url, query, streamConsumer);

        // Verify
        assertNull(result);
        verify(streamConsumer).accept(inputStream);

        // Verify RestTemplate call
        verify(restTemplate).execute(
                eq(url),
                eq(HttpMethod.POST),
                any(),
                any(ResponseExtractor.class)
        );

        // Verify logging
        verify(logger).info("fetch reqeuestId:{} url:{}  body:{}", "req123", url, jsonBody);
    }

    @Test
    public void testGetJsonBody_Success() throws JsonProcessingException {
        // Prepare test data
        Itr2Query query = mock(Itr2Query.class);
        String jsonBody = "{}";
        when(objectMapper.writeValueAsString(query)).thenReturn(jsonBody);

        // Execute
        String result = itrClient.getJsonBody(query);

        // Verify
        assertEquals(jsonBody, result);
    }

    @Test
    public void testGetJsonBody_JsonProcessingException() throws JsonProcessingException {
        // Prepare test data
        Itr2Query query = mock(Itr2Query.class);
        JsonProcessingException exception = new JsonProcessingException("JSON error") {};
        when(objectMapper.writeValueAsString(query)).thenThrow(exception);

        // Execute and verify exception
        TaggingServiceRunTimeException thrown = assertThrows(TaggingServiceRunTimeException.class, () -> {
            itrClient.getJsonBody(query);
        });
        assertEquals(exception, thrown.getCause());
    }

    @Test
    public void testGetHeaderWithSso() {
        // Execute
        HttpHeaders headers = itrClient.getHeaderWithSso("req123");

        // Verify
        assertNotNull(headers);
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertEquals("ssoToken123", headers.getFirst("sso-token"));
        assertEquals("req123", headers.getFirst("RequestId"));
    }

    @Test
    public void testFetch_URL_NullQuery() {
        // Prepare test data
        URL url = new URL("http://example.com/api");

        // Execute and verify exception
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            itrClient.fetch(url, (Itr2Query) null, String.class);
        });
        assertTrue(thrown.getMessage().contains("query"));
    }

    // Helper method to access private getJsonBody method
    private String getJsonBody(Object query) {
        try {
            java.lang.reflect.Method method = ItrClientImpl.class.getDeclaredMethod("getJsonBody", Object.class);
            method.setAccessible(true);
            return (String) method.invoke(itrClient, query);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Helper method to access private getHeaderWithSso method
    private HttpHeaders getHeaderWithSso(String requestId) {
        try {
            java.lang.reflect.Method method = ItrClientImpl.class.getDeclaredMethod("getHeaderWithSso", String.class);
            method.setAccessible(true);
            return (HttpHeaders) method.invoke(itrClient, requestId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

### Key Features of the Test Class
1. **Setup**:
   - Uses `@InjectMocks` to create `ItrClientImpl` with mocked `RestTemplate`, `TaggingAuthenticationService`, and `ItrConfiguration`.
   - Uses PowerMock to mock static methods: `LoggerFactory.getLogger`, `RequestIdGenerator.getRequestId`, `JsonRequestCallback.createFor`, and `ParameterizedTypeReferenceBuilder.getListTypeReference`.
   - Configures mocks for SSO token and permission retrieval.

2. **Test Methods**:
   - **fetch(URL, Itr2Query, Class<T>)**:
     - `testFetch_URL_Itr2Query_Success`: Tests successful response with `HttpStatus.OK`, verifying response body, headers, and logging.
     - `testFetch_URL_Itr2Query_HttpClientErrorException`: Tests `HttpClientErrorException`, verifying error logging and rethrow.
     - `testFetch_URL_Itr2Query_NonOkStatus`: Tests non-`OK` status, verifying `IllegalStateException` from `Preconditions.checkState`.
   - **fetch(String, Itr2Query, Consumer<InputStream>)**:
     - `testFetch_String_Itr2Query_Streaming_Success`: Tests streaming response, verifying `Consumer` invocation and null result.
     - `testFetch_String_Itr2Query_Streaming_HttpClientErrorException`: Tests `HttpClientErrorException`, verifying error logging.
   - **fetch(String, IQuery, Consumer<InputStream>)**:
     - `testFetch_String_IQuery_Streaming_Success`: Tests streaming with `IQuery`, similar to `Itr2Query`.
   - **getJsonBody**:
     - `testGetJsonBody_Success`: Tests successful JSON serialization.
     - `testGetJsonBody_JsonProcessingException`: Tests `JsonProcessingException`, verifying `TaggingServiceRunTimeException`.
   - **getHeaderWithSso**:
     - `testGetHeaderWithSso`: Verifies headers include `Content-Type`, `sso-token`, and `RequestId`.
   - **Edge Cases**:
     - `testFetch_URL_NullQuery`: Tests null `query`, expecting `NullPointerException` from JSON serialization.

3. **Assertions**:
   - Uses `assertNotNull`, `assertEquals`, and `assertNull` to verify results.
   - Uses `assertThrows` to verify expected exceptions.
   - Uses `ArgumentCaptor` to inspect `HttpEntity` and logger arguments.

4. **Mocking**:
   - Mocks `RestTemplate` for `exchange` and `execute` calls.
   - Mocks `ClientHttpResponse` for streaming responses.
   - Uses PowerMock to stub static utility methods.
   - Mocks `Itr2Query` and `IQuery` to avoid real implementations.

5. **Reflection**:
   - Provides helper methods to invoke private `getJsonBody` and `getHeaderWithSso` for testing.

### Assumptions
- **ItrClient Interface**:
  - Declares `fetch(URL, Itr2Query, Class<T>)`, `fetch(String, Itr2Query, Consumer<InputStream>)`, and `fetch(String, IQuery, Consumer<InputStream>)`.
- **Utility Classes**:
  - `JsonRequestCallback.createFor(HttpEntity)` returns a request callback.
  - `ParameterizedTypeReferenceBuilder.getListTypeReference(Class)` returns a `ParameterizedTypeReference<List<T>>`.
  - `RequestIdGenerator.getRequestId()` returns a `String`.
- **Dependencies**:
  - `ItrConfiguration.getItr2SsoPermission()` returns a `String`.
  - `TaggingAuthenticationService.getApplicationSsoToken(String)` returns a `String`.
- **ObjectMapper**:
  - Static and configured with default timezone.
  - Throws `JsonProcessingException` for serialization errors.
- **Error Handling**:
  - HTTP exceptions (`HttpClientErrorException`, `HttpServerErrorException`, `UnknownHttpStatusCodeException`) are logged and rethrown.
  - Generic `Exception` is logged and rethrown.
  - `Preconditions.checkState` throws `IllegalStateException` for non-`OK` status.
- **Logging**:
  - Static `Logger` is mocked via PowerMock.
  - Typo `reqeuestId` in log messages is preserved from the source code.

### Potential Issues
- **PowerMock Compatibility**: PowerMock requires Mockito 2.x. If your project uses Mockito 1.x (inferred from prior tests), update to Mockito 2.x or refactor to avoid static method mocking (e.g., inject `RequestIdGenerator`).
- **Utility Classes**:
  - If `JsonRequestCallback` or `ParameterizedTypeReferenceBuilder` don’t exist or have different methods, share their definitions (likely in `com.nwm.tntr.utiil` or similar).
  - If `RequestIdGenerator.getRequestId()` has a different signature, share `RequestIdGenerator.java`.
- **ItrClient Interface**: If the interface differs (e.g., different method signatures), share `ItrClient.java`.
- **Typo in Logs**: The log message `reqeuestId` (instead of `requestId`) is used as-is. If it’s a typo, confirm the correct string.
- **Null Handling**: The test assumes null `query` causes a `NullPointerException` in `getJsonBody`. If `ItrClientImpl` handles nulls differently, share validation logic.
- **Mockito Version**: If using Mockito 2.x, consider `@RunWith(PowerMockRunner.class)` with stricter stubbing.

### Next Steps
1. **Run the Test Class**:
   - Save `ItrClientImplTest.java` in `src/test/java/com/rbs/tntr/business/taggingService/service/common/`.
   - Add PowerMock dependencies to `pom.xml` or `build.gradle` (see above).
   - Run the tests in IntelliJ IDEA (Community Edition 2024.2.3) or via Maven/Gradle.
   - Share the test output, including any errors or stack traces.

2. **Resolve Prior Test Issues**:
   - **ApiExceptionHandlerTest**:
     - Run the provided `ApiExceptionHandlerTest.java` (previous response) and share results.
     - Share `ApiException.java` if its structure differs from the assumed stub.
   - **TntrTradeTransformerImplTest**:
     - Update `TntrTradeTransformerImplTest.java` with correct imports for `RepositoryCommand` and `RepositoryCommandFactory` (see earlier response).
     - Share `RepositoryCommand.java`, `RepositoryCommandFactory.java`, and `ReportingTemplate.java`.
     - Run the test and share results.
   - **DfConfigurerTest**:
     - Run the provided `DfConfigurerTest.java` and share results.
     - Ensure PowerMock dependencies are added.
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
   - Share `ItrClient.java`, `JsonRequestCallback.java`, `ParameterizedTypeReferenceBuilder.java`, `RequestIdGenerator.java`, `ItrConfiguration.java`, `TaggingAuthenticationService.java`, `Itr2Query.java`, `IQuery.java` if test failures indicate missing methods or incorrect assumptions.
   - Share `pom.xml` or `build.gradle` to confirm Mockito and PowerMock versions.

4. **Confirm Mockito/PowerMock Setup**:
   - Check `pom.xml` or `build.gradle` for Mockito version. Update to 2.x if using PowerMock.
   - Ensure PowerMock dependencies are correctly configured.

Please run the `ItrClientImplTest` class and share the results. Additionally, provide the requested class files and the results of the `ApiExceptionHandlerTest`, `TntrTradeTransformerImplTest`, `DfConfigurerTest`, `BlotterExportMappingsRepositoryImplTest`, and `SsoConfigurationTest` to resolve all issues comprehensively!