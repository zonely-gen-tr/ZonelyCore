package dev.zonely.whiteeffect.libraries.npclib.api.metadata;

public interface MetadataStore {
   <T> T get(String var1);

   <T> T get(String var1, T var2);

   boolean has(String var1);

   void remove(String var1);

   void set(String var1, Object var2);
}
