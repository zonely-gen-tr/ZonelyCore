package dev.zonely.whiteeffect.utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
   private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###");
   private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)(\u00a7)[0-9A-FK-ORX]");
   private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)\u00a7x(?:\u00a7[0-9A-F]){6}");
   private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)#[0-9A-F]{6}");

   public static String formatNumber(int number) {
      return DECIMAL_FORMAT.format((long)number);
   }

   public static String formatNumber(long number) {
      return DECIMAL_FORMAT.format(number);
   }

   public static String formatNumber(double number) {
      return DECIMAL_FORMAT.format(number);
   }

   public static String stripColors(String input) {
      if (input == null) {
         return null;
      }
      String withoutHex = LEGACY_HEX_PATTERN.matcher(input).replaceAll("");
      return COLOR_PATTERN.matcher(withoutHex).replaceAll("");
   }

   public static String formatColors(String textToFormat) {
      return translateAlternateColorCodes('&', textToFormat);
   }

   public static String deformatColors(String textToDeFormat) {
      if (textToDeFormat == null) {
         return null;
      }

      Matcher hexMatcher = LEGACY_HEX_PATTERN.matcher(textToDeFormat);
      StringBuffer buffer = new StringBuffer();
      while(hexMatcher.find()) {
         String sequence = hexMatcher.group();
         StringBuilder hex = new StringBuilder();
         for(int i = 2; i < sequence.length(); i += 2) {
            hex.append(sequence.charAt(i + 1));
         }
         hexMatcher.appendReplacement(buffer, Matcher.quoteReplacement("&#" + hex));
      }
      hexMatcher.appendTail(buffer);
      textToDeFormat = buffer.toString();

      String color;
      for(Matcher matcher = COLOR_PATTERN.matcher(textToDeFormat); matcher.find(); textToDeFormat = textToDeFormat.replaceFirst(Pattern.quote(color), Matcher.quoteReplacement("&" + color.substring(1)))) {
         color = matcher.group();
      }

      return textToDeFormat;
   }

   public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) {
      if (textToTranslate == null) {
         return null;
      }

      char[] chars = textToTranslate.toCharArray();
      StringBuilder result = new StringBuilder(chars.length * 2);

      for(int i = 0; i < chars.length; ++i) {
         char current = chars[i];
         if (current == altColorChar && i + 1 < chars.length) {
            char next = chars[i + 1];
            if (next == '#') {
               if (i + 7 < chars.length) {
                  String hex = textToTranslate.substring(i + 2, i + 8);
                  if (HEX_COLOR_PATTERN.matcher("#" + hex).matches()) {
                     result.append('\u00a7').append('x');
                     for(char hexChar : hex.toCharArray()) {
                        result.append('\u00a7').append(Character.toLowerCase(hexChar));
                     }
                     i += 7;
                     continue;
                  }
               }
            } else if (isColorCode(next)) {
               result.append('\u00a7').append(Character.toLowerCase(next));
               ++i;
               continue;
            }
         }

         result.append(current);
      }

      return result.toString();
   }

   public static String getFirstColor(String input) {
      Matcher matcher = COLOR_PATTERN.matcher(input);
      String first = "";
      if (matcher.find()) {
         first = matcher.group();
      }

      return first;
   }

   public static String getLastColor(String input) {
      Matcher matcher = COLOR_PATTERN.matcher(input);

      String last;
      for(last = ""; matcher.find(); last = matcher.group()) {
      }

      return last;
   }

   public static String repeat(String repeat, int amount) {
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < amount; ++i) {
         sb.append(repeat);
      }

      return sb.toString();
   }

   public static <T> String join(T[] array, int index, String separator) {
      StringBuilder joined = new StringBuilder();

      for(int slot = index; slot < array.length; ++slot) {
         joined.append(array[slot].toString() + (slot + 1 == array.length ? "" : separator));
      }

      return joined.toString();
   }

   public static <T> String join(T[] array, String separator) {
      return join(array, 0, separator);
   }

   public static <T> String join(Collection<T> collection, String separator) {
      return join(collection.toArray(new Object[collection.size()]), separator);
   }

   public static String[] split(String toSplit, int length) {
      return split(toSplit, length, false);
   }

   public static String capitalise(String toCapitalise) {
      StringBuilder result = new StringBuilder();
      String[] splittedString = toCapitalise.split(" ");

      for(int index = 0; index < splittedString.length; ++index) {
         String append = splittedString[index];
         result.append(append.substring(0, 1).toUpperCase() + append.substring(1).toLowerCase() + (index + 1 == splittedString.length ? "" : " "));
      }

      return result.toString();
   }

   public static String[] split(String toSplit, int length, boolean ignoreIncompleteWords) {
      StringBuilder result = new StringBuilder();
      StringBuilder current = new StringBuilder();
      char[] arr = toSplit.toCharArray();

      for(int charId = 0; charId < arr.length; ++charId) {
         char character = arr[charId];
         if (current.length() == length) {
            if (ignoreIncompleteWords) {
               result.append(current + "\n");
               current = new StringBuilder();
            } else {
               List<Character> removedChars = new ArrayList();

               for(int l = current.length() - 1; l > 0; --l) {
                  if (current.charAt(l) == ' ') {
                     current.deleteCharAt(l);
                     result.append(current + "\n");
                     Collections.reverse(removedChars);
                     current = new StringBuilder(join((Collection)removedChars, ""));
                     break;
                  }

                  removedChars.add(current.charAt(l));
                  current.deleteCharAt(l);
               }

               removedChars.clear();
               removedChars = null;
            }
         }

         current.append(current.length() == 0 && character == ' ' ? "" : character);
         if (charId + 1 == arr.length) {
            result.append(current + "\n");
         }
      }

      return result.toString().split("\n");
   }

   private static boolean isColorCode(char c) {
      return c >= '0' && c <= '9'
              || c >= 'a' && c <= 'f'
              || c >= 'k' && c <= 'o'
              || c == 'r'
              || c >= 'A' && c <= 'F'
              || c >= 'K' && c <= 'O'
              || c == 'R';
   }
}
