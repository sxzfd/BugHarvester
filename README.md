# BugHarvester

BugHarvester is a tool that automatically collects bug-fixing commits (BFCs) from a given open-source Java project. It identifies BFCs that modify a limited number of files and attempts to associate them with bug reports or issues.

## Usage

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/BugHarvester.git
   cd BugHarvester
   ```

2. **Build the project:**
   ```bash
   mvn clean package
   ```

3. **Run the tool:**
   ```bash
   java -jar target/BugHarvester-1.0-SNAPSHOT.jar <repository_url>
   ```
   For example:
   ```bash
   java -jar target/BugHarvester-1.0-SNAPSHOT.jar https://github.com/jhy/jsoup
   ```

   This will create a JSON file named `<repository_name>_bfcs.json` in the current directory, containing the harvested bug-fixing commits.

## How it works

BugHarvester uses the following heuristics to identify bug-fixing commits:

*   **Commit message:** The commit message must contain keywords like "fix", "bug", or "issue".
*   **Number of modified files:** The commit must modify a limited number of files (currently set to 5 or fewer).

For each potential BFC, BugHarvester attempts to extract an issue ID from the commit message (e.g., "#123").

## Dependencies

*   [JGit](https://www.eclipse.org/jgit/): A pure Java library for interacting with Git repositories.
*   [Gson](https://github.com/google/gson): A Java library for converting Java Objects into their JSON representation.
# BugHarvester
