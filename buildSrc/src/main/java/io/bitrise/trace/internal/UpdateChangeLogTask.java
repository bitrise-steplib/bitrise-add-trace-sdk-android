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
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * Internal task for updating the CHANGELOG.md of this repository. Run this task before release.
 */
public class UpdateChangeLogTask extends DefaultTask {

    private final Logger logger;
    private final Set<String> allowedCommitTypes = new HashSet<>();

    @Inject
    public UpdateChangeLogTask() {
        this.logger = getProject().getLogger();
        initAllowedCommitTypes();
    }

    /**
     * Initialises {@link #allowedCommitTypes} with the conventional commit types that should be in the CHANGELOG.md.
     *
     * @see <a href=https://www.conventionalcommits.org/en/v1.0.0/>https://www.conventionalcommits.org/en/v1.0.0/</a>
     */
    private void initAllowedCommitTypes() {
        final Set<String> allowedCommitTypes = new HashSet<>(Arrays.asList("fix", "feat"));
        this.allowedCommitTypes.addAll(allowedCommitTypes);
        allowedCommitTypes.forEach(it -> this.allowedCommitTypes.add(it + "!"));
    }

    /**
     * Does the update of the CHANGELOG.md. All commits since the previous tag will be collected, and the ones with
     * the allowed type ({@link #allowedCommitTypes}) will be added to the CHANGELOG.md.
     *
     * @throws IOException if any I/O error occurs.
     */
    @TaskAction
    public void taskAction() throws IOException {
        logger.lifecycle("Starting the update of CHANGELOG.md");
        final Git git = getGit();
        final Ref lastTag = getLastTag(git);
        final String releaseName = getReleaseName(lastTag);
        logger.lifecycle("The name of the release in the CHANGELOG.md will be: {}", releaseName);
        final List<RevCommit> newCommits = getNewCommits(git, lastTag);
        logger.lifecycle("Found {} commits since last release", newCommits.size());
        if (newCommits.size() == 0) {
            logger.warn("No new commits found, nothing to update, cancelling task");
            return;
        }
        final List<String> changeLogEntries = formatCommitsToChangeLogEntries(newCommits);
        logger.lifecycle("Formatted commit messages to CHANGELOG entries");
        final File changeLogFile = getChangeLogFile();
        updateChangeLog(changeLogFile, releaseName, changeLogEntries);
        logger.lifecycle("CHANGELOG entries added, finishing task");
    }

    /**
     * Gets the List of {@link RevCommit}s that happened after the given tag.
     *
     * @param git     the {@link Git} repository.
     * @param fromTag the given tag.
     * @return the List of commits.
     * @throws IOException if any I/O error occurs.
     */
    private List<RevCommit> getNewCommits(final Git git, final Ref fromTag) throws IOException {
        final RevWalk revWalk = getAllCommits(git);
        return getNewCommits(revWalk, fromTag);
    }

    /**
     * Gets the List of {@link RevCommit}s that happened after the given tag.
     *
     * @param revWalk the related {@link RevWalk}.
     * @param fromTag the given tag.
     * @return the List of commits.
     * @throws IOException if any I/O error occurs.
     */
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

    /**
     * Gets the {@link Git} to work with (this repo).
     *
     * @return this Git.
     * @throws IOException if any I/O error occurs.
     */
    private Git getGit() throws IOException {
        final Git git = Git.open(new File("./.git"));
        git.checkout();
        return git;
    }

    /**
     * Creates a {@link RevWalk} that contains all the commits on this branch (till the HEAD).
     *
     * @param git the given {@link Git}.
     * @return the created REvWalk.
     * @throws IOException if any I/O error occurs.
     */
    private RevWalk getAllCommits(final Git git) throws IOException {
        try (final RevWalk revWalk = new RevWalk(git.getRepository())) {
            revWalk.markStart(revWalk.parseCommit(git.getRepository().resolve("HEAD")));
            return revWalk;
        }
    }

    /**
     * Gets the last tag (which was created the latest).
     *
     * @param git the given {@link Git}.
     * @return the {@link Ref} of the tag.
     * @throws IOException if any I/O error occurs.
     */
    private Ref getLastTag(final Git git) throws IOException {
        final List<Ref> allTags = getAllTags(git);
        return allTags.get(allTags.size() - 1);
    }

    /**
     * Gets all tags of a given {@link Git}.
     *
     * @param git the given Git.
     * @return the List of tag {@link Ref}s.
     * @throws IOException if any I/O error occurs.
     */
    private List<Ref> getAllTags(final Git git) throws IOException {
        return git.getRepository().getRefDatabase().getRefsByPrefix("refs/tags/");
    }

    /**
     * Gets the name of the given release. The method {@link Ref#getName()} returns the full form, for example
     * "refs/tags/0.0.3", which will be clipped to be just the tag name, in this example "0.0.3". This is concated
     * with the current date.
     *
     * @param tag the given tag
     * @return the name of the release.
     */
    private String getReleaseName(final Ref tag) {
        final String tagShortName = tag.getName().substring(10);
        return String.format("# %s - %s", tagShortName, getCurrentDate());
    }

    /**
     * Gets the current date in a YYYY-MM-DD format.
     *
     * @return the String value of the current date.
     */
    private String getCurrentDate() {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd");
        return simpleDateFormat.format(Calendar.getInstance(TimeZone.getDefault()).getTime());
    }

    /**
     * Updates the given change log File, with a new release.
     *
     * @param changeLogFile    the given File.
     * @param releaseName      the name of the newly added release.
     * @param changeLogEntries the List of change log entries.
     * @throws IOException if any I/O error occurs.
     */
    private void updateChangeLog(final File changeLogFile, final String releaseName,
                                 final List<String> changeLogEntries) throws IOException {
        final List<String> lines = getFileLines(changeLogFile);
        final List<String> newContent = getUpdatedChangeLogContent(lines, releaseName, changeLogEntries);
        updateFileLines(changeLogFile, newContent);
    }

    /**
     * Gets the content of a given File and returns it as a List of Strings.
     *
     * @param file the given File.
     * @return the content of the File as a List of Strings.
     * @throws IOException if any I/O error occurs.
     */
    private List<String> getFileLines(final File file) throws IOException {
        return Files.readAllLines(Paths.get(file.getPath()), StandardCharsets.UTF_8);
    }

    /**
     * Updates the given File with the given List of Strings.
     *
     * @param file       the given File.
     * @param newContent the List of Strings that will be the content of the File.
     * @throws IOException if any I/O error occurs.
     */
    private void updateFileLines(final File file, final List<String> newContent) throws IOException {
        Files.write(Paths.get(file.getPath()), newContent, StandardCharsets.UTF_8);
    }

    /**
     * Creates and returns the new content for a change log file, based on the inputs.
     *
     * @param originalLines the original content of the File, in a List of Strings.
     * @param releaseName   the name of the release that will be added to the CHANGELOG.md.
     * @param newEntries    the new entries that should be appended to the CHANGELOG.md.
     * @return the updated content for the CHANGELOG.md.
     */
    private List<String> getUpdatedChangeLogContent(final List<String> originalLines, final String releaseName,
                                                    final List<String> newEntries) {
        final int initialPos = 3;
        originalLines.add(initialPos, releaseName);

        if (newEntries.size() == 0) {
            logger.warn("No commits found, with the allowed types, only adding the release name to the CHANGELOG.md");
            originalLines.add(initialPos + 1, "* Maintenance release, no fixes or new features");
        } else {
            for (int i = 0; i < newEntries.size(); i++) {
                originalLines.add(initialPos + 1 + i, newEntries.get(i));
            }
        }

        originalLines.add(initialPos + 1 + newEntries.size(), "");
        return originalLines;
    }

    /**
     * Formats the given {@link RevCommit}s to readable change log entries.
     *
     * @param commitsToAdd the commits that should be added to the CHANGELOG.md.
     * @return the formatted change log entries.
     */
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

    /**
     * Formats a given commit message to readable change log entry. Example message:
     *
     * <pre>
     * feat!: Rename input project_path
     *
     * Renamed input project_path to project_location.
     *
     * APM-2426
     * </pre>
     * <p>
     * Example for the formatted result:
     * <pre>
     *   * feat!: **Rename input project_path:** Renamed input project_path to project_location.
     * </pre>
     *
     * @param commitMessage the given commit message to format.
     * @param pattern       a compiled regex pattern for the message.
     * @param footerPattern a compiled regex pattern for the footer of the message.
     * @return the formatted change log entry.
     */
    private String formatCommitToChangeLogEntry(final String commitMessage, final Pattern pattern,
                                                final Pattern footerPattern) {
        final Matcher matcher = pattern.matcher(commitMessage);
        if (matcher.find()) {
            final String commitType = matcher.group(1).trim();
            final String title = matcher.group(2).trim();
            logger.debug("Commit type is \n{}\n, title is \n{}\n", commitType, title);
            if (allowedCommitTypes.contains(commitType)) {
                final String messageWithFooter = removeFooter(matcher.group(3).trim(), footerPattern);
                return String.format("* %s: **%s:** %s", commitType, title, messageWithFooter);
            }
            logger.debug("Skipping commit message with subject \"{}\" as it has a type of {}", title, commitType);
        } else {
            logger.warn("Could not parse commit message, ignoring. Please add manually to the CHANGELOG.md if it is " +
                    "required! The message:\n{}", commitMessage);
        }
        return null;
    }

    /**
     * Removes the footer from a given message.
     *
     * @param message       the given message.
     * @param footerPattern the compiled pattern for the footer.
     * @return the message without the footer.
     */
    private String removeFooter(final String message, final Pattern footerPattern) {
        final Matcher matcher = footerPattern.matcher(message);
        return matcher.replaceAll("").trim();
    }

    /**
     * Gets the CHANGELOG.md of this repo.
     *
     * @return the CHANGELOG.md.
     */
    private File getChangeLogFile() {
        return new File("./CHANGELOG.md");
    }
}
