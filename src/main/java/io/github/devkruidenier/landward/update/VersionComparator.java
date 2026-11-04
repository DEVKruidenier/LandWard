package io.github.devkruidenier.landward.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VersionComparator {

    private static final Pattern VERSION = Pattern.compile("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    private VersionComparator() {
    }

    static boolean isNewer(String candidate, String current) {
        int[] candidateParts = parse(candidate);
        int[] currentParts = parse(current);
        for (int index = 0; index < candidateParts.length; index++) {
            if (candidateParts[index] != currentParts[index]) {
                return candidateParts[index] > currentParts[index];
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        Matcher matcher = VERSION.matcher(version == null ? "" : version.trim());
        if (!matcher.find()) {
            return new int[]{0, 0, 0};
        }
        return new int[]{part(matcher, 1), part(matcher, 2), part(matcher, 3)};
    }

    private static int part(Matcher matcher, int group) {
        String value = matcher.group(group);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
