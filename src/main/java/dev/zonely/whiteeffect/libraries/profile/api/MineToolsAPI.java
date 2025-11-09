package dev.zonely.whiteeffect.libraries.profile.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zonely.whiteeffect.libraries.profile.Mojang;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class MineToolsAPI extends Mojang {
   private boolean response;

   public String fetchId(String name) {
      this.response = false;

      try {
         URLConnection conn = (new URL("https://api.minetools.eu/uuid/" + name)).openConnection();
         conn.setConnectTimeout(5000);
         BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         this.response = true;
         StringBuilder builder = new StringBuilder();

         String read;
         while((read = reader.readLine()) != null) {
            builder.append(read);
         }

         if (builder.toString().contains("Invalid UUID or Nickname!")) {
            this.response = true;
            return null;
         } else if (builder.toString().contains("\"status\": \"OK\"")) {
            this.response = true;
            return (new JsonParser()).parse(builder.toString()).getAsJsonObject().get("id").getAsString();
         } else {
            return null;
         }
      } catch (Exception var6) {
         return null;
      }
   }

   public String fetchSkinProperty(String id) {
      this.response = false;

      try {
         URLConnection conn = (new URL("https://api.minetools.eu/profile/" + id)).openConnection();
         conn.setConnectTimeout(5000);
         BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         this.response = true;
         StringBuilder builder = new StringBuilder();

         String read;
         while((read = reader.readLine()) != null) {
            builder.append(read);
         }

         String property = null;
         if (builder.toString().contains("Invalid UUID!")) {
            this.response = true;
            return null;
         } else {
            JsonObject properties = (new JsonParser()).parse(builder.toString()).getAsJsonObject().get("raw").getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
            String name = properties.get("name").getAsString();
            String value = properties.get("value").getAsString();
            String signature = properties.get("signature").getAsString();
            property = name + " : " + value + " : " + signature;
            return property;
         }
      } catch (Exception var11) {
         return null;
      }
   }

   public boolean getResponse() {
      return this.response;
   }
}
