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

package org.vividus.xray.converter;

import static java.lang.System.lineSeparator;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.vividus.bdd.model.jbehave.Parameters;
import org.vividus.bdd.model.jbehave.Scenario;
import org.vividus.bdd.model.jbehave.Step;

public final class CucumberScenarioConverter
{
    private static final String COMMENT_KEYWORD = "!-- ";
    private static final String CUCUMBER_SEPARATOR = "|";

    private CucumberScenarioConverter()
    {
    }

    public static CucumberScenario convert(Scenario scenario)
    {
        boolean hasExamples = scenario.getExamples() != null;
        String type = hasExamples ? "Scenario Outline" : "Scenario";
        String body = hasExamples ? buildParameterizedScenario(scenario) : buildPlainScenario(scenario);
        return new CucumberScenario(type, body);
    }

    private static String buildPlainScenario(Scenario scenario)
    {
        return getSteps(scenario, UnaryOperator.identity());
    }

    private static String buildParameterizedScenario(Scenario scenario)
    {
        return getSteps(scenario, steps ->
        {
            String examples = buildScenarioExamplesTable(scenario.getExamples().getParameters());
            steps.add(examples);
            return steps;
        });
    }

    private static String getSteps(Scenario scenario, UnaryOperator<List<String>> beforeJoining)
    {
        return scenario.collectSteps()
                       .stream()
                       .map(Step::getValue)
                       .map(processStep())
                       .collect(Collectors.collectingAndThen(Collectors.toList(), beforeJoining))
                       .stream()
                       .collect(Collectors.joining(lineSeparator()));
    }

    private static UnaryOperator<String> processStep()
    {
        return step -> step.startsWith(COMMENT_KEYWORD) ? step.replace(COMMENT_KEYWORD, "# ") : step;
    }

    private static String buildScenarioExamplesTable(Parameters parameters)
    {
        return new StringBuilder("Examples:").append(lineSeparator())
                                             .append(joinTableRow(parameters.getNames()))
                                             .append(parameters.getValues().stream()
                                                                           .map(CucumberScenarioConverter::joinTableRow)
                                                                           .collect(Collectors.joining()))
                                             .toString();
    }

    private static String joinTableRow(List<String> values)
    {
        return values.stream().collect(
                Collectors.joining(CUCUMBER_SEPARATOR, CUCUMBER_SEPARATOR, CUCUMBER_SEPARATOR + lineSeparator()));
    }

    public static class CucumberScenario
    {
        private final String type;
        private final String scenario;

        public CucumberScenario(String type, String scenario)
        {
            this.type = type;
            this.scenario = scenario;
        }

        public String getType()
        {
            return type;
        }

        public String getScenario()
        {
            return scenario;
        }
    }
}
