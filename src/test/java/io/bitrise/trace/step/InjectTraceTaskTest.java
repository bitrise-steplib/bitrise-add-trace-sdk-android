package io.bitrise.trace.step;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration;
import org.gradle.api.logging.Logging;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link InjectTraceTask}.
 */
public class InjectTraceTaskTest {

    @BeforeClass
    public static void setup() {
        InjectTraceTask.logger = Logging.getLogger(InjectTraceTaskTest.class.getName());
    }

    // region getSmallestNonNegativeNumber tests
    @Test
    public void getSmallestNonNegativeNumber_onePositive() {
        final int actual = InjectTraceTask.getSmallestNonNegativeNumber(1);
        assertThat(actual, is(1));
    }

    @Test
    public void getSmallestNonNegativeNumber_zero() {
        final int actual = InjectTraceTask.getSmallestNonNegativeNumber(0);
        assertThat(actual, is(0));
    }

    @Test
    public void getSmallestNonNegativeNumber_oneNegative() {
        final int actual = InjectTraceTask.getSmallestNonNegativeNumber(-6);
        assertThat(actual, is(-1));
    }

    @Test
    public void getSmallestNonNegativeNumber_twoPositive() {
        final int actual = InjectTraceTask.getSmallestNonNegativeNumber(1, 2);
        assertThat(actual, is(1));
    }

    @Test
    public void getSmallestNonNegativeNumber_nonNegative() {
        final int actual = InjectTraceTask.getSmallestNonNegativeNumber(1, 0);
        assertThat(actual, is(0));
    }

    @Test
    public void getSmallestNonNegativeNumber_equal() {
        final int actual = InjectTraceTask.getSmallestNonNegativeNumber(0, 0);
        assertThat(actual, is(0));
    }

    @Test
    public void getSmallestNonNegativeNumber_oneNegativeOnePositive() {
        final int actual = InjectTraceTask.getSmallestNonNegativeNumber(4, -5);
        assertThat(actual, is(4));
    }

    @Test
    public void getSmallestNonNegativeNumber_twoNegative() {
        final int actual = InjectTraceTask.getSmallestNonNegativeNumber(-7, -5);
        assertThat(actual, is(-1));
    }

    @Test
    public void getSmallestNonNegativeNumber_mixed() {
        final int actual = InjectTraceTask.getSmallestNonNegativeNumber(1, -2, 3, -4, 5);
        assertThat(actual, is(1));
    }
    // endregion

    // region removeGreedyCommentBlocksFromLine tests

    @Test
    public void removeGreedyCommentBlocksFromLine_none() {
        final String expected = "There is no greedy comment block in this";
        final String actual = InjectTraceTask.removeGreedyCommentBlocksFromLine(expected,
                InjectTraceTask.getGreedyCommentBlockPattern());
        assertThat(actual, is(expected));
    }

    @Test
    public void removeGreedyCommentBlocksFromLine_single() {
        final String line = "There %s is greedy comment block in this";
        final String actual = InjectTraceTask.removeGreedyCommentBlocksFromLine(String.format(line, "/* commented " +
                        "part */"),
                InjectTraceTask.getGreedyCommentBlockPattern());
        assertThat(actual, is(String.format(line, "")));
    }

    @Test
    public void removeGreedyCommentBlocksFromLine_multiple() {
        final String line = "There %1$s are %1$s greedy %1$s comment %1$s block %1$s in this";
        final String actual = InjectTraceTask.removeGreedyCommentBlocksFromLine(String.format(line, "/* commented " +
                        "part */"),
                InjectTraceTask.getGreedyCommentBlockPattern());
        assertThat(actual, is(String.format(line, "")));
    }

    @Test
    public void removeGreedyCommentBlocksFromLine_incompleteNotRemoved() {
        final String line = "There %s is greedy comment start in this";
        final String actual = InjectTraceTask.removeGreedyCommentBlocksFromLine(String.format(line, "/*"),
                InjectTraceTask.getGreedyCommentBlockPattern());
        assertThat(actual, is(String.format(line, "/*")));
    }
    // endregion

    // region removeCommentedCode tests

    private static final String LINE_COMMENT = "//";
    private static final String GREEDY_COMMENT_START = "/*";
    private static final String GREEDY_COMMENT_END = "*/";
    private static final String STRING_CONTENT = "This is a dummy String";
    private static final String STRING_CONTENT_WITH_LITERAL = "def website =\"https://bitrise.io\"";
    private static final String STRING_CONTENT_WITH_CHAR_LITERAL = "def website ='https://bitrise.io'";

    @Test
    public void removeCommentedCode_none() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(STRING_CONTENT);
            add(STRING_CONTENT);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("%1$s\n%1$s\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    //
    @Test
    public void removeCommentedCode_lineCommentAtStart() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(STRING_CONTENT);
            add(LINE_COMMENT + STRING_CONTENT);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("%1$s\n\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    //
    @Test
    public void removeCommentedCode_lineCommentInMiddle() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(STRING_CONTENT);
            add(STRING_CONTENT + LINE_COMMENT + STRING_CONTENT);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("%1$s\n%1$s\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    /* */
    @Test
    public void removeCommentedCode_greedyCommentSingleLine() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(GREEDY_COMMENT_START + STRING_CONTENT + GREEDY_COMMENT_END);
            add(STRING_CONTENT);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("\n%1$s\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    /* */
    @Test
    public void removeCommentedCode_greedyCommentInMiddle() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(STRING_CONTENT + GREEDY_COMMENT_START + STRING_CONTENT + GREEDY_COMMENT_END + STRING_CONTENT);
            add(STRING_CONTENT);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("%1$s%1$s\n%1$s\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    /*
     */
    @Test
    public void removeCommentedCode_greedyCommentTwoLine() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(GREEDY_COMMENT_START + STRING_CONTENT);
            add(STRING_CONTENT + GREEDY_COMMENT_END);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("\n\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    /*

     */
    @Test
    public void removeCommentedCode_greedyCommentMultiline() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(GREEDY_COMMENT_START + STRING_CONTENT);
            add(STRING_CONTENT);
            add(STRING_CONTENT + GREEDY_COMMENT_END);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = "\n\n";
        assertThat(actual, equalTo(expected));
    }

    /* // */
    @Test
    public void removeCommentedCode_mixedCommentInSameLine1() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(GREEDY_COMMENT_START + STRING_CONTENT + LINE_COMMENT + STRING_CONTENT);
            add(STRING_CONTENT + GREEDY_COMMENT_END);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("\n\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    // /*
    @Test
    public void removeCommentedCode_mixedCommentInSameLine2() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(LINE_COMMENT + STRING_CONTENT + GREEDY_COMMENT_START + STRING_CONTENT);
            add(STRING_CONTENT);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("\n%1$s\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    /* */ //
    @Test
    public void removeCommentedCode_mixedCommentInSameLine3() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(GREEDY_COMMENT_START + STRING_CONTENT + GREEDY_COMMENT_END + STRING_CONTENT + LINE_COMMENT + STRING_CONTENT);
            add(STRING_CONTENT);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("%1$s\n%1$s\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    /* */
    //
    @Test
    public void removeCommentedCode_mixedCommentMultiLine1() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(GREEDY_COMMENT_START + STRING_CONTENT + GREEDY_COMMENT_END);
            add(LINE_COMMENT + STRING_CONTENT);
            add(STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("\n\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    /*

    // */
    @Test
    public void removeCommentedCode_mixedCommentMultiLine2() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(GREEDY_COMMENT_START + STRING_CONTENT);
            add(STRING_CONTENT);
            add(LINE_COMMENT + STRING_CONTENT + GREEDY_COMMENT_END + STRING_CONTENT);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = String.format("\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void removeCommentedCode_DoNotModifyStrings() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(STRING_CONTENT_WITH_LITERAL);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = STRING_CONTENT_WITH_LITERAL + "\n";
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void removeCommentedCode_DoNotModifyChars() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add(STRING_CONTENT_WITH_CHAR_LITERAL);
        }};
        final String actual = InjectTraceTask.removeCommentedCode(codeLines);
        final String expected = STRING_CONTENT_WITH_CHAR_LITERAL + "\n";
        assertThat(actual, equalTo(expected));
    }
    // endregion

    // region getIndexOfFromCode tests

    @Test
    public void getIndexOfFromCode_ShouldFind() {
        final int actual = InjectTraceTask.getIndexOfFromCode(STRING_CONTENT, "dummy");
        assertThat(actual, equalTo(10));
    }

    @Test
    public void getIndexOfFromCode_StringLiteral_ShouldNotFind() {
        final int actual = InjectTraceTask.getIndexOfFromCode(STRING_CONTENT_WITH_LITERAL, "http");
        assertThat(actual, equalTo(-1));
    }

    @Test
    public void getIndexOfFromCode_CharLiteral_ShouldNotFind() {
        final int actual = InjectTraceTask.getIndexOfFromCode(STRING_CONTENT_WITH_CHAR_LITERAL, "http");
        assertThat(actual, equalTo(-1));
    }
    // endRegion

    // region hasDependency tests
    private final static String DUMMY_DEPENDENCY_NAME = "dummy-dependency";
    private final static String DUMMY_DEPENDENCY_GROUP_NAME = "io.bitrise.dummy";
    private final static Dependency DUMMY_DEPENDENCY = mock(Dependency.class);

    static {
        when(DUMMY_DEPENDENCY.getName()).thenReturn(DUMMY_DEPENDENCY_NAME);
        when(DUMMY_DEPENDENCY.getGroup()).thenReturn(DUMMY_DEPENDENCY_GROUP_NAME);
    }

    @Test
    public void hasDependency_True() {
        final DefaultConfiguration mockConfiguration = mock(DefaultConfiguration.class);
        final DefaultDependencySet mockDependencySet = mock(DefaultDependencySet.class);
        when(mockDependencySet.iterator()).thenReturn(Collections.singletonList(DUMMY_DEPENDENCY).iterator());
        when(mockConfiguration.getAllDependencies()).thenReturn(mockDependencySet);

        final boolean actualValue = InjectTraceTask.hasDependency(mockConfiguration, DUMMY_DEPENDENCY_NAME,
                DUMMY_DEPENDENCY_GROUP_NAME);
        assertThat(actualValue, is(true));
    }

    @Test
    public void hasDependency_False() {
        final DefaultConfiguration mockConfiguration = mock(DefaultConfiguration.class);
        final DefaultDependencySet mockDependencySet = mock(DefaultDependencySet.class);
        final Dependency someOtherDependency = mock(Dependency.class);
        when(someOtherDependency.getName()).thenReturn(DUMMY_DEPENDENCY_NAME);
        when(someOtherDependency.getGroup()).thenReturn("not.bitrise.group");

        when(mockDependencySet.iterator()).thenReturn(Collections.singletonList(someOtherDependency).iterator());
        when(mockConfiguration.getAllDependencies()).thenReturn(mockDependencySet);

        final boolean actualValue = InjectTraceTask.hasDependency(mockConfiguration, DUMMY_DEPENDENCY_NAME,
                DUMMY_DEPENDENCY_GROUP_NAME);
        assertThat(actualValue, is(false));
    }
    // endregion

    // region getContentToAppend tests
    private static final String DUMMY_GRADLE_FILE_NAME = "dummy.gradle";

    @Test
    public void getContentToAppend_Groovy() {
        final String actual = InjectTraceTask.getContentToAppend("build.gradle", DUMMY_GRADLE_FILE_NAME);
        assertThat(actual, equalTo(String.format("\napply from: \"%s\"", DUMMY_GRADLE_FILE_NAME)));
    }

    @Test
    public void getContentToAppend_Kotlin() {
        final String actual = InjectTraceTask.getContentToAppend("build.gradle.kts", DUMMY_GRADLE_FILE_NAME);
        assertThat(actual, equalTo(String.format("\napply(\"%s\")", DUMMY_GRADLE_FILE_NAME)));
    }

    @Test(expected = IllegalStateException.class)
    public void getContentToAppend_None() {
        InjectTraceTask.getContentToAppend("README.md", DUMMY_GRADLE_FILE_NAME);
    }
    // endregion

    // region updateBuildScriptContent
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final static String DUMMY_BUILD_GRADLE_CONTENT_1 = "\n" +
            "someContent\n" +
            "buildscript {" +
            "%s" +
            "    repositories {\n" +
            "        mavenLocal()\n" +
            "        google()\n" +
            "        mavenCentral()\n" +
            "    }\n" +
            "    dependencies {\n" +
            "        classpath 'com.android.tools.build:gradle:4.0.2'\n" +
            "    }\n" +
            "} " +
            "\nsomeOtherContent";

    private final static String DUMMY_BUILD_GRADLE_CONTENT_2 = "\n" +
            "def stringName = \"buildscript {\"" +
            DUMMY_BUILD_GRADLE_CONTENT_1;

    private final static String DUMMY_BUILD_GRADLE_CONTENT_3 = "\n" +
            "someContent\n" +
            " dependencies {" +
            "   implementation fileTree(dir: 'libs', include: ['*.jar'])\n" +
            "   implementation \"io.bitrise.trace:trace-sdk:0.0.7\"" +
            "}" +
            "\nsomeOtherContent";

    @Test
    public void updateBuildScriptContent_BuildScriptShouldBeUpdated() throws IOException {
        final File tempFile = tempFolder.newFile("build.gradle");
        FileUtils.writeStringToFile(tempFile, String.format(DUMMY_BUILD_GRADLE_CONTENT_1, "\n"),
                Charset.defaultCharset());

        InjectTraceTask.updateBuildScriptContent(tempFile.getPath());

        final String actual = FileUtils.readFileToString(tempFile, Charset.defaultCharset());
        final String expected = String.format(DUMMY_BUILD_GRADLE_CONTENT_1 + "\n",
                InjectTraceTask.getTraceGradlePluginDependency() + InjectTraceTask.getBuildScriptRepositoryContent() + "\n");

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void updateBuildScriptContent_LiteralsShouldNotBeAffected() throws IOException {
        final File tempFile = tempFolder.newFile("build.gradle");
        FileUtils.writeStringToFile(tempFile, String.format(DUMMY_BUILD_GRADLE_CONTENT_2, "\n"),
                Charset.defaultCharset());

        InjectTraceTask.updateBuildScriptContent(tempFile.getPath());

        final String actual = FileUtils.readFileToString(tempFile, Charset.defaultCharset());
        final String expected = String.format(DUMMY_BUILD_GRADLE_CONTENT_2 + "\n",
                InjectTraceTask.getTraceGradlePluginDependency() + InjectTraceTask.getBuildScriptRepositoryContent() + "\n");

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void updateBuildScriptContent_NoMatch() throws IOException {
        final File tempFile = tempFolder.newFile("build.gradle");
        FileUtils.writeStringToFile(tempFile, DUMMY_BUILD_GRADLE_CONTENT_3,  Charset.defaultCharset());

        final boolean actual = InjectTraceTask.updateBuildScriptContent(tempFile.getPath());
        assertThat(actual, equalTo(false));
    }

    @Test
    public void appendContentToTop_ContentShouldBeOnTheTop() throws IOException {
        final File tempFile = tempFolder.newFile("build.gradle");
        FileUtils.writeStringToFile(tempFile, String.format(DUMMY_BUILD_GRADLE_CONTENT_1, "\n"),
                Charset.defaultCharset());

        final String dummyTopContent = "THIS SHOULD BE ON THE TOP";
        final List<String> originalContent = Files.readAllLines(Paths.get(tempFile.getPath()), StandardCharsets.UTF_8);
        InjectTraceTask.appendContentToTop(tempFile.getPath(), dummyTopContent + "\n");

        final List<String> expected = originalContent;
        originalContent.add(0, dummyTopContent);
        final List<String> actual = Files.readAllLines(Paths.get(tempFile.getPath()), StandardCharsets.UTF_8);
        assertEquals(expected, actual);
    }

    @Test
    public void findStringLiterals_EmptyResult() {
        final List<InjectTraceTask.Range> actual = InjectTraceTask.findStringLiterals(STRING_CONTENT);
        final List<InjectTraceTask.Range> expected = new ArrayList<>();

        assertThat(actual, is(expected));
    }

    @Test
    public void findStringLiterals_SingleResult() {
        final List<InjectTraceTask.Range> actual = InjectTraceTask.findStringLiterals(DUMMY_BUILD_GRADLE_CONTENT_1);
        final List<InjectTraceTask.Range> expected = new ArrayList<>();
        expected.add(new InjectTraceTask.Range(151, 189));

        assertThat(actual, is(expected));
    }

    @Test
    public void findStringLiterals_MultiResult() {
        final List<InjectTraceTask.Range> actual = InjectTraceTask.findStringLiterals(DUMMY_BUILD_GRADLE_CONTENT_2);
        final List<InjectTraceTask.Range> expected = new ArrayList<>();
        expected.add(new InjectTraceTask.Range(18, 33));
        expected.add(new InjectTraceTask.Range(184, 222));

        assertThat(actual, is(expected));
    }
    //endregion
}