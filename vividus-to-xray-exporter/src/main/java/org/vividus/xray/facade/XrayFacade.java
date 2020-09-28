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

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vividus.jira.JiraFacade;
import org.vividus.util.json.JsonPathUtils;
import org.vividus.xray.databind.CucumberTestCaseSerializer;
import org.vividus.xray.databind.ManualTestCaseSerializer;
import org.vividus.xray.model.AbstractTestCase;
import org.vividus.xray.model.CucumberTestCase;
import org.vividus.xray.model.ManualTestCase;

public class XrayFacade
{
    private static final Logger LOGGER = LoggerFactory.getLogger(XrayFacade.class);

    private final String projectKey;
    private final String assignee;
    private final List<String> editableStatuses;
    private final JiraFacade jiraFacade;
    private final ObjectMapper objectMapper;

    public XrayFacade(String projectKey, String assignee, List<String> editableStatuses, JiraFacade jiraFacade,
            ManualTestCaseSerializer manualTestSerializer, CucumberTestCaseSerializer cucumberTestSerializer)
    {
        this.projectKey = projectKey;
        this.assignee = assignee;
        this.editableStatuses = editableStatuses;
        this.jiraFacade = jiraFacade;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(new SimpleModule().addSerializer(ManualTestCase.class, manualTestSerializer)
                                                  .addSerializer(CucumberTestCase.class, cucumberTestSerializer));
    }

    public String createTestCase(CucumberTestCaseParameters testCaseParameters) throws IOException
    {
        return createTestCase(createCucumberTest(testCaseParameters));
    }

    public String createTestCase(ManualTestCaseParameters testCaseParameters) throws IOException
    {
        return createTestCase(createManualTest(testCaseParameters));
    }

    private <T extends AbstractTestCase> String createTestCase(AbstractTestCase testCase) throws IOException
    {
        String createTestRequest = objectMapper.writeValueAsString(testCase);
        LOGGER.atInfo().addArgument(testCase::getType).addArgument(createTestRequest).log("Creating {} Test Case: {}");
        String response = jiraFacade.createIssue(createTestRequest);
        String issueKey = JsonPathUtils.getData(response, "$.key");
        LOGGER.atInfo().addArgument(testCase::getType)
                       .addArgument(issueKey)
                       .log("{} Test with key {} has been created");
        return issueKey;
    }

    public void updateTestCase(String testCaseKey, CucumberTestCaseParameters testCaseParameters)
            throws IOException, NonEditableIssueStatusException
    {
        updateTestCase(testCaseKey, () -> createCucumberTest(testCaseParameters));
    }

    public void updateTestCase(String testCaseKey, ManualTestCaseParameters testCaseParameters)
            throws IOException, NonEditableIssueStatusException
    {
        updateTestCase(testCaseKey, () -> createManualTest(testCaseParameters));
    }

    private <T extends AbstractTestCase> void updateTestCase(String testCaseKey, Supplier<T> testCaseFactory)
            throws IOException, NonEditableIssueStatusException
    {
        checkIfIssueEditable(testCaseKey);
        AbstractTestCase testCase = testCaseFactory.get();
        String updateTestRequest = objectMapper.writeValueAsString(testCase);
        LOGGER.atInfo().addArgument(testCase::getType)
                       .addArgument(testCaseKey)
                       .addArgument(updateTestRequest)
                       .log("Updating {} Test Case with ID {}: {}");
        jiraFacade.updateIssue(testCaseKey, updateTestRequest);
        LOGGER.atInfo().addArgument(testCase::getType)
                       .addArgument(testCaseKey)
                       .log("{} Test with key {} has been updated");
    }

    private void checkIfIssueEditable(String issueKey) throws IOException, NonEditableIssueStatusException
    {
        String status = jiraFacade.getIssueStatus(issueKey);

        if (editableStatuses.stream().noneMatch(s -> StringUtils.equalsIgnoreCase(s, status)))
        {
            throw new NonEditableIssueStatusException(issueKey, status);
        }
    }

    public void createTestsLink(String testCaseId, String requirementId) throws IOException
    {
        String linkType = "Tests";
        LOGGER.atInfo().addArgument(linkType)
                       .addArgument(testCaseId)
                       .addArgument(requirementId)
                       .log("Create '{}' link from {} to {}");
        jiraFacade.createIssueLink(testCaseId, requirementId, linkType);
    }

    private ManualTestCase createManualTest(ManualTestCaseParameters testCaseParameters)
    {
        ManualTestCase manualTest = createTestCase(ManualTestCase::new, testCaseParameters);
        manualTest.setManualTestSteps(testCaseParameters.getSteps());
        return manualTest;
    }

    private CucumberTestCase createCucumberTest(CucumberTestCaseParameters testCaseParameters)
    {
        CucumberTestCase cucumberTest = createTestCase(CucumberTestCase::new, testCaseParameters);
        cucumberTest.setScenarioType(testCaseParameters.getScenarioType());
        cucumberTest.setScenario(testCaseParameters.getScenario());
        return cucumberTest;
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractTestCase> T createTestCase(Supplier<T> testCaseFactory,
            AbstractTestCaseParameters parameters)
    {
        AbstractTestCase testCase = testCaseFactory.get();
        testCase.setType(parameters.getType().getValue());
        testCase.setProjectKey(projectKey);
        testCase.setAssignee(assignee);
        testCase.setSummary(parameters.getSummary());
        testCase.setLabels(parameters.getLabels());
        testCase.setComponents(parameters.getComponents());
        return (T) testCase;
    }

    public static final class NonEditableIssueStatusException extends Exception
    {
        private static final long serialVersionUID = -5547086076322794984L;

        public NonEditableIssueStatusException(String testCaseId, String status)
        {
            super("Issue " + testCaseId + " is in non-editable '" + status + "' status");
        }
    }
}
