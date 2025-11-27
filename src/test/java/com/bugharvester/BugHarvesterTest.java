package com.bugharvester;

import com.bugharvester.BugHarvester.BugFixingCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BugHarvesterTest {

    @Mock
    private GitHub github;

    @Mock
    private GHRepository repository;

    @Mock
    private GHIssueSearchBuilder searchBuilder;

    @Mock
    private GHIssue issue;

    @Mock
    private GHIssueEvent event;

    @Mock
    private GHCommit commit;

    @Mock
    private GHCommit.ShortInfo shortInfo;

    @Mock
    private PagedSearchIterable<GHIssue> pagedSearchIterable;

    @Mock
    private PagedIterator<GHIssue> pagedIterator;

    @Mock
    private PagedIterable<GHIssueEvent> pagedEvents;

    @Mock
    private PagedIterator<GHIssueEvent> pagedEventsIterator;

    @Test
    public void testHarvestBugFixingCommits() throws IOException {
        String repoUrl = "https://github.com/user/repo";

        when(github.getRepository("user/repo")).thenReturn(repository);
        when(github.searchIssues()).thenReturn(searchBuilder);
        when(searchBuilder.q("repo:user/repo is:issue is:closed label:bug")).thenReturn(searchBuilder);
        when(searchBuilder.list()).thenReturn(pagedSearchIterable);
        when(pagedSearchIterable.iterator()).thenReturn(pagedIterator);
        when(pagedIterator.hasNext()).thenReturn(true, false);
        when(pagedIterator.next()).thenReturn(issue);

        when(issue.listEvents()).thenReturn(pagedEvents);
        when(pagedEvents.iterator()).thenReturn(pagedEventsIterator);
        when(pagedEventsIterator.hasNext()).thenReturn(true, false);
        when(pagedEventsIterator.next()).thenReturn(event);

        when(event.getEvent()).thenReturn("closed");
        when(event.getCommitId()).thenReturn("commit_id");
        when(repository.getCommit("commit_id")).thenReturn(commit);
        when(commit.getSHA1()).thenReturn("commit_hash");
        when(commit.getCommitShortInfo()).thenReturn(shortInfo);
        when(shortInfo.getMessage()).thenReturn("Fixes #123");
        when(issue.getNumber()).thenReturn(123);

        List<BugFixingCommit> bfcs = BugHarvester.harvestBugFixingCommits(github, repoUrl);

        assertEquals(1, bfcs.size());
        BugFixingCommit bfc = bfcs.get(0);
        assertEquals("commit_hash", bfc.commitHash);
        assertEquals("Fixes #123", bfc.commitMessage);
        assertEquals("123", bfc.issueId);
    }
}
