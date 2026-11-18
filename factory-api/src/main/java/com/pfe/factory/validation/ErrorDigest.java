package com.pfe.factory.validation;

import com.pfe.factory.util.Hashes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extrait borné et normalisé d'une erreur de compilation/de test, sans chemin absolu. */
public record ErrorDigest(
        String signature,
        String checkType,
        List<String> failedTests,
        List<String> files,
        List<String> messages,
        String rawTail) {

    private static final Pattern FILE = Pattern.compile("([\\w./-]+\\.(?:js|ts|java|html))");

    public static ErrorDigest from(String stdout, String stderr, String repoRoot, String checkType) {
        String safeRoot = repoRoot == null ? "" : repoRoot;
        String text = (stdout == null ? "" : stdout) + "\n" + (stderr == null ? "" : stderr);
        text = text.replace(safeRoot, "<repo>").replace("\\", "/");

        List<String> tests = new ArrayList<>();
        Matcher tm = Pattern.compile("(?:✖|<<< FAILURE!|not ok)\\s*([^\\n]+)").matcher(text);
        while (tm.find() && tests.size() < 10) tests.add(tm.group(1).trim());

        List<String> files = new ArrayList<>();
        Matcher fm = FILE.matcher(text);
        while (fm.find() && files.size() < 10) {
            String f = fm.group(1);
            if (!files.contains(f)) files.add(f);
        }

        List<String> messages = Arrays.stream(text.split("\\R"))
                .filter(l -> l.contains("[ERROR]") || l.toLowerCase().contains("error")
                        || l.contains("FAILURE"))
                .limit(5)
                .map(l -> l.length() > 240 ? l.substring(0, 240) : l)
                .toList();

        String signature = Hashes.sha256(checkType + "|" + String.join("|", tests)
                + "|" + String.join("|", messages)).substring(0, 12);
        String tail = text.length() > 4000 ? text.substring(text.length() - 4000) : text;

        return new ErrorDigest(signature, checkType, tests, files, messages, tail);
    }
}
