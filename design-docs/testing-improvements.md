Improve TestNG test execution/reporting

# Use cases

1. Efficient generation of XML results
2. Efficient generation of HTML results
3. HTML results contain output per test VS output per test class
4. HTML results contain aggregated std err/std out VS separated

# Implementation plan

## Story: TestNG XML generation is efficient (DONE)

-test events/output stored in binary format (internal format geared towards efficiency)
    -2 files per test class (events and output, the latter is streamed), cache of open files
    -uses java serialization
    -the binary file with output is streamed. We should keep reasonable amount of files open
-parent process writes the binary results
-parent process writes the XML results

### Coverage

-update existing coverage if needed
-inconsistent data in the binary format: unfinished tests, output for tests with unknown id, etc
-no binary results after test execution
-file handles are closed

### Backwards compatibility

-story introduces negligible breaking change: if the worker process / parent process crashes, no XML results are generated at all. Previously, partial XML results are available.

## Story: TestNG produces the new XML/HTML result by default (DONE)

- Change the Test `testReport` default value to `true`.
- Change the Test `testNGOptions.useDefaultListeners` default value to `false`.
- Extract a shared `TestReporter` implementation out of `TestNGTestFramework` and `JUnitTestFramework` and remove `TestFramework.report()`.
- Update the documentation and release notes accordingly.

### Coverage

- Add a performance test with a build with many TestNG tests which do not generate any logging output. Verify that this build is not any slower than Gradle 1.0 or Gradle 1.3.
- Update `TestNGProducesJUnitXmlResultsIntegrationTest` so that it uses the default value for the `testReport` property.
- Update `TestNGProducesOldReportsIntegrationTest` so that it uses the appropriate values for the `testReport` property.
- Update performance tests to use the default setting for the `testReport` property.

### Backwards compatibility:

- Story changes the default values (for better, though)

## Story: JUnit XML generation is efficient (DONE)

Use the same mechanism as TestNG for XML result generation

- Change `Test.executeTests()` so that it uses a TestReportDataCollector for all test frameworks (not just TestNG)
- Change `JUnitTestClassProcessor.startProcessing()` so that it no longer uses `JUnitXmlReportGenerator`.
- Remove `JUnitXmlReportGenerator`, `XmlTestSuiteWriter` and `XmlTestSuiteWriterFactory`.

### Coverage

- Add a performance test with a build with many JUnit tests which do not generate any logging output. Verify that this build is not any slower than Gradle 1.0 or Gradle 1.3.
- Add coverage for tests that don't have an associated method or class.
- Check that start-time and duration reported for a class should include all setup and tear-down.

### Backwards compatibility:

- No partial XML results available when process crashes (see first story for more)

## Story: HTML test report generation is efficient (DONE)

HTML report is generated from the binary format, not from XML results

- Change `DefaultTestReport` to use `TestResultsProvider` to get results instead of loading from XML.
- Spike using [jatl](http://code.google.com/p/jatl/) to generate the report instead of using the DOM.
- Change the report rendering so that it copies the test output directly from `TestResultsProvider` to file, rather than loading it into heap.

## Bug GRADLE-2649: Test report shows implementation class for tests run via `@RunWith(Suite.class)` (DONE)

- Change `DecoratingTestDescriptor.getClassName()` to return the delegate's class name.
- Change `TestReportDataCollector.onOutput()` to create a class test result object when the test descriptor has a class name associated with it, and
  there are no results for the test class.

### Coverage

- A JUnit 4 suite `SomeSuite` includes 2 test classes `Class1` and `Class2`, and has @BeforeClass and @AfterClass methods that generate logging.
    - The XML results should include an XML file for `SomeSuite` containing its logging, an XML file for `Class1` containing its tests and their
      logging, and an XML file for `Class2` containing its tests and their logging.
    - The HTML report should include the same information.
- A JUnit 3 suite `SomeSuite` includes 2 test classes, wrapped in a `TestSetup` decorator that generates logging during setup and teardown. The suite includes
  2 test classes `Class1` and `Class2`.
    - The XML results and HTML report should include the same information as the previous test case.
- Multiple JUnit suites that include the same test class `SomeClass`.
    - The XML results should include an XML file for `SomeClass` that includes each test method multiple times, one for each time it was executed.
    - The HTML report should include the same information.

## Story: Add support for JUnit categories (DONE)

- Add `JUnitOptions.includeCategories` and `excludeCategories` properties. These define a set of category types
  to include and exclude, respectively.
- When include categories are specified, a test method is filtered if it or its test class is not annotated with a
  `@Category` annotation where one of the listed types is assignable-to one of the specified include category types.
- When exclude categories are specified, a test method is filtered if it or its test class is annotated with a
  `@Category` annotation where one of the listed types is assignable-to one of the specified exclude category types.
- A test class is filtered when all of its test methods are filtered.
- if include and/or exclude categories are defined but the selected JUnit version does not support categories, a warning is emitted.

### Implementation

1. Add the above filtering in `JUnitTestClassExecuter`.
2. Add documentation to the 'testing' section of the user guide that describes how to use categories with JUnit and
  groups with TestNG.
3. Extend the JUnit test detection so that:
    - When exclude categories have been specified, then filter the following classes:
        - The class is annotated with an exclude category or one of its subtypes OR
        - The class and its supertypes are not annotated with `@RunWith` and all of of the class' declared and inherited
          `@Test` methods are annotated with an exclude category or one of its subtypes.
    - When include categories have been specified, then filter the following classes:
        - The class or its super types are not annotated with `@RunWith` AND
        - The class is not annotated with an include category or one of its subtypes AND
        - None of the class' declared or inherited `@Test` methods annotated with an include category or one
          of its subtypes.

### Test coverage

- When include and exclude categories specified:
    - All test methods from a class annotated with include category are executed.
    - All test methods from a class annotated with include category subtype are executed.
    - No test methods for a class annotated with exclude category are executed.
    - No test methods for a class annotated with exclude category subtype are executed.
    - No test methods for a class annotated with both include and exclude categories are executed.
    - No test methods for a class annotated with some other category are executed.
    - No test methods for a class with no annotations are executed.
    - Test method annotated with include category on class with no annotations is executed.
    - Test method annotated with exclude category on class annotated with include category is not executed.
    - Test method annotated with include category on class annotated with exclude category is not executed.
- When exclude categories specified:
    - All test methods for a class annotated with some other category are executed.
    - All test methods from a class with no annotations are executed.
    - No test methods for a class annotated with exclude category are executed.
    - Test method annotated with include category on class with no annotations is executed.
    - Test method annotated with exclude category on class with no annotations is not executed.
- Class uses custom test runner that creates a tree of tests with a mix of categories.
- Run one filtering test case against multiple JUnit versions in `JUnitCrossVersionIntegrationSpec`.
- Warning is emitted if categories are configured but junit version does not support categories.
- Report includes details when one of the include or exclude annotation types cannot be loaded.
- Report include details when all test classes are excluded
- When a test method is both included and @Ignored, the test method is reported as 'skipped'.

## Story: Add a JaCoCo code coverage plugin

- Merge the [JaCoCo plugin pull request](https://github.com/gradle/gradle/pull/138)
- Add `@Incubating` to public task, plugin and extension types.
- Move tasks to `org.gradle.testing.jacoco.tasks`
- Move plugin and extensions to `org.gradle.testing.jacoco.plugin`
- Move default destination dir from `JacocoMerge` to plugin.
- Move default report dir from `JacocoReport` to plugin.
- Fix up input and output files on `JacocoReport`.
- `JacocoReport` should not use sourcesets to represent source
- Change the reporting task so that it `mustRunAfter` the test task.
- Flip the relationship between `jacoco` plugin and `sonar` plugins.
- Rename the `jacoco` project to `testing`.
- Move the testing infrastructure into this project.
- Change the reporting task to implement `Reporting`.

### Test coverage

- Report is generated when all tests fail.
- Report is generated when one or more tests fail.
- Report is not generated when tests cannot be run.

## Story: `Test` task implements the `Reporting` contract

### Implementation plan

Move reporting configuration into this framework, leaving existing Test properties/methods in place as deprecated facades.

### User visible changes

The Test task reports container will expose the HTML and XML reports…

    test {
      reports {
        html.enabled = false
        xml.enabled = false
      }
    }

The standard `Report` options will be respected. This deprecates Test methods/properties such as `testReportEnabled`, `testReportDir`.

### Test coverage

- HTML and XML reports can be disabled
- Changing HTML report configuration through old properties (e.g. `testReportDisabled`) is respected (and deprecated)

## Story: XML test report shows output per test

This is about providing a way to produce XML like:

    <?xml version="1.1" encoding="UTF-8"?>
    <testsuite name="junit.TheTest" tests="2" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="hasdrubal.local" time="1.366372749405E9">
     <properties/>
     <testcase name="t1" classname="junit.TheTest" time="0.001">
       <system-out><![CDATA[from t1]]></system-out>
     </testcase>
     <testcase name="t2" classname="junit.TheTest" time="0.001">
       <system-out><![CDATA[from t2]]></system-out>
     </testcase>
    </testsuite>

Where as we currently produce:

    <?xml version="1.1" encoding="UTF-8"?>
    <testsuite name="junit.TheTest" tests="1" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="hasdrubal.local" time="1.366372749405E9">
     <properties/>
     <testcase name="t" classname="junit.TheTest" time="0.001"/>
     <testcase name="t2" classname="junit.TheTest" time="0.001/">
     <system-out><![CDATA[from t1
    from t2]]></system-out>
    </testsuite>

Jenkins support the output-nested-under-testcase approach outlined above. Having output in this format will produce better test result reporting in the Jenkins UI.

As this is a communication protocol between Gradle and other tools (e.g. CI servers) changing this format needs to be managed carefully.

### User visible changes

A boolean property named '`outputPerTestCase`' will be added to the `Report` that represents the XML file report.

To enable this feature:

    test {
      reports {
        junitXml.outputPerTestCase true
      }
    }

### Implementation

Our test execution infrastructure already associates output events with test cases. When we serialise the results to disk back in the build process we discard this association and write a single text file for each stream (out & err) of each class. The JUnit XML generator uses these files (along with the binary results file) to generate the XML.

Instead of writing the output to text files, we will serialize the TestOutputEvent into the results binary file.

The data structure that gets serialized must provide the means to reconstruct the ordered "stream" of output events:

1. for the execution of each suite
2. for the execution of each test case within a suite

This implies that a sequence identifier id is added to the association between an output event and the test suite, and the output event and the test case. There is no requirement at this time to be able to reconstruct the ordered stream of output events for an execution (i.e. all tests).

The result data structure needs to support being able to render the HTML and XML reports efficiently. Different ways of structuring the output event storage (e.g. are event children of `TestMethodResult` or `TestClassResult`?) will need to be trialled and measured.

The impact of the new data structure on report generation will need to be measured for both the output-per-suite case and the output-per-test-case case.

### Test coverage

- Test class with multiple methods producing output produces correctly nested output elements in XML (JUnit and TestNG)
- Test class with parameterised methods producing output produces correctly … (JUnit and TestNG)
- Useful output/result when test case names are not unique 
- With `outputPerTestCase` on, output from class level methods (e.g. `@BeforeClass`) is associated with the test class
- With `outputPerTestCase` on, output from test case level methods (e.g. `@Before`) is associated with the test case
- With `outputPerTestCase` off, existing output format is unchanged (should be covered by not introducing failures for our existing tests)
- Output is correctly associated with test cases when a test cases logs output from multiple threads

## Story: A user uses a test execution mode that executes test cases concurrently within the same VM

Our test listening/event infrastructure does not support this in so far that it does not correctly associate output from test cases correctly when firing test output events.

We also do not have a suitable level of test coverage of this kind of test parallelism.

Potential execution modes:

1. JUnit Suite using org.junit.experimental.ParallelComputer (classes and method level computers)
2. TestNG managed parallelism (class and method level)

### Implementation plan

`CaptureTestOutputTestResultProcessor` (and associated classes) will need to handle concurrent output events. 
As output events are generated by tests writing to standard streams, a facade stream will need to be inserted that uses thread local tracking internally to associate output with test cases. 
As tests may spawn their own threads, an inheritable thread local strategy will need to be used.

### User visible changes

Test output is associated with the correct test when test cases are run concurrently in the same VM.

### Test coverage

- When using intra JVM parallelism (types listed above), test output listeners receive test output events associated with the correct test case
  - Output during test cases
  - Output during preflight/postflight methods (e.g. @Before, @BeforeClass etc.)
- Test cases spawn threads that produce output
- When generating JUnit XML report with output per test case, output is correctly associated to test cases

## Story: HTML test report shows number of ignored tests

## Story: HTML test report shows output per test

## Story: HTML test report shows aggregated output (out + err)

-instead of showing separate tabs for out + err, there's a single tab 'output'

### Backwards compatibility:

-depending how the story is implemented, we might drop support for separate err and std output.
It's a breaking change in a way but I don't find the separate err/std output useful.

## Story: HTML test report shows runtime grouping of tests into suites

- Add the tree of test execution to the test report

## Story: Aggregate HTML test report can be generated

- Change test task to persist test results in internal format.
- Add test report task.

## Story: HTML report contains the output from TestNG Reporter

I'm hoping we won't have to implement it. I don't know how popular the Reporter is.
Also the problem can be avoided by using the Reporter methods that also print to the standard output (which is nice because the messages show up in IDE).

## Separate test report generation from test execution

Configure a `TestReport` task to always run after the `Test` task, and change the `TestReport` type to implement `Reporting`. Remove (via deprecation) the reporting from the `Test` task type.

## Bug GRADLE-2524: Missing stuff in TestNG output

### Problem

There are a couple of jira tickets related to the problem with missing stuff in TestNG output.
Typically, those are outputs printed by TestNG listeners (our API enables hooking the listeners)
or outputs printed very early/very late in the game (BeforeSuite/AfterSuite, etc).

### Possible fix

TestNGTestClassProcessor uses CaptureTestOutputTestResultProcessor to capture the output.
However, the latter class is geared towards the JUnit model, where we receive notifications on class start / end.
In TestNG, we only receive notification on entire suite start / end and then on each test method start / end.
This means that with TestNG, the CaptureTestOutputTestResultProcessor is started only when the first test starts.
We could possibly fix it by starting redirecting the output at suite start in the TestNG scenario.

# Other issues

- Provide some way to generate only the old TestNG reports, so that both test report and test XML generation can be disabled.
