package dev.zonely.whiteeffect.database.data.container;

import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.data.interfaces.AbstractContainer;
import dev.zonely.whiteeffect.titles.Title;
import org.json.simple.JSONArray;

public class TitlesContainer extends AbstractContainer {
   public TitlesContainer(DataContainer dataContainer) {
      super(dataContainer);
   }

   public void add(Title title) {
      JSONArray titles = this.dataContainer.getAsJsonArray();
      titles.add(title.getId());
      this.dataContainer.set(titles.toString());
      titles.clear();
   }

   public boolean has(Title title) {
      return this.dataContainer.getAsJsonArray().contains(title.getId());
   }
}
