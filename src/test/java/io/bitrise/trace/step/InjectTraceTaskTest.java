package io.bitrise.trace.step;

import org.junit.Test;

import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Test cases for {@link InjectTraceTask}.
 */
public class InjectTraceTaskTest {

    //region getSmallestNonNegativeNumber tests
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
    //endregion

    //region removeGreedyCommentBlocksFromLine tests

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
    //endregion

    //region removeCommentedCode tests

    private static final String LINE_COMMENT = "//";
    private static final String GREEDY_COMMENT_START = "/*";
    private static final String GREEDY_COMMENT_END = "*/";
    private static final String STRING_CONTENT = "This is a dummy String";

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
        final String expected = String.format("\n\n%1$s\n", STRING_CONTENT);
        assertThat(actual, equalTo(expected));
    }

    /* */ //
    @Test
    public void removeCommentedCode_mixedCommentInSameLine3() {
        final ArrayList<String> codeLines = new ArrayList<String>() {{
            add( GREEDY_COMMENT_START +  STRING_CONTENT  +GREEDY_COMMENT_END + STRING_CONTENT + LINE_COMMENT+ STRING_CONTENT);
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
    //endregion
}