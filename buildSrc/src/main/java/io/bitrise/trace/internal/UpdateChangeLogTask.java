package io.bitrise.trace.internal;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;

public class UpdateChangeLogTask extends DefaultTask {

    private final Logger logger;

    @Inject
    public UpdateChangeLogTask() {
        this.logger = getProject().getLogger();
    }

    @TaskAction
    public void taskAction() throws IOException {
        logger.lifecycle("Starting the update of CHANGELOG.md");
        final Git git = getGit();
        final Ref tag3 = getAllTags(git).get(2); //TODO change to last tag
        final String releaseName = getReleaseName(tag3);
        logger.lifecycle("The name of the release in the CHANGELOG.md will be: {}", releaseName);
        final List<RevCommit> newCommits = getNewCommits(git, tag3);
        logger.lifecycle("Found {} commits since last release", newCommits.size());
        final List<String> changeLogEntries = formatCommitsToChangeLogEntries(newCommits);
        logger.lifecycle("Formatted commit messages to CHANGELOG entries");
        final File changeLogFile = getChangeLogFile();
        updateChangeLog(changeLogFile, releaseName, changeLogEntries);
        logger.lifecycle("CHANGELOG entries added, finishing task");
    }

    private List<RevCommit> getNewCommits(final Git git, final Ref fromTag) throws IOException {
        final RevWalk revWalk = getAllCommits(git);
        return getNewCommits(revWalk, fromTag);
    }

    private List<RevCommit> getNewCommits(final RevWalk revWalk, final Ref fromTag) throws IOException {
        RevCommit next = revWalk.next();
        final List<RevCommit> newCommits = new ArrayList<>();
        while (next != null) {
            if (fromTag.getObjectId().equals(next.getId())) {
                // Found the tag
                break;
            }
            newCommits.add(next);
            next = revWalk.next();
        }
        return newCommits;
    }

    private Git getGit() throws IOException {
        final Git git = Git.open(new File("./.git"));
        git.checkout();
        return git;
    }

    private RevWalk getAllCommits(final Git git) throws IOException {
        try (final RevWalk revWalk = new RevWalk(git.getRepository())) {
            revWalk.markStart(revWalk.parseCommit(git.getRepository().resolve("HEAD")));
            return revWalk;
        }
    }

    private Ref getLastTag(final Git git) throws IOException {
        final List<Ref> allTags = getAllTags(git);
        return allTags.get(allTags.size() - 1);
    }

    private List<Ref> getAllTags(final Git git) throws IOException {
        return git.getRepository().getRefDatabase().getRefsByPrefix("refs/tags/");
    }

    private String getReleaseName(final Ref tag) {
        //refs/tags/0.0.3
        final String tagShortName = tag.getName().substring(10);
        return String.format("# %s - %s", tagShortName, getCurrentDate());
    }

    private String getCurrentDate() {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd");
        return simpleDateFormat.format(Calendar.getInstance(TimeZone.getDefault()).getTime());
    }

    private void updateChangeLog(final File changeLogFile, final String releaseName,
                                 final List<String> changeLogEntries) throws IOException {
        final List<String> lines = getFileLines(changeLogFile);
        final List<String> newContent = getUpdatedChangeLogContent(lines, releaseName, changeLogEntries);
        updateFileLines(changeLogFile, newContent);
    }

    private List<String> getFileLines(final File file) throws IOException {
        return Files.readAllLines(Paths.get(file.getPath()), StandardCharsets.UTF_8);
    }

    private void updateFileLines(final File file, final List<String> newContent) throws IOException {
        Files.write(Paths.get(file.getPath()), newContent, StandardCharsets.UTF_8);
    }

    private List<String> getUpdatedChangeLogContent(final List<String> originalLines, final String releaseName,
                                                    final List<String> newEntries) {
        final int initialPos = 3;
        originalLines.add(initialPos, releaseName);

        for (int i = 0; i < newEntries.size(); i++) {
            originalLines.add(initialPos + 1 + i, newEntries.get(i));
        }

        originalLines.add(initialPos + 1 + newEntries.size(),"");
        return originalLines;
    }

    private List<String> formatCommitsToChangeLogEntries(final List<RevCommit> commitsToAdd) {
        final String regexString = "([^:]*):(.*)\n\n((.|\n)*)";
        final String footerRegex = "(\n|\n)APM-[\\d]+(\n|\r|$)";
        final Pattern pattern = Pattern.compile(regexString);
        final Pattern footerPattern = Pattern.compile(footerRegex);
        return commitsToAdd.stream()
                           .map(it -> formatCommitToChangeLogEntry(it.getFullMessage(), pattern, footerPattern))
                           .filter(Objects::nonNull)
                           .collect(Collectors.toList());
    }

    private String formatCommitToChangeLogEntry(final String commitMessage, final Pattern pattern,
                                                final Pattern footerPattern) {
        final Matcher matcher = pattern.matcher(commitMessage);
        if (matcher.find()) {
            final String commitType = matcher.group(1).trim();
            final String title = matcher.group(2).trim();
            final String messageWithFooter = removeFooter(matcher.group(3).trim(), footerPattern);
            return String.format("* %s: **%s:** %s", commitType, title, messageWithFooter);
        } else {
            logger.warn("Could not parse commit message, ignoring. Please add manually to the CHANGELOG.md if it is " +
                    "required! The message:\n{}", commitMessage);
            return null;
        }
    }

    private String removeFooter(final String message, final Pattern footerPattern) {
        final Matcher matcher = footerPattern.matcher(message);
        return matcher.replaceAll("").trim();
    }

    private File getChangeLogFile() {
        return new File("./CHANGELOG.md");
    }
}
