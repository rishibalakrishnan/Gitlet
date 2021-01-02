package gitlet;

import ucb.junit.textui;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;

import static org.junit.Assert.*;

/** The suite of all JUnit tests for the gitlet package.
 *  @author rishibalakrishnan
 */
public class UnitTest {

    /** Run the JUnit tests in the loa package. Add xxxTest.class entries to
     *  the arguments of runClasses to run other JUnit tests. */
    public static void main(String[] ignored) {
        System.exit(textui.runClasses(UnitTest.class));
    }

    /** Checks commit functionality. */
    @Test
    public void stateTest() {
        HashMap<String, String> empty = new HashMap<>();
        State state = new State(Utils.sha1(new Date().toString()));
        assertTrue(state != null);
    }

}


