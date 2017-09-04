package com.uber.okbuck.wrapper;

import com.uber.okbuck.OkBuckGradlePlugin;
import com.uber.okbuck.core.util.FileUtil;
import com.uber.okbuck.template.config.BuckWrapper;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"WeakerAccess", "CanBeFinal", "unused", "ResultOfMethodCallIgnored", "NewApi"})
public class BuckWrapperTask extends DefaultTask {

    @Input
    public String repo;

    @Input
    public Set<String> watch;

    @Input
    public Set<String> sourceRoots;

    @Input
    public Set<String> ignoredDirs;

    private final File wrapper = getProject().file("buckw");

    @TaskAction
    void installWrapper() {
        new BuckWrapper()
                .customBuckRepo(repo)
                .watch(toWatchmanMatchers(watch))
                .sourceRoots(toWatchmanMatchers(sourceRoots))
                .ignoredDirs(toWatchmanIgnoredDirs(ignoredDirs))
                .render(wrapper);
        wrapper.setExecutable(true);

        File watchmanConfig = getProject().file(".watchmanconfig");
        if (!watchmanConfig.exists()) {
            FileUtil.copyResourceToProject("wrapper/WATCHMAN_CONFIG", getProject().file(".watchmanconfig"));
        }

        File buckVersion = getProject().file(".buckversion");
        if (!buckVersion.exists()) {
            try {
                Files.write(buckVersion.toPath(), OkBuckGradlePlugin.DEFAULT_BUCK_VERSION.getBytes());
            } catch (IOException ignored) { }
        }
    }

    @Override
    public String getDescription() {
        return "Create buck wrapper";
    }

    @Override
    public String getGroup() {
        return OkBuckGradlePlugin.GROUP;
    }

    private static String toWatchmanIgnoredDirs(Set<String> ignoredDirs) {
        if (ignoredDirs.isEmpty()) {
            return "";
        }

        String ignoreExprs = ignoredDirs
                .stream()
                .map(ignoredDir -> "            [\"dirname\", \"" + ignoredDir + "\"]")
                .collect(Collectors.joining(",\n"));

        return "        [\"not\",\n" + ignoreExprs + "\n        ]";
    }

    private static String toWatchmanMatchers(Set<String> wildcardPatterns) {
        List<String> matches = new ArrayList<>();
        List<String> suffixes = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (String wildcardPattern : wildcardPatterns) {
            String simplifiedPattern = wildcardPattern;
            if (wildcardPattern.startsWith("**/")) {
                simplifiedPattern = wildcardPattern.replaceAll("\\*\\*/", "");
            }
            String basename = FilenameUtils.getBaseName(simplifiedPattern);
            String extension = FilenameUtils.getExtension(simplifiedPattern);
            if (!simplifiedPattern.contains("/")) {
                // simple file name with no path prefixes
                if (basename.equals("*")) { // suffix
                    suffixes.add(extension);
                } else { // name
                    names.add(simplifiedPattern);
                }
            } else {
                matches.add(wildcardPattern);
            }
        }

        String matchExprs = matches
                .stream()
                .map(match -> "            [\"match\", \"" + match + "\", \"wholename\"]")
                .collect(Collectors.joining(",\n"));

        String suffixExprs = suffixes
                .stream()
                .map(suffix -> "            [\"suffix\", \"" + suffix + "\"]")
                .collect(Collectors.joining(",\n"));

        String nameExpr = names
                .stream()
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(", "));
        if (!nameExpr.isEmpty()) {
            nameExpr = "            [\"name\", [" + nameExpr + "]]";
        }

        return Stream.of(suffixExprs, nameExpr, matchExprs)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.joining(",\n"));
    }
}
