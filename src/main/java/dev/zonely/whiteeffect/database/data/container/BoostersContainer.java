package dev.zonely.whiteeffect.database.data.container;

import dev.zonely.whiteeffect.booster.Booster;
import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.data.interfaces.AbstractContainer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class BoostersContainer extends AbstractContainer {
   public BoostersContainer(DataContainer dataContainer) {
      super(dataContainer);
      JSONObject boosters = this.dataContainer.getAsJsonObject();
      if (!boosters.containsKey("0")) {
         boosters.put("0", new JSONArray());
         boosters.put("1", new JSONArray());
         boosters.put("2", "none");
      }

      this.dataContainer.set(boosters.toString());
   }

   public boolean enable(Booster booster) {
      if (this.getEnabled() != null) {
         return false;
      } else {
         this.removeBooster(Booster.BoosterType.PRIVATE, booster);
         JSONObject boosters = this.dataContainer.getAsJsonObject();
         boosters.put("2", booster.getMultiplier() + ":" + (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(booster.getHours())));
         this.dataContainer.set(boosters.toString());
         boosters.clear();
         return true;
      }
   }

   public void addBooster(Booster.BoosterType type, double multiplier, long hours) {
      JSONObject boosters = this.dataContainer.getAsJsonObject();
      ((JSONArray)boosters.get(String.valueOf(type.ordinal()))).add(multiplier + ":" + hours);
      this.dataContainer.set(boosters.toString());
      boosters.clear();
   }

   public void removeBooster(Booster.BoosterType type, Booster booster) {
      JSONObject boosters = this.dataContainer.getAsJsonObject();
      ((JSONArray)boosters.get(String.valueOf(type.ordinal()))).remove(booster.getMultiplier() + ":" + booster.getHours());
      this.dataContainer.set(boosters.toString());
      boosters.clear();
   }

   public List<Booster> getBoosters(Booster.BoosterType type) {
      List<Booster> list = new ArrayList();
      JSONArray boosters = (JSONArray)this.dataContainer.getAsJsonObject().get(String.valueOf(type.ordinal()));
      Iterator var4 = boosters.iterator();

      while(var4.hasNext()) {
         Object obj = var4.next();
         if (obj instanceof String) {
            list.add(Booster.parseBooster((String)obj));
         }
      }

      return list;
   }

   public String getEnabled() {
      JSONObject boosters = this.dataContainer.getAsJsonObject();
      String current = (String)boosters.get("2");
      if (current.equals("none")) {
         boosters.clear();
         return null;
      } else {
         String[] splitted = current.split(":");
         double multiplier = Double.parseDouble(splitted[0]);
         long expires = Long.parseLong(splitted[1]);
         if (expires > System.currentTimeMillis()) {
            boosters.clear();
            return multiplier + ":" + expires;
         } else {
            boosters.put("2", "none");
            this.dataContainer.set(boosters.toString());
            boosters.clear();
            return null;
         }
      }
   }
}
