# Gitlet Design Document

**Rishi Balakrishnan**:

## Classes and Data Structures
### Main
This class handles the terminal commands

**Fields**
1. `State state` - Stores the current state of the directory


###Commit

This class stores the information about each commit

**Fields**
1. `Long metadata` - Stores the date and time of the commit
2. `String parent` - Stores the hash of the parent(s)
3. `String hashcode` - Stores the hash of this commit
4. `String message` - Stores the message of the commit
5. `Hashmap<String, String> files` - Contains a title to hash mapping of each file
6. `int distance` - Stores the distance from commit to first commit


###State
Contains the state of the working directory

**Fields**
1. `ArrayList<String> added` - Names of files to be added
2. `ArrayList<String> removed` - Names of files to be removed
3. `String branch` - Stores the name of the current branch
4. `Hashmap<String, String[]> branches` - Contains mapping from branch title to array of Strings where 
    1. String[0]: commitID of where the branch was created 
    2. String[1]: commitID of latest commit
    3. String[2]: commitID of current commit

 

## Algorithms
###State
1. `void add(String fileName)` - Adds file to staging area
2. `void remove(String fileName)` - Removes file from tracking
3. `void commit()` - Creates commit object, serializes it, updates the branch to commit mapping, and clears the staging area.
4. `void merge(String commitID)`
    1. Compares the distances of the first commits of each branch and finds the branch with the longer distance
    2. Iterates back through that branch to find the split point
    3. Compares files in current branch to split point and given branch and work through cases in project 3 specifications under merge method
5. `void checkoutFile(String fileName)` - Calls checkoutCommitFile for fileName in the head commit for current branch
6. `void checkOutBranch(String branchName)` - calls reset method for the latest commit in the given branch
7. `void checkOutCommitFile (String file, String commit)` - finds file from file to SHA-1 mapping of given commit and overwrites file in working directory 
6. `State()` - Initializes fields and creates the first commit
9. `void newBranch(String branchName)` - enters new branch in branch-to-commitHash hashmap
10. `void rm-Branch(String branchName)` - removes branch from branch to commit hash mapping
11. `void reset(String commitID)` - set head to branches' latest commit and overwrite files in working directory to files that existed for that commit

###Main
1. `void globalLog()` - Iterates through all commits and prints them
2. `void find()` - Iterates through all commits and prints the commits with the right message
3. `void init()` - Creates .gitlet directory and creates new State method



## Persistence
1. Serialize commits after the commit or merge commands. Store the file in the .gitlet/commits directory with its title being the SHA-1 hashing of its metadata
2. Serializing files after the commit or merge commands. Store the files in the .gitlet/files directory with its title being the SHA-1 hashing of the file contents
3. Serializing state after the user enters add, commit, rm, checkout, branch, rm-branch, reset, or merge commands in the terminal. Store the serialized state in the .gitlet directory with file name state 
