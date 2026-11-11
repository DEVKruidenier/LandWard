package io.github.devkruidenier.landward.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VersionComparator {

    private static final Pattern VERSION = Pattern.compile("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([A-Za-z0-9.-]+))?(?:\\+[A-Za-z0-9.-]+)?$");

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
        Matcher next = VERSION.matcher(candidate == null ? "" : candidate.trim());
        Matcher installed = VERSION.matcher(current == null ? "" : current.trim());
        if (!next.matches() || !installed.matches()) return false;
        String a = next.group(5), b = installed.group(5);
        if ("SNAPSHOT".equalsIgnoreCase(a) || "SNAPSHOT".equalsIgnoreCase(b)) return false;
        if (a == null || b == null) return a == null && b != null;
        Matcher aTokens = Pattern.compile("\\d+|\\D+").matcher(a);
        Matcher bTokens = Pattern.compile("\\d+|\\D+").matcher(b);
        while (aTokens.find()) {
            if (!bTokens.find()) return true;
            String x = aTokens.group(), y = bTokens.group();
            int order = Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))
                    ? new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y)) : x.compareToIgnoreCase(y);
            if (order != 0) return order > 0;
        }
        return false;
    }

    private static int[] parse(String version) {
        Matcher matcher = VERSION.matcher(version == null ? "" : version.trim());
        if (!matcher.matches()) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{part(matcher, 1), part(matcher, 2), part(matcher, 3), part(matcher, 4)};
    }

    private static int part(Matcher matcher, int group) {
        String value = matcher.group(group);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
