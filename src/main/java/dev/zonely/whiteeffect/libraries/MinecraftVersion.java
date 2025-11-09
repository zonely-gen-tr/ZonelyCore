package dev.zonely.whiteeffect.libraries;

import com.google.common.base.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinecraftVersion {
   private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");
   private static MinecraftVersion currentVersion;
   private static String detectedPackageToken;
   private final int major;
   private final int minor;
   private final int build;
   private final int compareId;

   public MinecraftVersion(Server server) {
      this(resolveVersion(server));
   }

   public MinecraftVersion(String version) {
      int[] numbers = this.parseVersion(version);
      this.major = numbers[0];
      this.minor = numbers[1];
      this.build = numbers[2];
      this.compareId = Integer.parseInt(this.major + "" + this.minor + "" + this.build);
   }

   public MinecraftVersion(int major, int minor, int build) {
      this.major = major;
      this.minor = minor;
      this.build = build;
      this.compareId = Integer.parseInt(major + "" + minor + "" + build);
   }

   private static String resolveVersion(Server server) {
      Class<?> type = server.getClass();

      while (type != null) {
         Package pkg = type.getPackage();
         if (pkg != null) {
            String token = findVersionToken(pkg.getName());
            if (token != null) {
               detectedPackageToken = token;
               return normalizeToken(token);
            }
         }

         type = type.getSuperclass();
      }

      detectedPackageToken = null;
      String fallback = firstNonEmpty(
         server.getBukkitVersion(),
         Bukkit.getBukkitVersion(),
         server.getVersion(),
         Bukkit.getVersion()
      );

      if (fallback != null) {
         return fallback;
      }

      throw new IllegalStateException("Unable to resolve Minecraft version from " + server.getClass().getName());
   }

   private static String normalizeToken(String token) {
      if (token == null) {
         return "";
      }

      String normalized = token.replace('_', '.');
      if (normalized.startsWith("v")) {
         normalized = normalized.substring(1);
      }

      return normalized;
   }

   private static String findVersionToken(String packageName) {
      if (packageName == null || packageName.isEmpty()) {
         return null;
      }

      String[] segments = packageName.split("\\.");

      for(String segment : segments) {
         if (segment != null && segment.startsWith("v") && segment.contains("_R")) {
            return segment;
         }
      }

      return null;
   }

   private static String firstNonEmpty(String... candidates) {
      if (candidates == null) {
         return null;
      }

      for(String candidate : candidates) {
         if (candidate == null) {
            continue;
         }

         String trimmed = candidate.trim();
         if (!trimmed.isEmpty()) {
            return trimmed;
         }
      }

      return null;
   }

   public static MinecraftVersion getCurrentVersion() {
      if (currentVersion == null) {
         currentVersion = new MinecraftVersion(Bukkit.getServer());
      }

      return currentVersion;
   }

   public boolean lowerThan(MinecraftVersion version) {
      return this.compareId <= version.getCompareId();
   }

   public boolean newerThan(MinecraftVersion version) {
      return this.compareId >= version.getCompareId();
   }

   public boolean inRange(MinecraftVersion latest, MinecraftVersion olded) {
      return this.compareId <= latest.getCompareId() && this.compareId >= olded.getCompareId();
   }

   public int getMajor() {
      return this.major;
   }

   public int getMinor() {
      return this.minor;
   }

   public int getBuild() {
      return this.build;
   }

   public int getCompareId() {
      return this.compareId;
   }

   private int[] parseVersion(String version) {
      if (version == null) {
         throw new IllegalStateException("Corrupt MC Server version: null");
      }

      String clean = version.trim();
      if (clean.isEmpty()) {
         throw new IllegalStateException("Corrupt MC Server version: " + version);
      }

      List<Integer> parts = new ArrayList<>(3);
      Matcher matcher = DIGIT_PATTERN.matcher(clean);

      while (matcher.find() && parts.size() < 3) {
         parts.add(Integer.parseInt(matcher.group(1)));
      }

      if (parts.size() < 2) {
         throw new IllegalStateException("Corrupt MC Server version: " + version);
      }

      while (parts.size() < 3) {
         parts.add(0);
      }

      return new int[]{parts.get(0), parts.get(1), parts.get(2)};
   }

   public String getVersion() {
      if (detectedPackageToken != null && !detectedPackageToken.isEmpty()) {
         return detectedPackageToken;
      }

      return String.format("v%s_%s_R%s", this.major, this.minor, this.build);
   }

   public static boolean hasLegacyPackageStructure() {
      getCurrentVersion();
      return detectedPackageToken != null;
   }

   public static String getDetectedPackageToken() {
      getCurrentVersion();
      return detectedPackageToken;
   }

   public boolean equals(Object obj) {
      if (!(obj instanceof MinecraftVersion)) {
         return false;
      } else if (obj == this) {
         return true;
      } else {
         MinecraftVersion other = (MinecraftVersion)obj;
         return this.getMajor() == other.getMajor() && this.getMinor() == other.getMinor() && this.getBuild() == other.getBuild();
      }
   }

   public int hashCode() {
      return Objects.hashCode(new Object[]{this.getMajor(), this.getMinor(), this.getBuild()});
   }

   public String toString() {
      return String.format("%s", this.getVersion());
   }
}
