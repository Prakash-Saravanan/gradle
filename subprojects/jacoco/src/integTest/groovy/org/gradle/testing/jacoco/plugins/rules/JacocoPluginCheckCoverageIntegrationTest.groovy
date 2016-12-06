/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testing.jacoco.plugins.rules

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest
import spock.lang.Unroll

import static JacocoViolationRulesLimit.Insufficient
import static JacocoViolationRulesLimit.Sufficient

class JacocoPluginCheckCoverageIntegrationTest extends AbstractIntegrationSpec {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)
    private final static String[] TEST_TASK_PATH = [':test'] as String[]
    private final static String[] JACOCO_REPORT_TASK_PATH = [':jacocoTestReport'] as String[]
    private final static String[] TEST_AND_JACOCO_REPORT_TASK_PATHS = TEST_TASK_PATH + JACOCO_REPORT_TASK_PATH
    private final static String[] INTEG_TEST_AND_JACOCO_REPORT_TASK_PATHS = [':integrationTest', ':jacocoIntegrationTestReport'] as String[]

    def setup() {
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
    }

    def "can define no rules"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {}
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
    }

    def "can define single rule without limits"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {
                    rule {}
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
    }

    def "Ant task reports error for unknown field value"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {
                    rule {
                        element = 'UNKNOWN'
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("'UNKNOWN' is not a permitted value for org.jacoco.core.analysis.ICoverageNode\$ElementType")
    }

    def "can define includes for single rule"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {
                    rule {
                        element = 'CLASS'
                        includes = ['com.company.*', 'org.gradle.*']
                        $Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for class org.gradle.Class1: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    def "can define excludes for single rule"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {
                    rule {
                        excludes = ['company', '$testDirectory.name']
                        $Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
    }

    def "can check rules even if all report formats are disabled"() {
        given:
        buildFile << """
            jacocoTestReport {
                reports {
                    xml.enabled false
                    csv.enabled false
                    html.enabled false
                }
                violationRules {
                    rule {
                        $Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    @Unroll
    def "can define rule with sufficient coverage for #description"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {
                    rule {
                        ${limits.join('\n')}
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)

        where:
        limits                                 | description
        [Sufficient.LINE_METRIC_COVERED_RATIO] | 'line metric with covered ratio'
        [Sufficient.CLASS_METRIC_MISSED_COUNT] | 'class metric with missed count'
        [Sufficient.LINE_METRIC_COVERED_RATIO,
         Sufficient.CLASS_METRIC_MISSED_COUNT] | 'line and class metric'

    }

    @Unroll
    def "can define rule with insufficient coverage for #description"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {
                    rule {
                        ${limits.join('\n')}
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: $errorMessage")

        where:
        limits                                   | description                                   | errorMessage
        [Insufficient.LINE_METRIC_COVERED_RATIO] | 'line metric with covered ratio'              | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        [Insufficient.CLASS_METRIC_MISSED_COUNT] | 'class metric with missed count'              | 'classes missed count is 0.0, but expected minimum is 0.5'
        [Insufficient.LINE_METRIC_COVERED_RATIO,
         Insufficient.CLASS_METRIC_MISSED_COUNT] | 'first of multiple insufficient limits fails' | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        [Sufficient.LINE_METRIC_COVERED_RATIO,
         Insufficient.CLASS_METRIC_MISSED_COUNT,
         Sufficient.CLASS_METRIC_MISSED_COUNT]   | 'first insufficient limits fails'             | 'classes missed count is 0.0, but expected minimum is 0.5'
    }

    def "can define multiple rules"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {
                    rule {
                        $Sufficient.LINE_METRIC_COVERED_RATIO
                    }
                    rule {
                        $Insufficient.CLASS_METRIC_MISSED_COUNT
                    }
                }
            }
        """

        when:
        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: classes missed count is 0.0, but expected minimum is 0.5")
    }

    def "can disable rules"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {
                    rule {
                        $Sufficient.LINE_METRIC_COVERED_RATIO
                    }
                    rule {
                        enabled = false
                        $Insufficient.CLASS_METRIC_MISSED_COUNT
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
    }

    def "can ignore failures"() {
        given:
        buildFile << """
            jacocoTestReport {
                violationRules {
                    failOnViolation = true

                    rule {
                        $Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    @Unroll
    def "can define same rules for multiple report tasks #tasksPaths"() {
        given:
        javaProjectUnderTest.writeIntegrationTestSourceFiles()

        buildFile << """
            tasks.withType(JacocoReport) {
                violationRules {
                    rule {
                        $Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        fails tasksPaths

        then:
        executedAndNotSkipped(tasksPaths)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")

        where:
        tasksPaths << [TEST_AND_JACOCO_REPORT_TASK_PATHS, INTEG_TEST_AND_JACOCO_REPORT_TASK_PATHS]
    }

    @Unroll
    def "can define different rules for multiple report tasks #tasksPaths"() {
        given:
        javaProjectUnderTest.writeIntegrationTestSourceFiles()

        buildFile << """
            $reportTaskName {
                violationRules {
                    rule {
                        $limit
                    }
                }
            }
        """

        when:
        fails tasksPaths

        then:
        executedAndNotSkipped(tasksPaths)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: $errorMessage")

        where:
        tasksPaths                              | reportTaskName                | limit                                  | errorMessage
        TEST_AND_JACOCO_REPORT_TASK_PATHS       | 'jacocoTestReport'            | Insufficient.LINE_METRIC_COVERED_RATIO | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        INTEG_TEST_AND_JACOCO_REPORT_TASK_PATHS | 'jacocoIntegrationTestReport' | Insufficient.CLASS_METRIC_MISSED_COUNT | 'classes missed count is 0.0, but expected minimum is 0.5'
    }

    def "changes to violation rules re-run task"() {
        buildFile << """
            jacocoTestReport {
                violationRules {
                    rule {
                        $Sufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executedAndNotSkipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)

        when:
        succeeds TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executed(TEST_AND_JACOCO_REPORT_TASK_PATHS)
        skipped(TEST_AND_JACOCO_REPORT_TASK_PATHS)

        when:
        buildFile << """
            jacocoTestReport.violationRules.rules[0].limits[0].maximum = 0.5
        """

        fails TEST_AND_JACOCO_REPORT_TASK_PATHS

        then:
        executed(JACOCO_REPORT_TASK_PATH)
        skipped(TEST_TASK_PATH)
        errorOutput.contains("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")
    }
}
