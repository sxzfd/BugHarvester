package com.bugharvester;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;

public class BFCVerifier {

    private final String repoUrl;
    private final BugHarvester.BugFixingCommit bfc;
    private final ProjectAnalyzer analyzer;

    public BFCVerifier(String repoUrl, BugHarvester.BugFixingCommit bfc) throws GitAPIException, IOException {
        this.repoUrl = repoUrl;
        this.bfc = bfc;
        this.analyzer = new ProjectAnalyzer(repoUrl);
    }

    public boolean verify() throws IOException, InterruptedException, GitAPIException {
        RevCommit parentCommit = analyzer.getParent(bfc.commitHash);
        if (parentCommit == null) {
            System.out.println("BFC " + bfc.commitHash + " has no parent. Skipping.");
            return false;
        }

        List<DiffEntry> diffs = analyzer.getDiff(parentCommit.getName(), bfc.commitHash);
        List<String> testFiles = diffs.stream()
                .map(diff -> diff.getChangeType() == DiffEntry.ChangeType.ADD || diff.getChangeType() == DiffEntry.ChangeType.MODIFY ? diff.getNewPath() : null)
                .filter(path -> path != null && (path.contains("src/test") || path.toLowerCase().contains("test")))
                .collect(Collectors.toList());

        if (testFiles.isEmpty()) {
            System.out.println("No new or modified test files found in BFC " + bfc.commitHash + ". Skipping.");
            return false;
        }

        analyzer.checkout(parentCommit.getName());

        for (String testFile : testFiles) {
            String testFileContent = analyzer.getFileContentAtCommit(bfc.commitHash, testFile);
            if (testFileContent != null) {
                Path filePath = Paths.get(analyzer.getRepoDir().getPath(), testFile);
                Files.createDirectories(filePath.getParent());
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(testFileContent);
                }
            }
        }

        boolean parentTestsPassWithMigratedTests = analyzer.buildAndTest();

        analyzer.checkout(bfc.commitHash);
        boolean bfcTestsPass = analyzer.buildAndTest();

        return !parentTestsPassWithMigratedTests && bfcTestsPass;
    }
}
