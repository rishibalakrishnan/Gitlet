package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Class for handling all the commit operations in gitlet.
 * @author rishibalakrishnan
 */
public class Commit implements Serializable {

    /**
     * Creates new commit.
     * @param metadata Date/time of commit creation
     * @param parent Commit ID(s) of parent commit(s)
     * @param message Commit message
     * @param added Filenames and contents of files staged for commit
     * @param removed Filenames and contents of files staged for removal
     * @param from The commit to copy files from
     */
    Commit(Date metadata, String parent, String message,
           HashMap<String, String> added, HashMap<String, String> removed,
           Commit from) {

        _metadata = metadata;
        _parent = parent;
        _message = message;
        _hashcode = Utils.sha1(metadata.toString());

        if (metadata.equals(new Date(0))) {
            _files = new HashMap<>();
        } else  {
            handleFiles(added, removed, from);
        }

        saveCommit(this);
    }

    /**
     * Starts tracking added files, stops tracking removed files.
     * @param added Files to save
     * @param removed Files to remove
     * @param from Commit that this commit should be compared to
     */
    @SuppressWarnings("unchecked")
    private void handleFiles(HashMap<String, String> added,
                             HashMap<String, String> removed, Commit from) {

        HashMap<String, String> parentFiles = from.getFiles();
        _files = (HashMap<String, String>) parentFiles.clone();

        for (String fileName : added.keySet()) {
            String hash = createFile(fileName, added.get(fileName));
            _files.put(fileName, hash);
        }

        for (String fileName : removed.keySet()) {
            _files.remove(fileName);
        }

    }

    /**
     * Creates a new blob to store file and text.
     * @param fileName filename of new file
     * @param text Contents of file
     * @return Hash of file, which is also its title in storage
     */
    private String createFile(String fileName, String text) {
        String hash = Utils.sha1(fileName + text);
        File newFile = new File(Main.FILES_PATH + "/" + hash + ".txt");
        Utils.writeContents(newFile, text);
        return hash;
    }

    /**
     * Gets specified file from working directory.
     * @param name File to retrieve
     * @return File object
     */
    public static File getFileFromWorking(String name) {
        List<String> list = Utils.plainFilenamesIn(Main.WORKING_DIR);
        File toReturn = null;
        for (String file : list) {
            if (file.equals(name)) {
                toReturn = new File(Main.WORKING_DIR + "/" + name);
            }
        }
        if (toReturn == null) {
            throw new GitletException("File does not exist.");
        }
        return toReturn;
    }

    /**
     * Retrieves commit with given hash from commit directory.
     * @param hash Commit id of commit to retrieve
     * @return Commit object
     */
    public static Commit getCommit(String hash) {
        File commits = new File(Main.COMMIT_PATH);
        File[] files = commits.listFiles();
        File commit = null;
        for (File file : files) {
            String name = file.getName();
            if (name.substring(0, hash.length()).equals(hash)) {
                commit = file;
            }
        }
        if (commit == null) {
            throw new GitletException("No commit with that ID exists.");
        }
        Commit toReturn = Utils.readObject(commit, Commit.class);
        return toReturn;
    }

    /**
     * Returns string to be used by log function.
     * @return String containing information about the commit
     */
    public String logString() {
        String toReturn = "===\ncommit " + getHashcode() + "\n";
        if (_parent != null) {
            String[] parents = _parent.split(" ");
            if (parents.length == 2) {
                toReturn += "Merge: " + parents[0].substring(0, 7) + " "
                        + parents[1].substring(0, 7) + "\n";
            }
        }
        toReturn += "Date: " + formatDate() + "\n" + _message + "\n";
        return toReturn;
    }

    /**
     * Returns formatted version of metadata.
     * @return formatted date
     */
    private String formatDate() {
        DateFormat format = new
                SimpleDateFormat("E MMM d HH:mm:ss yyyy Z");
        return format.format(_metadata);
    }

    /**
     * Returns commit's hashcode.
     * @return The hashcode
     */
    public String getHashcode() {
        return _hashcode;
    }

    /**
     * Returns the first parent of commit if any.
     * @return Commit ID of first parent
     */
    public String getFirstParent() {
        String toReturn = _parent;
        if (toReturn != null) {
            String[] parents = toReturn.split(" ");
            toReturn = parents[0];
        }
        return toReturn;
    }

    /**
     * Returns the second parent of commit if any.
     * @return Second parent of this commit
     */
    public String getSecondParent() {
        if (_parent == null) {
            return null;
        }

        String[] parents = _parent.split(" ");
        String toReturn = (parents.length > 1) ? parents[1] : null;
        return toReturn;
    }

    /**
     * Static method that returns first parent of specified commit.
     * @param hash Commit ID of child commit
     * @return Commit ID of first parent
     */
    public static String getFirstParent(String hash) {
        Commit commit = getCommit(hash);
        return commit.getFirstParent();
    }

    /**
     * Static method that returns second parent of specified commit.
     * @param hash Commit ID of child commit
     * @return commit ID of second parent
     */
    public static String getSecondParent(String hash) {
        Commit commit = getCommit(hash);
        return commit.getSecondParent();
    }

    /**
     * Returns files tracked by this commit.
     * @return HashMap containing tracked files.
     */
    public HashMap<String, String> getFiles() {
        return _files;
    }

    /**
     * Returns message of this commit.
     * @return Commit message
     */
    public String getMessage() {
        return _message;
    }

    /**
     * Returns version of filename stored by this commit.
     * @param fileName String containing name of file to retrieve
     * @return File wanted by user
     */
    public File getFile(String fileName) {
        if (!_files.containsKey(fileName)) {
            throw new GitletException("File does not exist in that commit");
        }

        String hash = _files.get(fileName);
        File file = new File(Main.FILES_PATH + "/" + hash + ".txt");
        return file;

    }


    /**
     * Serializes commit and saves it in commit directory.
     * @param commit Commit to save
     */
    public static void saveCommit(Commit commit) {
        File commitFile = new File(Main.COMMIT_PATH + "/"
                + commit.getHashcode() + ".txt");
        Utils.writeObject(commitFile, commit);
    }

    /** Metadata of when this commit was created.*/
    private Date _metadata;
    /** String containing parent(s) of this commit. */
    private String _parent;
    /** Hashcode of this commit. */
    private String _hashcode;
    /** Message of this commit. */
    private String _message;
    /** Maps files tracked to their hashcode. */
    private HashMap<String, String> _files;
    /** Boolean whether commit has a second parent. */
}
