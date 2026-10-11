package dev.zonely.whiteeffect.hook;

public class FriendsHook {
   public interface FriendAccess {
      boolean isFriend(String player, String friend);

      boolean isBlacklisted(String player, String blacklisted);
   }

   public static final FriendAccess EMPTY = new FriendAccess() {
      @Override
      public boolean isFriend(String player, String friend) {
         return false;
      }

      @Override
      public boolean isBlacklisted(String player, String blacklisted) {
         return false;
      }
   };

   private static volatile FriendAccess access = EMPTY;

   public static void bind(FriendAccess provider) {
      access = provider == null ? EMPTY : provider;
   }

   public static boolean isFriend(String player, String friend) {
      return access.isFriend(player, friend);
   }

   public static boolean isBlacklisted(String player, String blacklisted) {
      return access.isBlacklisted(player, blacklisted);
   }
}
