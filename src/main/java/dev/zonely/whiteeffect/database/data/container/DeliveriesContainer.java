package dev.zonely.whiteeffect.database.data.container;

import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.data.interfaces.AbstractContainer;
import org.json.simple.JSONObject;

public class DeliveriesContainer extends AbstractContainer {
   public DeliveriesContainer(DataContainer dataContainer) {
      super(dataContainer);
   }

   public void claimDelivery(long id, long time) {
      JSONObject deliveries = this.dataContainer.getAsJsonObject();
      deliveries.put(String.valueOf(id), System.currentTimeMillis() + time);
      this.dataContainer.set(deliveries.toString());
      deliveries.clear();
   }

   public long getClaimTime(long id) {
      JSONObject deliveries = this.dataContainer.getAsJsonObject();
      long time = deliveries.containsKey(String.valueOf(id)) ? (Long)deliveries.get(String.valueOf(id)) : 0L;
      deliveries.clear();
      return time;
   }

   public boolean alreadyClaimed(long id) {
      return this.getClaimTime(id) > System.currentTimeMillis();
   }
}
