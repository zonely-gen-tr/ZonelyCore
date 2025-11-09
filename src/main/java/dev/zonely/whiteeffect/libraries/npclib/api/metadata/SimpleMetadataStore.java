package dev.zonely.whiteeffect.libraries.npclib.api.metadata;

import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.Map;

public class SimpleMetadataStore implements MetadataStore {

   private final Map<String, Object> metadata = new HashMap<>();

   private void checkPrimitive(Object data) {
      Preconditions.checkNotNull(data, "Veri hatasi.");
      boolean isPrimitive = data instanceof Boolean || data instanceof String || data instanceof Number;
      if (!isPrimitive) {
         throw new IllegalArgumentException("Veri hatasi.");
      }
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T get(String key) {
      return (T) metadata.get(key);
   }

   @Override
   public <T> T get(String key, T def) {
      T t = get(key);
      if (t == null) {
         set(key, def);
         return def;
      }

      return t;
   }

   @Override
   public boolean has(String key) {
      Preconditions.checkNotNull(key, "Anahtar yanlis.");
      return metadata.containsKey(key);
   }

   @Override
   public void remove(String key) {
      metadata.remove(key);
   }

   @Override
   public void set(String key, Object data) {
      Preconditions.checkNotNull(key, "Anahtar yanlis.");
      if (data == null) {
         remove(key);
      } else {
         checkPrimitive(data);
         metadata.put(key, data);
      }
   }
}
