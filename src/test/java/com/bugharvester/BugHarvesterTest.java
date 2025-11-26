package com.bugharvester;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;

public class BugHarvesterTest {

    @Test
    public void testExtractIssueId() throws Exception {
        Method extractIssueIdMethod = BugHarvester.class.getDeclaredMethod("extractIssueId", String.class);
        extractIssueIdMethod.setAccessible(true);

        String messageWithIssueId = "Fix bug #123";
        String issueId = (String) extractIssueIdMethod.invoke(null, messageWithIssueId);
        assertEquals("123", issueId);

        String messageWithoutIssueId = "Fix a bug";
        issueId = (String) extractIssueIdMethod.invoke(null, messageWithoutIssueId);
        assertNull(issueId);

        String messageWithMultipleIssueIds = "Fix bug #123 and #456";
        issueId = (String) extractIssueIdMethod.invoke(null, messageWithMultipleIssueIds);
        assertEquals("123", issueId);
    }
}
