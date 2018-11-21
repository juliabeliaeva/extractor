package org.jetbrains.extractor;

import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public class FilterByRevisions {

    public static void main(String[] args) throws IOException {
        String repositoryFile = args[0];
        String revisionsFile = args[1];
        String outputFile = args[2];
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
        Repository repository = repositoryBuilder.setGitDir(new File(repositoryFile, ".git")).readEnvironment().findGitDir().build();
        RevWalk walk = new RevWalk(repository);
        walk.sort(RevSort.TOPO);
        walk.markStart(walk.parseCommit(repository.resolve("HEAD")));

        Set<String> revisions = readRevisions(revisionsFile);
        walk.setRevFilter(new MyRevFilter(revisions));

        ArrayList<RevCommit> commitsInOrder = new ArrayList<>();
        for (RevCommit commit : walk) {
            commitsInOrder.add(commit);
        }

        Map<RevCommit, Set<RevCommit>> parentsMap = new LinkedHashMap<>();
        Map<RevCommit, Set<RevCommit>> childrenMap = new LinkedHashMap<>();
        Set<RevCommit> roots = new LinkedHashSet<>();

        for (RevCommit current : commitsInOrder) {
            System.out.print(".");
            Set<RevCommit> followers = new LinkedHashSet<>();
            for (RevCommit root : roots) {
                Queue<RevCommit> queue = new LinkedList<>();
                queue.add(root);
                while (!queue.isEmpty()) {
                    RevCommit c = queue.poll();
                    if (walk.isMergedInto(current, c)) {
                        followers.add(c);
                    } else {
                        Set<RevCommit> nexts = childrenMap.get(c);
                        if (nexts != null && !nexts.isEmpty()) {
                            queue.addAll(nexts);
                        }
                    }
                }

                Set<RevCommit> toRemove = new HashSet<>();
                for (RevCommit tip : followers) {
                    for (RevCommit base : followers) {
                        if (base != tip && walk.isMergedInto(base, tip)) {
                            toRemove.add(tip);
                        }
                    }
                }
                followers.removeAll(toRemove);
            }
//            System.out.println(followers.size());
            roots.removeAll(followers);
            roots.add(current);

            for (RevCommit following : followers) {
                // add to parent map
                Set<RevCommit> parents = parentsMap.computeIfAbsent(following, k -> new LinkedHashSet<>());
                parents.add(current);
            }
            // add to children map
            Set<RevCommit> children = childrenMap.computeIfAbsent(current, k -> new LinkedHashSet<>());
            children.addAll(followers);
        }

        System.out.println();

        // just a check
        for (int commitIndex = commitsInOrder.size() - 2; commitIndex >= 0; commitIndex--) {
            RevCommit commit = commitsInOrder.get(commitIndex);
            Set<RevCommit> parents = parentsMap.get(commit);
            if (parents == null || parents.isEmpty()) {
                System.out.println("commit without parent " + commitIndex + " " + commit.getId().getName());
                for (int j = commitIndex + 1; j < commitsInOrder.size(); j++) {
                    RevCommit parent = commitsInOrder.get(j);
                    if (walk.isMergedInto(parent, commit)) {
                        parents = new HashSet<>();
                        parentsMap.put(commit, parents);
                        parents.add(parent);
                        break;
                    }
                }
            }

        }

        for (RevCommit commit : commitsInOrder) {
            Set<RevCommit> children = childrenMap.get(commit);
            if (children == null || children.isEmpty()) {
                System.out.println("commit without children " + commitsInOrder.indexOf(commit) + " " + commit.getId().getName());
            }

        }

        StringBuilder scriptContent = new StringBuilder();
        scriptContent.append("#!/bin/bash\n\n");

        scriptContent.append("commit");
        scriptContent.append(commitsInOrder.size() - 1);
        scriptContent.append("=");
        scriptContent.append(commitsInOrder.get(commitsInOrder.size() - 1).getId().getName());
        scriptContent.append("\n\n");

        for (int commitIndex = commitsInOrder.size() - 2; commitIndex >= 0; commitIndex--) {
            RevCommit commit = commitsInOrder.get(commitIndex);

            scriptContent.append("export GIT_AUTHOR_NAME=\"");
            scriptContent.append(commit.getAuthorIdent().getName());
            scriptContent.append("\"\n");
            scriptContent.append("export GIT_AUTHOR_EMAIL=\"");
            scriptContent.append(commit.getAuthorIdent().getEmailAddress());
            scriptContent.append("\"\n");
            scriptContent.append("export GIT_AUTHOR_DATE=\"");
            scriptContent.append(commit.getAuthorIdent().getWhen().toString());
            scriptContent.append("\"\n");
            scriptContent.append("export GIT_COMMITTER_NAME=\"");
            scriptContent.append(commit.getCommitterIdent().getName());
            scriptContent.append("\"\n");
            scriptContent.append("export GIT_COMMITTER_EMAIL=\"");
            scriptContent.append(commit.getCommitterIdent().getEmailAddress());
            scriptContent.append("\"\n");
            scriptContent.append("export GIT_COMMITTER_DATE=\"");
            scriptContent.append(commit.getCommitterIdent().getWhen().toString());
            scriptContent.append("\"\n");

            scriptContent.append("commit");
            scriptContent.append(commitIndex);
            scriptContent.append("=$(echo -e '");
            scriptContent.append(commit.getFullMessage().replace("\n", "\\n").replace("'", "\\047"));
            scriptContent.append("' | git commit-tree ");
            scriptContent.append(commit.getTree().getId().getName());
            scriptContent.append(" ");
            // parents
            for (RevCommit parent : parentsMap.get(commit)) {
                scriptContent.append("-p $commit");
                scriptContent.append(commitsInOrder.indexOf(parent));
                scriptContent.append(" ");
            }
            scriptContent.append(")\n");
            scriptContent.append("echo ");
            scriptContent.append(commitIndex);
            scriptContent.append(" $commit");
            scriptContent.append(commitIndex);
            scriptContent.append("\n");
        }

        File buildTreeScript = new File(outputFile);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(buildTreeScript), Charset.forName("UTF-8")))) {
            writer.print(scriptContent.toString());
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<String> readRevisions(String revisionsFileName) {
        Set<String> revisions = new HashSet<>();
        File revisionsFile = new File(revisionsFileName);
        try (BufferedReader reader = new BufferedReader(new FileReader(revisionsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                revisions.add(line);
            }

            return revisions;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class MyRevFilter extends RevFilter {
        private final Set<String> myCommitIds = new HashSet<>();

        MyRevFilter(Collection<String> ids) {
            myCommitIds.addAll(ids);
        }

        @Override
        public boolean include(RevWalk walker, RevCommit commit) throws StopWalkException {
            return myCommitIds.contains(commit.getId().getName());
        }

        @Override
        public RevFilter clone() {
            return new MyRevFilter(myCommitIds);
        }
    }
}
