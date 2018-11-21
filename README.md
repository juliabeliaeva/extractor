## Extractor

Scripts for extracting a history of a subfolder of a git repository into a separate repository.

#### Motivation

I needed to extract a repository with [MPS Contrib](https://github.com/JetBrains/MPS-Contrib) files from the main [MPS repository](https://github.com/JetBrains/MPS). My requirements were:
* retain file history through renames/moves, even when the files were moved outside of the specified subfolder (this allows to extract multiple folders by moving them into one subfolders first);
* generate a nice readable history without a web of unnecessary merge commits;
* find a solution that will work on a big repository in a reasonable time.

Helpful articles:
* [How to Move Folders Between Git Repositories](http://st-on-it.blogspot.ru/2010/01/how-to-move-folders-between-git.html)
* [git filter-branch '--subdirectory-filter' preserving '--no-ff' merges](http://sgf-dma.blogspot.ru/2012/12/git-filter-branch-subdirectory-filter.html)

#### Used approach

1. For every file extract full history (with renames): all commits where the file was changed plus all the paths to the file (`smartlog.sh`). The output contains two files: `revisions.txt` with commit ids and `history.txt` with file names. File history algorithm that is used here is described in [GitFileHistory.java](https://github.com/JetBrains/intellij-community/blob/18d2398d373ea13d8618e2d5422450ccb53202da/plugins/git4idea/src/git4idea/history/GitFileHistory.java#L50) from [IntelliJ IDEA](https://www.jetbrains.com/idea/) source code.

2. Create a branch only with commits in revisions.txt (`FilterByRevisions.java`). It walks all revisions from HEAD that are in revisions.txt maintaining a set of "roots" -- ends of current branch without parents. Every commit it tries to attach to one of the roots or their children. The output is a bash script `buildtree.sh` which generates a resulting graph of commits using `commit-tree` command.

3. Execute `buildtree.sh` and checkout a `HEAD` of generated graph. If there are several heads, all but one are lost.

4. Filter HEAD by paths and leave only paths from `history.txt`. This is done by `filter.sh`.

#### Usage

Disclaimer: this is a "works on my machine" project, so use at your own risk.

Execute `scripts/main.sh <PATH TO REPOSITORY> <RELATIVE PATH TO THE FOLDER TO EXTRACT>`