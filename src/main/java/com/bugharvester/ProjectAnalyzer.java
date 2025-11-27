package com.bugharvester;

import java.io.File;
import java.io.IOException;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

public class ProjectAnalyzer {

    private final Git git;
    private final File repoDir;

    public ProjectAnalyzer(String repoUrl) throws GitAPIException, IOException {
        String repoName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1).replaceAll("\\.git$", "");
        this.repoDir = new File(repoName);
        this.git = cloneOrOpenRepository(repoUrl, this.repoDir);
    }

    private Git cloneOrOpenRepository(String repoUrl, File repoDir) throws GitAPIException, IOException {
        if (repoDir.exists()) {
            System.out.println("Repository already exists. Opening existing repository.");
            return Git.open(repoDir);
        } else {
            System.out.println("Cloning repository from " + repoUrl);
            return Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(repoDir)
                    .call();
        }
    }

    public void checkout(String commitHash) throws GitAPIException {
        System.out.println("Checking out commit: " + commitHash);
        git.checkout().setName(commitHash).call();
    }

    public String detectBuildSystem() {
        if (new File(repoDir, "pom.xml").exists()) {
            return "Maven";
        } else if (new File(repoDir, "build.gradle").exists() || new File(repoDir, "build.gradle.kts").exists()) {
            return "Gradle";
        } else {
            return "Unknown";
        }
    }

    public boolean buildAndTest() {
        String buildSystem = detectBuildSystem();
        System.out.println("Detected build system: " + buildSystem);

        try {
            switch (buildSystem) {
                case "Maven":
                    return runCommand("mvn clean install");
                case "Gradle":
                    return runCommand("./gradlew build");
                default:
                    System.err.println("Unsupported build system.");
                    return false;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean runCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.directory(repoDir);
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        return exitCode == 0;
    }

    public File getRepoDir() {
        return repoDir;
    }

    public RevCommit getParent(String commitHash) throws IOException {
        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            RevCommit commit = revWalk.parseCommit(ObjectId.fromString(commitHash));
            if (commit.getParentCount() > 0) {
                return revWalk.parseCommit(commit.getParent(0).getId());
            }
            return null;
        }
    }

    public List<DiffEntry> getDiff(String oldCommitHash, String newCommitHash) throws GitAPIException, IOException {
        ObjectId oldId = git.getRepository().resolve(oldCommitHash + "^{tree}");
        ObjectId newId = git.getRepository().resolve(newCommitHash + "^{tree}");

        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, oldId);
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, newId);

            return git.diff()
                    .setNewTree(newTreeIter)
                    .setOldTree(oldTreeIter)
                    .call();
        }
    }

    public String getFileContentAtCommit(String commitHash, String filePath) throws IOException {
        ObjectId commitId = ObjectId.fromString(commitHash);
        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(filePath));
                if (!treeWalk.next()) {
                    return null;
                }
                ObjectId objectId = treeWalk.getObjectId(0);
                byte[] bytes = git.getRepository().open(objectId).getBytes();
                return new String(bytes);
            }
        }
    }
}
