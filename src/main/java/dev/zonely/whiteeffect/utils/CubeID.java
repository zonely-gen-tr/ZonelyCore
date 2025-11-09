package dev.zonely.whiteeffect.utils;

import java.util.Iterator;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public class CubeID implements Iterable<Block> {
   private final String world;
   private final int xmax;
   private int xmin;
   private final int ymax;
   private final int ymin;
   private final int zmax;
   private final int zmin;

   public CubeID(Location l1, Location l2) {
      this.world = l1.getWorld().getName();
      this.xmax = Math.max(l1.getBlockX(), l2.getBlockX());
      this.xmin = Math.min(l1.getBlockX(), l2.getBlockX());
      this.ymax = Math.max(l1.getBlockY(), l2.getBlockY());
      this.ymin = Math.min(l1.getBlockY(), l2.getBlockY());
      this.zmax = Math.max(l1.getBlockZ(), l2.getBlockZ());
      this.zmin = Math.min(l1.getBlockZ(), l2.getBlockZ());
   }

   public CubeID(String serializedCube) {
      String[] split = serializedCube.split("; ");
      this.world = split[0];
      this.xmax = Integer.parseInt(split[1]);
      this.xmin = Integer.parseInt(split[2]);
      this.ymax = Integer.parseInt(split[3]);
      this.ymin = Integer.parseInt(split[4]);
      this.zmax = Integer.parseInt(split[5]);
      this.zmin = Integer.parseInt(split[6]);
   }

   public CubeID.CubeIterator iterator() {
      return new CubeID.CubeIterator(this);
   }

   public Location getRandomLocation() {
      int x = (new Random()).nextInt(this.xmax - this.xmin) + 1;
      int y = (new Random()).nextInt(this.xmax - this.xmin) + 1;
      int z = (new Random()).nextInt(this.xmax - this.xmin) + 1;
      return new Location(Bukkit.getWorld(this.world), (double)(this.xmin + x), (double)(this.ymin + y), (double)(this.zmin + z));
   }

   public Location getCenterLocation() {
      double x = (double)this.xmin + (double)(this.xmax + 1 - this.xmin) / 2.0D;
      double z = (double)this.zmin + (double)(this.zmax + 1 - this.zmin) / 2.0D;
      World world = Bukkit.getWorld(this.world);
      return new Location(world, x, (double)(this.ymax - 10), z);
   }

   public boolean contains(Location loc) {
      return loc != null && loc.getWorld().getName().equals(this.world) && loc.getBlockX() >= this.xmin && loc.getBlockX() <= this.xmax && loc.getBlockY() >= this.ymin && loc.getBlockY() <= this.ymax && loc.getBlockZ() >= this.zmin && loc.getBlockZ() <= this.zmax;
   }

   public String getWorld() {
      return this.world;
   }

   public int getXmin() {
      return this.xmin;
   }

   public void setXmin(int xmin) {
      this.xmin = xmin;
   }

   public int getXmax() {
      return this.xmax;
   }

   public String toString() {
      return this.world + "; " + this.xmax + "; " + this.xmin + "; " + this.ymax + "; " + this.ymin + "; " + this.zmax + "; " + this.zmin;
   }

   public int getYmax() {
      return this.ymax;
   }

   public int getYmin() {
      return this.ymin;
   }

   public int getZmax() {
      return this.zmax;
   }

   public int getZmin() {
      return this.zmin;
   }

   public class CubeIterator implements Iterator<Block> {
      String world;
      CubeID cuboId;
      int baseX;
      int baseY;
      int baseZ;
      int sizeX;
      int sizeY;
      int sizeZ;
      int x;
      int y;
      int z;

      public CubeIterator(CubeID cuboId) {
         this.x = this.y = this.z = 0;
         this.baseX = CubeID.this.getXmin();
         this.baseY = CubeID.this.getYmin();
         this.baseZ = CubeID.this.getZmin();
         this.cuboId = cuboId;
         this.world = cuboId.getWorld();
         this.sizeX = Math.abs(CubeID.this.getXmax() - CubeID.this.getXmin()) + 1;
         this.sizeY = Math.abs(CubeID.this.getYmax() - CubeID.this.getYmin()) + 1;
         this.sizeZ = Math.abs(CubeID.this.getZmax() - CubeID.this.getZmin()) + 1;
      }

      public boolean hasNext() {
         return this.x < this.sizeX && this.y < this.sizeY && this.z < this.sizeZ;
      }

      public Block next() {
         Block block = Bukkit.getWorld(this.world).getBlockAt(this.baseX + this.x, this.baseY + this.y, this.baseZ + this.z);
         if (++this.x >= this.sizeX) {
            this.x = 0;
            if (++this.y >= this.sizeY) {
               this.y = 0;
               ++this.z;
            }
         }

         return block;
      }

      public void remove() {
      }
   }
}
