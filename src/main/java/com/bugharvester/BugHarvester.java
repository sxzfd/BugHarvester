package com.bugharvester;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.kohsuke.github.*;

import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BugHarvester {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar BugHarvester.jar <command> [options]");
            System.err.println("Commands:");
            System.err.println("  harvest <repository_url> [oauth_token]");
            System.err.println("  analyze <repository_url> <commit_hash>");
            System.err.println("  verify <repository_url> <bfc_json_file>");
            return;
        }

        String command = args[0];
        switch (command) {
            case "harvest":
                if (args.length < 2) {
                    System.err.println("Usage: java -jar BugHarvester.jar harvest <repository_url> [oauth_token]");
                    return;
                }
                String repoUrl = args[1];
                String oauthToken = args.length > 2 ? args[2] : null;
                String repoName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1);

                try {
                    List<BugFixingCommit> bfcs = harvestBugFixingCommits(repoUrl, oauthToken);
                    writeBFCsToJson(bfcs, repoName + "_bfcs.json");
                    System.out.println("Successfully harvested " + bfcs.size() + " bug-fixing commits.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case "analyze":
                if (args.length < 3) {
                    System.err.println("Usage: java -jar BugHarvester.jar analyze <repository_url> <commit_hash>");
                    return;
                }
                String analyzeRepoUrl = args[1];
                String commitHash = args[2];
                ProjectAnalyzer analyzer = new ProjectAnalyzer(analyzeRepoUrl);
                analyzer.checkout(commitHash);
                analyzer.buildAndTest();
                break;
            case "verify":
                if (args.length < 3) {
                    System.err.println("Usage: java -jar BugHarvester.jar verify <repository_url> <bfc_json_file>");
                    return;
                }
                String verifyRepoUrl = args[1];
                String bfcJsonFile = args[2];

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                List<VerifiedBugFixingCommit> verifiedBFCs = new ArrayList<>();
                try (FileReader reader = new FileReader(bfcJsonFile)) {
                    Type bfcListType = new TypeToken<ArrayList<BugFixingCommit>>(){}.getType();
                    List<BugFixingCommit> bfcsToVerify = gson.fromJson(reader, bfcListType);

                    for (BugFixingCommit bfc : bfcsToVerify) {
                        System.out.println("Verifying BFC: " + bfc.commitHash);
                        BFCVerifier verifier = new BFCVerifier(verifyRepoUrl, bfc);
                        boolean isVerified = verifier.verify();
                        verifiedBFCs.add(new VerifiedBugFixingCommit(bfc, isVerified));
                        System.out.println("BFC " + bfc.commitHash + " verified: " + isVerified);
                    }
                }

                String outputFileName = bfcJsonFile.replace(".json", "_verified.json");
                try (FileWriter writer = new FileWriter(outputFileName)) {
                    gson.toJson(verifiedBFCs, writer);
                }
                System.out.println("Verification results written to " + outputFileName);
                break;
            default:
                System.err.println("Unknown command: " + command);
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

    static class VerifiedBugFixingCommit extends BugFixingCommit {
        boolean verified;

        public VerifiedBugFixingCommit(BugFixingCommit bfc, boolean verified) {
            super(bfc.commitHash, bfc.commitMessage, bfc.issueId);
            this.verified = verified;
        }
    }
}
