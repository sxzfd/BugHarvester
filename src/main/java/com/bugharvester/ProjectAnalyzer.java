package com.bugharvester;

import java.io.File;
import java.io.IOException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

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
}
