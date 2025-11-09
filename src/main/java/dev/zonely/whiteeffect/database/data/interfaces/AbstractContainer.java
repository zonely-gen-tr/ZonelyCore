package dev.zonely.whiteeffect.database.data.interfaces;

import dev.zonely.whiteeffect.database.data.DataContainer;

public abstract class AbstractContainer {
   protected DataContainer dataContainer;

   public AbstractContainer(DataContainer dataContainer) {
      this.dataContainer = dataContainer;
   }

   public void gc() {
      this.dataContainer = null;
   }
}
