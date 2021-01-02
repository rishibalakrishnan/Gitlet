package gitlet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Rishi Balakrishnan
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) {
        try {
            new Main().processCommands(args);
            return;
        } catch (GitletException e) {
            System.out.println(e.getMessage());
        }
        System.exit(0);
    }

    /**
     * @param commands terminal input to the program
     */
    private void processCommands(String[] commands) {
        if (commands.length == 0) {
            throw new GitletException("Please enter a command.");
        } else if (commands[0].equals("init") && commands.length > 1) {
            throw new GitletException("Incorrect operands.");
        } else if (!gitletExists() && !commands[0].equals("init")) {
            throw new GitletException("Not in an initialized "
                    + "Gitlet directory.");
        } else if (commands[0].equals("init")) {
            initialize();
        }

        validateCommands(commands);
        _state = State.retrieveState();

        switch (commands[0]) {
        case "init":
            break;
        case "add":
            _state.add(commands[1]);
            break;
        case "commit":
            _state.commit(new Date(), commands[1]);
            break;
        case "rm":
            _state.rm(commands[1]);
            break;
        case "log":
            _state.log();
            break;
        case "global-log":
            _state.globalLog();
            break;
        case "find":
            _state.find(commands[1]);
            break;
        case "status":
            _state.status();
            break;
        case "checkout":
            _state.checkout(commands);
            break;
        case "branch":
            _state.branch(commands[1]);
            break;
        case "rm-branch":
            _state.rmBranch(commands[1]);
            break;
        case "reset":
            _state.reset(commands[1]);
            break;
        case "merge":
            _state.merge(commands[1]);
            break;
        default:
            throw new GitletException("No command with that name exists.");
        }

        State.saveState(_state);
    }

    /**
     * Method to ensure valid user commands.
     * @param args String array of commands
     */
    private void validateCommands(String[] args) {
        ArrayList<String> allCommands = new ArrayList<String>(Arrays.asList(
                "init", "add", "commit", "rm", "log", "global-log", "find",
                "status", "checkout", "branch", "rm-branch", "reset", "merge"));
        if (!allCommands.contains(args[0])) {
            throw new GitletException("No command with that name exists.");
        }

        ArrayList<String> oneArg = new ArrayList<>(Arrays.asList("add",
                "commit", "rm", "find", "branch", "rm-branch", "reset",
                "merge"));
        if (args[0].equals("checkout")
                && (args.length < 2 || args.length > 4)) {
            throw new GitletException("Incorrect operands.");
        } else if (args[0].equals("commit") && (args.length == 1
                || args[1].equals(""))) {
            throw new GitletException("Please enter a commit message.");
        } else if (oneArg.contains(args[0]) && args.length != 2) {
            throw new GitletException("Incorrect operands.");
        } else if (!oneArg.contains(args[0]) && !args[0].equals("checkout")
                && args.length != 1) {
            throw new GitletException("Incorrect operands.");
        }
    }

    /**
     * Checks whether gitlet is already initialized.
     * @return Status of .gitlet directory
     */
    private boolean gitletExists() {
        return new File(GITLET_DIR).exists();
    }

    /**
     * Method to initialize a gitlet directory in working directory.
     */
    private void initialize() {
        File gitlet = new File(GITLET_DIR);
        if (gitlet.exists()) {
            throw new GitletException("Gitlet version-control system already "
                    + "exists in the current directory.");
        } else {
            gitlet.mkdir();
            File commits = new File(COMMIT_PATH);
            commits.mkdir();
            File files = new File(FILES_PATH);
            files.mkdir();
            HashMap<String, String> empty = new HashMap<String, String>();
            Commit init = new Commit(new Date(0), null,
                    "initial commit", empty, empty, null);
            _state = new State(init.getHashcode());
            State.saveState(_state);
        }
    }


    /** Stores the state of the program. */
    private State _state;
    /** Path to the working directory. */
    public static final String WORKING_DIR = System.getProperty("user.dir");
    /** Path to .gitlet subdirectory.*/
    public static final String GITLET_DIR = WORKING_DIR + "/.gitlet";
    /** Path to subdirectory storing commits. */
    public static final String COMMIT_PATH = WORKING_DIR + "/.gitlet/commits";
    /** Path to subdirectory storing blobs. */
    public static final String FILES_PATH = WORKING_DIR + "/.gitlet/files";
    /** Path to file containing serialized state. */
    public static final String STATE_PATH = WORKING_DIR + "/.gitlet/state.txt";

}
