package dev.zonely.whiteeffect.store;

import dev.zonely.whiteeffect.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;

public final class StoreText {
    private StoreText() {}

    public static String plain(String value) {
        if (value == null) return "";
        String text = value.replace("\\n", "\n").replace("\r\n", "\n");
        text = text.replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6])\\s*>", "\n")
                .replaceAll("(?i)<li(?:\\s[^>]*)?>", "- ").replaceAll("<[^>]*>", "");
        String[] names = {"Ouml", "ouml", "Uuml", "uuml", "Ccedil", "ccedil", "Auml", "auml", "Eacute", "eacute", "Iacute", "iacute", "szlig", "ndash", "mdash", "hellip", "lsquo", "rsquo", "ldquo", "rdquo"};
        String[] values = {"Ö", "ö", "Ü", "ü", "Ç", "ç", "Ä", "ä", "É", "é", "Í", "í", "ß", "–", "—", "…", "‘", "’", "“", "”"};
        for (int i = 0; i < names.length; i++) text = text.replace("&" + names[i] + ";", values[i]);
        Matcher numeric = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(text);
        StringBuffer decoded = new StringBuffer();
        while (numeric.find()) {
            String replacement = numeric.group();
            try {
                String number = numeric.group(1);
                int point = Integer.parseInt(number.startsWith("x") ? number.substring(1) : number, number.startsWith("x") ? 16 : 10);
                if (Character.isValidCodePoint(point)) replacement = new String(Character.toChars(point));
            } catch (IllegalArgumentException ignored) { }
            numeric.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }
        numeric.appendTail(decoded);
        text = decoded.toString().replace("\u00a0", " ").replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        return StringUtils.formatColors(text).trim();
    }

    public static List<String> lore(String text, String baseColor, int width) {
        List<String> lines = new ArrayList<>();
        int limit = Math.max(10, width);
        for (String paragraph : plain(text).split("\n", -1)) {
            String current = baseColor;
            int visible = 0;
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isEmpty()) continue;
                int length = ChatColor.stripColor(word).codePointCount(0, ChatColor.stripColor(word).length());
                if (visible > 0 && visible + 1 + length > limit) {
                    lines.add(current);
                    current = continuation(current, baseColor);
                    visible = 0;
                }
                if (visible > 0) { current += " "; visible++; }
                // Split unbroken URLs/identifiers without splitting section-sign color pairs.
                for (int offset = 0; offset < word.length();) {
                    char c = word.charAt(offset);
                    if (c == '\u00a7' && offset + 1 < word.length()) {
                        current += word.substring(offset, offset + 2); offset += 2; continue;
                    }
                    if (visible >= limit) {
                        lines.add(current); current = continuation(current, baseColor); visible = 0;
                    }
                    int point = word.codePointAt(offset);
                    current += new String(Character.toChars(point));
                    visible++; offset += Character.charCount(point);
                }
            }
            lines.add(visible == 0 ? "" : current);
        }
        return lines;
    }

    private static String continuation(String line, String base) {
        String colors = ChatColor.getLastColors(line);
        return colors.isEmpty() ? base : colors;
    }

    public static String title(String value) {
        String plain = plain(value).replace('\n', ' ');
        if (ChatColor.stripColor(plain).length() <= 64) return plain;
        List<String> parts = lore(plain, "", 61);
        return parts.get(0) + "...";
    }
}
