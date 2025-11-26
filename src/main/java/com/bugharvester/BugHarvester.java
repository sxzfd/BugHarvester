package com.bugharvester;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BugHarvester {

    private static final int MAX_FILES_CHANGED = 5;
    private static final Pattern ISSUE_ID_PATTERN = Pattern.compile("#(\\d+)");

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar BugHarvester.jar <repository_url>");
            return;
        }

        String repoUrl = args[0];
        String repoName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1);
        File repoDir = new File(repoName);

        try {
            Git git = cloneRepository(repoUrl, repoDir);
            List<BugFixingCommit> bfcs = harvestBugFixingCommits(git.getRepository());
            writeBFCsToJson(bfcs, repoName + "_bfcs.json");
            System.out.println("Successfully harvested " + bfcs.size() + " bug-fixing commits.");
        } catch (GitAPIException | IOException e) {
            e.printStackTrace();
        }
    }

    private static Git cloneRepository(String repoUrl, File repoDir) throws GitAPIException {
        if (repoDir.exists()) {
            System.out.println("Repository already exists. Opening existing repository.");
            try {
                return Git.open(repoDir);
            } catch (IOException e) {
                throw new GitAPIException("Error opening existing repository", e) {
                };
            }
        } else {
            System.out.println("Cloning repository from " + repoUrl);
            return Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(repoDir)
                    .call();
        }
    }

    private static List<BugFixingCommit> harvestBugFixingCommits(Repository repository) throws GitAPIException, IOException {
        List<BugFixingCommit> bfcs = new ArrayList<>();
        Git git = new Git(repository);
        Iterable<RevCommit> commits = git.log().all().call();

        for (RevCommit commit : commits) {
            if (isBugFixingCommit(commit, repository)) {
                String issueId = extractIssueId(commit.getFullMessage());
                bfcs.add(new BugFixingCommit(commit.getName(), commit.getFullMessage(), issueId));
            }
        }

        return bfcs;
    }

    private static boolean isBugFixingCommit(RevCommit commit, Repository repository) throws IOException {
        String message = commit.getFullMessage().toLowerCase();
        if (!(message.contains("fix") || message.contains("bug") || message.contains("issue"))) {
            return false;
        }

        if (commit.getParentCount() == 0) {
            return false;
        }

        RevCommit parent = commit.getParent(0);
        DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        diffFormatter.setRepository(repository);
        diffFormatter.setContext(0);
        List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());

        return diffs.size() <= MAX_FILES_CHANGED;
    }

    private static String extractIssueId(String message) {
        Matcher matcher = ISSUE_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static void writeBFCsToJson(List<BugFixingCommit> bfcs, String fileName) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(fileName)) {
            gson.toJson(bfcs, writer);
        }
    }

    private static class BugFixingCommit {
        private final String commitHash;
        private final String commitMessage;
        private final String issueId;

        public BugFixingCommit(String commitHash, String commitMessage, String issueId) {
            this.commitHash = commitHash;
            this.commitMessage = commitMessage;
            this.issueId = issueId;
        }
    }
}
