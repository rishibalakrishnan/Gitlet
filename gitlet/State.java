package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;
import java.util.Map;

/**
 * Class that maintains state of gitlet program and handles most commands.
 * @author Rishi Balakrishnan
 */
public class State implements Serializable {

    /**
     * Constuctor for the state class.
     * @param hashcode Commit ID of current commit.
     */
    public State(String hashcode) {
        _branch = "master";
        _branches = new HashMap<>();
        String[] status = new String[]{hashcode, hashcode, hashcode};
        _branches.put("master", status);
        _added = new HashMap<>();
        _removed = new HashMap<>();
    }

    /**
     * Adds a file to the staging area.
     * @param fileName file to be added
     */
    public void add(String fileName) {
        if (_removed.containsKey(fileName)) {
            String contents = _removed.get(fileName);
            _removed.remove(fileName);
            File file = new File(Main.WORKING_DIR + "/" + fileName);
            Utils.writeContents(file, contents);
        } else {
            File file = Commit.getFileFromWorking(fileName);
            String contents = Utils.readContentsAsString(file);

            String fileHash = Utils.sha1(fileName + contents);
            Commit current = Commit.getCommit(currentCommit());
            HashMap<String, String> files = current.getFiles();
            if (!fileHash.equals(files.get(fileName))) {
                _added.put(fileName, contents);
            }
        }
    }

    /**
     * Creates a new commit based on staging area.
     * @param metadata Date/time of the commit
     * @param message Message of the commit
     */
    public void commit(Date metadata, String message) {
        if (_added.keySet().size() == 0 && _removed.keySet().size() == 0) {
            throw new GitletException("No changes added to the commit.");
        }
        Commit parent = Commit.getCommit(currentCommit());
        Commit commit = new Commit(metadata, currentCommit(), message, _added,
                _removed, parent);
        setCommit(commit.getHashcode());
        _added.clear();
        _removed.clear();
    }

    /**
     * Method to create a merge commit.
     * @param metadata Date/time of merge commit
     * @param message Message of merge commit
     * @param parent Parents of merge
     * @param split Latest common ancestor of both parents
     */
    private void mergeCommit(Date metadata, String message, String parent,
                             Commit split) {

        if (_added.keySet().size() == 0 && _removed.keySet().size() == 0) {
            throw new GitletException("No changes added to the commit.");
        }
        Commit commit = new Commit(metadata, parent, message, _added, _removed,
                split);
        setCommit(commit.getHashcode());
        _added.clear();
        _removed.clear();
    }

    /**
     * Method to remove file from being tracked in gitlet.
     * @param fileName File to remove
     */
    public void rm(String fileName) {
        boolean staged, tracked;
        staged = tracked = false;
        if (_added.containsKey(fileName)) {
            _added.remove(fileName);
            staged = true;
        }

        Commit currentCommit = Commit.getCommit(currentCommit());
        HashMap<String, String> trackedFiles = currentCommit.getFiles();
        if (trackedFiles.containsKey(fileName)) {
            File file = new File(Main.WORKING_DIR + "/" + fileName);
            String contents;
            try {
                contents = Utils.readContentsAsString(file);
            } catch (IllegalArgumentException e) {
                contents = "";
            }
            _removed.put(fileName, contents);
            Utils.restrictedDelete(file);
            tracked = true;
        }

        if (!staged && !tracked) {
            throw new GitletException("No reason to remove the file.");
        }
    }

    /**
     * Method that prints log of current branch.
     */
    public void log() {
        logHelper(currentCommit());
    }

    /**
     * Recursively presents all parent commits until initial commit.
     * @param hashcode Hashcode of commit to print
     */
    private void logHelper(String hashcode) {
        if (hashcode != null) {
            Commit commit = Commit.getCommit(hashcode);
            System.out.println(commit.logString());
            logHelper(Commit.getFirstParent(hashcode));
        }
    }

    /**
     * Print out all commits ever made.
     */
    public void globalLog() {
        File commitDir = new File(Main.COMMIT_PATH);
        File[] commits = commitDir.listFiles();
        for (File commit : commits) {
            Commit current = Utils.readObject(commit, Commit.class);
            System.out.println(current.logString());
        }
    }

    /**
     * Prints ids of commits with given message.
     * @param message Message to search for
     */
    public void find(String message) {
        boolean found = false;
        File commitDir = new File(Main.COMMIT_PATH);
        File[] commits = commitDir.listFiles();
        for (File commit : commits) {
            Commit current = Utils.readObject(commit, Commit.class);
            if (current.getMessage().equals(message)) {
                found = true;
                System.out.println(current.getHashcode());
            }
        }

        if (!found) {
            throw new GitletException("Found no commit with that message.");
        }
    }

    /**
     * Merges two branches together to create a new commit.
     * @param other The branch to merge with the current branch
     */
    public void merge(String other) {
        if (_added.size() > 0 || _removed.size() > 0) {
            throw new GitletException("You have uncommitted changes.");
        } else if (other.equals(_branch)) {
            throw new GitletException("Cannot merge a branch with itself.");
        } else if (!_branches.containsKey(other)) {
            throw new GitletException("A branch with that name "
                    + "does not exist.");
        }

        Commit currentCommit = Commit.getCommit(_branches.get(_branch)[1]);
        HashMap<String, String> currentFiles = currentCommit.getFiles();

        Commit otherCommit = Commit.getCommit(_branches.get(other)[1]);
        HashMap<String, String> otherFiles = otherCommit.getFiles();

        List<String> workingFiles = Utils.plainFilenamesIn(Main.WORKING_DIR);
        for (String name : workingFiles) {
            if (!tracked(currentFiles, name) && otherFiles.containsKey(name)) {
                throw new GitletException("There is an untracked file in the "
                        + "way; delete it, or add and commit it first.");
            }
        }

        Commit split = findSplitPoint(other);
        HashMap<String, String> splitFiles = split.getFiles();

        for (String fileName : otherFiles.keySet()) {
            String otherFile = otherFiles.get(fileName);
            String splitFile = splitFiles.get(fileName);
            if (!currentFiles.containsKey(fileName)
                    && !checkEquals(otherFile, splitFile)) {
                checkoutCommitFile(otherCommit.getHashcode(), fileName);
                add(fileName);
            }
        }

        mergeFiles(currentFiles, otherFiles, splitFiles, currentCommit,
                otherCommit);

        String parent = currentCommit.getHashcode() + " "
                + otherCommit.getHashcode();
        String message = "Merged " + other + " into " + _branch + ".";
        mergeCommit(new Date(), message, parent, currentCommit);

    }

    /**
     * Merges files for two commits together.
     * @param currentFiles Files of current commit
     * @param otherFiles Files of other commit
     * @param splitFiles Files of split point commit
     * @param currentCommit Current commit
     * @param otherCommit Other commit
     */
    private void mergeFiles(HashMap<String, String> currentFiles,
                            HashMap<String, String> otherFiles,
                            HashMap<String, String> splitFiles,
                            Commit currentCommit, Commit otherCommit) {
        boolean mergeConflict = false;
        for (String fileName : currentFiles.keySet()) {
            String currentHash = currentFiles.get(fileName);
            String otherHash = otherFiles.get(fileName);
            String splitHash = splitFiles.get(fileName);
            if (currentHash.equals(splitHash) && otherHash == null) {
                rm(fileName);
            } else if (otherHash != null && !otherHash.equals(splitHash)
                    && checkEquals(splitHash, currentHash)) {
                checkoutCommitFile(otherCommit.getHashcode(), fileName);
                add(fileName);
            } else if (!currentHash.equals(otherHash)
                    && !checkEquals(otherHash, splitHash)) {
                mergeConflict = true;
                File cFile = currentCommit.getFile(fileName);
                String currentContents = Utils.readContentsAsString(cFile);
                String otherContents = (otherHash == null) ? ""
                        : Utils.readContentsAsString(
                                otherCommit.getFile(fileName));
                String finalContent = "<<<<<<< HEAD\n" + currentContents
                        + "=======\n" + otherContents + ">>>>>>>\n";
                File workingFile = Commit.getFileFromWorking(fileName);
                Utils.writeContents(workingFile, finalContent);
                add(fileName);
            }
        }
        if (mergeConflict) {
            System.out.println("Encountered a merge conflict.");
        }

    }

    /**
     * Checks whether given file is tracked.
     * @param currentFiles HashMap of files tracked in current commit
     * @param name Name of file to check
     * @return wheter the file is tracked or not
     */
    private boolean tracked(HashMap<String, String> currentFiles, String name) {
        return currentFiles.containsKey(name) || _added.containsKey(name)
                || _removed.containsKey(name);
    }

    /**
     * Whether two strings are both null or equal to each other.
     * @param str1 String 1
     * @param str2 String 2
     * @return whether the strings are equal
     */
    private boolean checkEquals(String str1, String str2) {
        return Objects.equals(str1, str2);
    }

    /**
     * Finds the split point of two branches.
     * @param other The other branch to check.
     * @return Latest common ancestor of both branches.
     */
    private Commit findSplitPoint(String other) {
        doSplitPointChecks(other);
        HashSet<String> otherHistory = new HashSet<>();
        String otherHead = _branches.get(other)[1];
        getTotalHistory(otherHead, otherHistory);
        String hash = searchForMatch(otherHistory, _branches.get(_branch)[1]);
        return Commit.getCommit(hash);

    }

    /**
     * Returns commit ID of the split point.
     * @param history Commit history of other branch.
     * @param hash Head of current branch
     * @return Commit ID of splitpoint
     */
    private String searchForMatch(HashSet<String> history, String hash) {
        ArrayList<String> toSearch = new ArrayList<>();
        toSearch.add(hash);
        while (true) {
            ArrayList<String> next = new ArrayList<>();
            for (String search: toSearch) {
                if (history.contains(search)) {
                    return search;
                } else if (search != null) {
                    next.add(Commit.getFirstParent(search));
                    if (Commit.getSecondParent(search) != null) {
                        next.add(Commit.getSecondParent(search));
                    }
                } else {
                    throw new GitletException("Could not find split point.");
                }
            }
            toSearch = next;
        }
    }

    /**
     * Checks for various cases where merge doesn't need to happen.
     * @param other the given branch to merge.
     */
    private void doSplitPointChecks(String other) {
        HashSet<String> currentCommits = getBranchCommits(_branch);
        String currentHead = _branches.get(_branch)[1];
        HashSet<String> otherCommits = getBranchCommits(other);
        String otherHead = _branches.get(other)[1];
        if (currentHead.equals(otherHead)) {
            throw new GitletException("Given branch is an ancestor of the "
                    + "current branch.");
        } else if (!otherCommits.contains(currentHead)
                && currentCommits.contains(otherHead)) {
            throw new GitletException("Given branch is an ancestor of the "
                    + "current branch.");
        } else if (otherCommits.contains(currentHead)
                && !currentCommits.contains(otherHead)) {
            checkoutBranch(other);
            throw new GitletException("Current branch fast-forwarded");
        }
    }

    /**
     * Gets history of branch from Head to first commit of branch.
     * @param branch Branch to parse.
     * @return HashSet containing commits from start to end of branch
     */
    private HashSet<String> getBranchCommits(String branch) {
        HashSet<String> commits = new HashSet<>();
        Commit current = Commit.getCommit(_branches.get(branch)[1]);

        Commit start = Commit.getCommit(_branches.get(branch)[0]);
        while (!current.getHashcode().equals(start.getHashcode())) {
            commits.add(current.getHashcode());
            current = Commit.getCommit(current.getFirstParent());
        }
        commits.add(current.getHashcode());
        return commits;
    }

    /**
     * Gets total history of branch from latest commit to initial gitlet commit.
     * @param hash Head of given branch
     * @param history Hashset to store history
     */
    private void getTotalHistory(String hash, HashSet<String> history) {
        if (hash == null || history.contains(hash)) {
            return;
        }
        history.add(hash);
        getTotalHistory(Commit.getFirstParent(hash), history);
        getTotalHistory(Commit.getSecondParent(hash), history);
    }

    /**
     * Prints status of the working directory.
     */
    public void status() {
        System.out.println("=== Branches ===");
        printSortedSet(_branches.keySet(), _branch);
        System.out.println("\n=== Staged Files ===");
        printSortedSet(_added.keySet(), "");
        System.out.println("\n=== Removed Files ===");
        printSortedSet(_removed.keySet(), "");
        System.out.println("\n=== Modifications Not Staged For Commit ===");
        modifiedFiles();
        System.out.println("\n=== Untracked Files ===");
        untrackedFiles();
        System.out.println();
    }

    /**
     * Prints the untracked files in the working directory.
     */
    private void untrackedFiles() {
        List<String> workingFiles = Utils.plainFilenamesIn(Main.WORKING_DIR);
        Commit currentCommit = Commit.getCommit(currentCommit());
        HashMap<String, String> files = currentCommit.getFiles();
        for (String name : workingFiles) {
            if (!tracked(files, name)) {
                System.out.println(name);
            }
        }
    }

    /**
     * Prints files modified from last commit.
     */
    private void modifiedFiles() {
        Commit currentCommit = Commit.getCommit(currentCommit());
        HashMap<String, String> files = currentCommit.getFiles();
        List<String> workingFiles = Utils.plainFilenamesIn(Main.WORKING_DIR);
        for (String name : files.keySet()) {
            if (!workingFiles.contains(name) && !_removed.containsKey(name)) {
                System.out.println(name + " (deleted)");
            } else if (workingFiles.contains(name)) {
                File file = Commit.getFileFromWorking(name);
                String contents = Utils.readContentsAsString(file);
                String hash = Utils.sha1(name + contents);
                if (!hash.equals(files.get(name))) {
                    System.out.println(name + " (modified)");
                }
            }
        }

    }


    /**
     * Prints Set in alphabetical order with one element starred.
     * @param set Set to print
     * @param check Element to star
     */
    private void printSortedSet(Set<String> set, String check) {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        for (String str : list) {
            if (str.equals(check)) {
                System.out.println("*" + str);
            } else {
                System.out.println(str);
            }
        }
    }

    /**
     * Creates new branch and attaches it to current commit.
     * @param name Name of new branch
     */
    public void branch(String name) {
        if (_branches.containsKey(name)) {
            throw new GitletException("A branch with that name already "
                    + "exists.");
        }

        String commit = currentCommit();
        String[] branchStatus = new String[]{commit, commit, commit};
        _branches.put(name, branchStatus);
    }

    /**
     * Removes branch specified.
     * @param name branch to remove
     */
    public void rmBranch(String name) {
        if (!_branches.containsKey(name)) {
            throw new GitletException("A branch with that name does "
                    + "not exist.");
        } else if (name.equals(_branch)) {
            throw new GitletException("Cannot remove the current branch.");
        }

        _branches.remove(name);
    }

    /**
     * Checks out the file, commit, or branch the user specified.
     * @param commands User inputted commands from terminal.
     */
    public void checkout(String[] commands) {
        validateCheckout(commands);

        if (commands.length == 2) {
            checkoutBranch(commands[1]);
        } else if (commands.length == 3) {
            checkoutFile(commands[2]);
        } else {
            checkoutCommitFile(commands[1], commands[3]);
        }
    }

    /**
     * Makes sure that the checkout commands are valid.
     * @param commands String array of terminal commands
     */
    private void validateCheckout(String[] commands) {
        if (commands.length == 3 && !commands[1].equals("--")) {
            throw new GitletException("Incorrect operands.");
        } else if (commands.length == 4 && !commands[2].equals("--")) {
            throw new GitletException("Incorrect operands.");
        }
    }

    /**
     * Checks out a branch, and updates current branch in state.
     * @param name Branch name to check out
     */
    private void checkoutBranch(String name) {
        if (name.equals(_branch)) {
            throw new GitletException("No need to checkout the current "
                    + "branch.");
        }

        if (!_branches.containsKey(name)) {
            throw new GitletException("No such branch exists.");
        }

        checkoutTotalCommit(_branches.get(name)[1]);
        _branch = name;
        setCommit(_branches.get(name)[1]);
    }


    /**
     * Checks out files from given commit and deletes tracked files not present.
     * Method called by checkoutBranch and reset methods
     * @param hash ID of commit to checkout
     */
    private void checkoutTotalCommit(String hash) {
        Commit oldCommit = Commit.getCommit(currentCommit());
        HashMap<String, String> oldFiles = oldCommit.getFiles();
        Commit commit = Commit.getCommit(hash);
        HashMap<String, String> files = commit.getFiles();

        List<String> workingFiles = Utils.plainFilenamesIn(Main.WORKING_DIR);

        for (String name : workingFiles) {
            if (files.containsKey(name) && !tracked(oldFiles, name)) {
                throw new GitletException("There is an untracked file in the "
                        + "way; delete it, or add and commit it first.");
            }
        }

        for (String fileName : oldFiles.keySet()) {
            if (!files.containsKey(fileName)) {
                Utils.restrictedDelete(Main.WORKING_DIR + "/" + fileName);
            }
        }

        for (Map.Entry<String, String> entry : files.entrySet()) {
            checkoutCommitFile(commit.getHashcode(), entry.getKey());
        }

        _added.clear();
        _removed.clear();
    }

    /**
     * Resets working directory and state to given commit.
     * @param hash Commit ID to reset to
     */
    public void reset(String hash) {
        checkoutTotalCommit(hash);
        setCommit(hash);
    }

    /**
     * Checkouts out file from head of branch.
     * @param name of file to check out
     */
    private void checkoutFile(String name) {
        checkoutCommitFile(currentCommit(), name);
    }

    /**
     * Checks out file from given commit.
     * @param commit ID of commit to check out file from
     * @param fileName name of file to check out
     */
    private void checkoutCommitFile(String commit, String fileName) {
        Commit from = Commit.getCommit(commit);
        File newFile = from.getFile(fileName);
        try {
            File oldFile = Commit.getFileFromWorking(fileName);
            Utils.writeContents(oldFile, Utils.readContentsAsString(newFile));
        } catch (GitletException e) {
            File createdFile = new File(Main.WORKING_DIR, fileName);
            try {
                createdFile.createNewFile();
                Utils.writeContents(createdFile,
                        Utils.readContentsAsString(newFile));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Serializes state and writes it into state file.
     * @param state State object to serialize
     */
    public static void saveState(State state) {
        File stateFile = new File(Main.STATE_PATH);
        if (!stateFile.exists()) {
            try {
                stateFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Utils.writeObject(stateFile, state);
    }

    /**
     * Retrieves state from state file.
     * @return State object
     */
    public static State retrieveState() {
        File stateFile = new File(Main.STATE_PATH);
        if (!stateFile.exists()) {
            throw new GitletException("State not initialized");
        }

        return Utils.readObject(stateFile, State.class);
    }

    /**
     * Returns commit id of head of current branch.
     * @return Commit ID of head of branch
     */
    public String currentCommit() {
        return _branches.get(_branch)[1];
    }

    /**
     * Sets the head commit of current branch.
     * @param hash Commit ID of new head.
     */
    private void setCommit(String hash) {
        _branches.get(_branch)[1] = hash;
    }

    /** HashMap storing fileName and content of all added files. */
    private HashMap<String, String> _added;
    /** HashMap storing fileName and content of all removed files. */
    private HashMap<String, String> _removed;
    /** Name of current branch. */
    private String _branch;
    /** Maps branches to their first and latest commits. */
    private HashMap<String, String[]> _branches;
}
