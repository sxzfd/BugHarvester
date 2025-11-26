package com.bugharvester;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.kohsuke.github.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BugHarvester {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar BugHarvester.jar <repository_url> [oauth_token]");
            return;
        }

        String repoUrl = args[0];
        String oauthToken = args.length > 1 ? args[1] : null;
        String repoName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1);

        try {
            List<BugFixingCommit> bfcs = harvestBugFixingCommits(repoUrl, oauthToken);
            writeBFCsToJson(bfcs, repoName + "_bfcs.json");
            System.out.println("Successfully harvested " + bfcs.size() + " bug-fixing commits.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<BugFixingCommit> harvestBugFixingCommits(String repoUrl, String oauthToken) throws IOException {
        GitHub github;
        if (oauthToken != null && !oauthToken.isEmpty()) {
            github = new GitHubBuilder().withOAuthToken(oauthToken).build();
        } else {
            github = GitHub.connectAnonymously();
        }
        return harvestBugFixingCommits(github, repoUrl);
    }

    static List<BugFixingCommit> harvestBugFixingCommits(GitHub github, String repoUrl) throws IOException {
        System.out.println("Starting to harvest bug-fixing commits from " + repoUrl);
        List<BugFixingCommit> bfcs = new ArrayList<>();
        String repoName = repoUrl.replace("https://github.com/", "").replaceAll("\\.git$", "");
        GHRepository repository = github.getRepository(repoName);
        System.out.println("Connected to repository: " + repository.getFullName());

        String searchQuery = String.format("repo:%s is:issue is:closed label:bug", repoName);
        System.out.println("Executing search query: " + searchQuery);
        PagedSearchIterable<GHIssue> bugIssues = github.searchIssues().q(searchQuery).list();
        System.out.println("Found " + bugIssues.getTotalCount() + " bug issues.");

        System.out.println("Processing bug issues...");
        int issueCount = 0;
        for (GHIssue issue : bugIssues) {
            issueCount++;
            System.out.println("Processing issue " + issueCount + ": #" + issue.getNumber());
            for (GHIssueEvent event : issue.listEvents()) {
                if ("closed".equals(event.getEvent()) && event.getCommitId() != null) {
                    try {
                        GHCommit commit = repository.getCommit(event.getCommitId());
                        bfcs.add(new BugFixingCommit(commit.getSHA1(), commit.getCommitShortInfo().getMessage(), String.valueOf(issue.getNumber())));
                    } catch (IOException e) {
                        System.err.println("Could not retrieve commit " + event.getCommitId() + " for issue #" + issue.getNumber() + ". Skipping.");
                    }
                    break;
                }
            }
        }

        System.out.println("Finished processing all issues.");
        return bfcs;
    }

    private static void writeBFCsToJson(List<BugFixingCommit> bfcs, String fileName) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(fileName)) {
            gson.toJson(bfcs, writer);
        }
    }

    static class BugFixingCommit {
        final String commitHash;
        final String commitMessage;
        final String issueId;

        public BugFixingCommit(String commitHash, String commitMessage, String issueId) {
            this.commitHash = commitHash;
            this.commitMessage = commitMessage;
            this.issueId = issueId;
        }
    }
}
