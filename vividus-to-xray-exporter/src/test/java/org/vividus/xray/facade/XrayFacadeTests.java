/*
 * Copyright 2019-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vividus.xray.facade;

import static com.github.valfirst.slf4jtest.LoggingEvent.info;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.github.valfirst.slf4jtest.TestLogger;
import com.github.valfirst.slf4jtest.TestLoggerFactory;
import com.github.valfirst.slf4jtest.TestLoggerFactoryExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vividus.jira.JiraFacade;
import org.vividus.xray.databind.AbstractTestCaseSerializer;
import org.vividus.xray.databind.CucumberTestCaseSerializer;
import org.vividus.xray.databind.ManualTestCaseSerializer;
import org.vividus.xray.facade.XrayFacade.NonEditableIssueStatusException;
import org.vividus.xray.model.AbstractTestCase;
import org.vividus.xray.model.CucumberTestCase;
import org.vividus.xray.model.ManualTestCase;
import org.vividus.xray.model.ManualTestStep;
import org.vividus.xray.model.TestCaseType;

@ExtendWith({ MockitoExtension.class, TestLoggerFactoryExtension.class })
class XrayFacadeTests
{
    private static final String ISSUE_ID = "issue id";
    private static final String BODY = "{}";
    private static final String PROJECT_KEY = "project key";
    private static final String OPEN_STATUS = "Open";
    private static final String ASSIGNEE = "test-assignee";
    private static final String MANUAL_TYPE = "Manual";
    private static final String CUCUMBER_TYPE = "Cucumber";
    private static final String CREATE_RESPONSE = "{\"key\" : \"" + ISSUE_ID + "\"}";

    @Captor private ArgumentCaptor<ManualTestCase> manualTestCaseCaptor;
    @Captor private ArgumentCaptor<CucumberTestCase> cucumberTestCaseCaptor;

    @Mock private ManualTestCaseSerializer manualTestSerializer;
    @Mock private CucumberTestCaseSerializer cucumberTestSerializer;
    @Mock private JiraFacade jiraFacade;
    @Mock private ManualTestStep manualTestStep;
    private XrayFacade xrayFacade;

    private final TestLogger logger = TestLoggerFactory.getTestLogger(XrayFacade.class);

    @AfterEach
    void afterEach()
    {
        verifyNoMoreInteractions(jiraFacade);
    }

    @Test
    void shouldCreateTestsLink() throws IOException
    {
        initializeFacade(List.of());
        String requirementId = "requirement id";
        String linkType = "Tests";

        xrayFacade.createTestsLink(ISSUE_ID, requirementId);

        verify(jiraFacade).createIssueLink(ISSUE_ID, requirementId, linkType);
        assertThat(logger.getLoggingEvents(),
                is(List.of(info("Create '{}' link from {} to {}", linkType, ISSUE_ID, requirementId))));
    }

    @Test
    void shouldUpdateManualTestCase() throws IOException, NonEditableIssueStatusException
    {
        initializeFacade(List.of(OPEN_STATUS));
        mockSerialization(manualTestSerializer, manualTestCaseCaptor);
        ManualTestCaseParameters parameters = createManualParameters();

        when(jiraFacade.getIssueStatus(ISSUE_ID)).thenReturn(OPEN_STATUS);

        xrayFacade.updateTestCase(ISSUE_ID, parameters);

        verify(jiraFacade).updateIssue(ISSUE_ID, BODY);
        verifyUpdateLogs(MANUAL_TYPE);
        verifyManualTestCase(parameters);
    }

    @Test
    void shouldUpdateCucumberTestCase() throws IOException, NonEditableIssueStatusException
    {
        initializeFacade(List.of(OPEN_STATUS));
        mockSerialization(cucumberTestSerializer, cucumberTestCaseCaptor);
        CucumberTestCaseParameters parameters = createCucumberParameters();

        when(jiraFacade.getIssueStatus(ISSUE_ID)).thenReturn(OPEN_STATUS);

        xrayFacade.updateTestCase(ISSUE_ID, parameters);

        verify(jiraFacade).updateIssue(ISSUE_ID, BODY);
        verifyUpdateLogs(CUCUMBER_TYPE);
        verifyCucumberTestCase(parameters);
    }

    private void verifyUpdateLogs(String type)
    {
        assertThat(logger.getLoggingEvents(), is(List.of(
            info("Updating {} Test Case with ID {}: {}", type, ISSUE_ID, BODY),
            info("{} Test with key {} has been updated", type, ISSUE_ID))));
    }

    @Test
    void shouldUpdateTestCaseNotEditableStatus() throws IOException, NonEditableIssueStatusException
    {
        initializeFacade(List.of(OPEN_STATUS));
        ManualTestCaseParameters parameters = createManualParameters();

        String closedStatus = "Closed";
        when(jiraFacade.getIssueStatus(ISSUE_ID)).thenReturn(closedStatus);

        NonEditableIssueStatusException exception = assertThrows(NonEditableIssueStatusException.class,
            () -> xrayFacade.updateTestCase(ISSUE_ID, parameters));
        assertEquals("Issue " + ISSUE_ID + " is in non-editable '" + closedStatus + "' status", exception.getMessage());
        assertThat(logger.getLoggingEvents(), is(List.of()));
    }

    @Test
    void shouldCreateManualTestCase() throws IOException
    {
        mockSerialization(manualTestSerializer, manualTestCaseCaptor);
        initializeFacade(List.of());
        ManualTestCaseParameters parameters = createManualParameters();
        when(jiraFacade.createIssue(BODY)).thenReturn(CREATE_RESPONSE);

        xrayFacade.createTestCase(parameters);

        verifyCreateLogs(MANUAL_TYPE);
        verifyManualTestCase(parameters);
    }

    @Test
    void shouldCreateCucumberTestCase() throws IOException
    {
        mockSerialization(cucumberTestSerializer, cucumberTestCaseCaptor);
        initializeFacade(List.of());
        CucumberTestCaseParameters parameters = createCucumberParameters();
        when(jiraFacade.createIssue(BODY)).thenReturn(CREATE_RESPONSE);

        xrayFacade.createTestCase(parameters);

        verifyCreateLogs(CUCUMBER_TYPE);
        verifyCucumberTestCase(parameters);
    }

    private void verifyCreateLogs(String type)
    {
        assertThat(logger.getLoggingEvents(), is(List.of(
            info("Creating {} Test Case: {}", type, BODY),
            info("{} Test with key {} has been created", type, ISSUE_ID))));
    }

    private void verifyManualTestCase(ManualTestCaseParameters parameters)
    {
        ManualTestCase manualTestCase = manualTestCaseCaptor.getValue();
        assertEquals(List.of(manualTestStep), manualTestCase.getManualTestSteps());
        verifyTestCase(parameters, manualTestCase);
    }

    private void verifyCucumberTestCase(CucumberTestCaseParameters parameters)
    {
        CucumberTestCase cucumberTestCase = cucumberTestCaseCaptor.getValue();
        assertEquals(parameters.getScenarioType(), cucumberTestCase.getScenarioType());
        assertEquals(parameters.getScenario(), cucumberTestCase.getScenario());
        verifyTestCase(parameters, cucumberTestCase);
    }

    private void verifyTestCase(AbstractTestCaseParameters parameters, AbstractTestCase testCase)
    {
        assertEquals(PROJECT_KEY, testCase.getProjectKey());
        assertEquals(ASSIGNEE, testCase.getAssignee());
        assertEquals(parameters.getLabels(), testCase.getLabels());
        assertEquals(parameters.getComponents(), testCase.getComponents());
        assertEquals(parameters.getSummary(), testCase.getSummary());
    }

    private ManualTestCaseParameters createManualParameters()
    {
        ManualTestCaseParameters parameters = createParameters(TestCaseType.MANUAL, ManualTestCaseParameters::new);
        parameters.setSteps(List.of(manualTestStep));
        return parameters;
    }

    private CucumberTestCaseParameters createCucumberParameters()
    {
        CucumberTestCaseParameters parameters = createParameters(TestCaseType.CUCUMBER,
                CucumberTestCaseParameters::new);
        parameters.setScenarioType("scenario-type");
        parameters.setScenario("scenario");
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractTestCaseParameters> T createParameters(TestCaseType type, Supplier<T> factory)
    {
        AbstractTestCaseParameters parameters = factory.get();
        parameters.setType(type);
        parameters.setSummary("scenarioTitle");
        parameters.setLabels(new LinkedHashSet<>(List.of("label")));
        parameters.setComponents(new LinkedHashSet<>(List.of("component")));
        return (T) parameters;
    }

    private void initializeFacade(List<String> editableStatuses)
    {
        xrayFacade = new XrayFacade(PROJECT_KEY, ASSIGNEE, editableStatuses, jiraFacade, manualTestSerializer,
                cucumberTestSerializer);
    }

    private <T extends AbstractTestCase> void mockSerialization(AbstractTestCaseSerializer<T> serializer,
            ArgumentCaptor<T> captor) throws IOException
    {
        doAnswer(a ->
        {
            JsonGenerator generator = a.getArgument(1, JsonGenerator.class);
            generator.writeStartObject();
            generator.writeEndObject();
            return null;
        }).when(serializer).serialize(captor.capture(), any(JsonGenerator.class), any(SerializerProvider.class));
    }
}
