package dev.zonely.whiteeffect.nms.v1_8_R3.utils.pathfinding;

import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.IBlockAccess;
import net.minecraft.server.v1_8_R3.IntHashMap;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.PathPoint;
import net.minecraft.server.v1_8_R3.PathfinderAbstract;

public abstract class PlayerPathfinderAbstract extends PathfinderAbstract {
   protected IBlockAccess a;
   protected IntHashMap<PathPoint> b = new IntHashMap();
   protected int c;
   protected int d;
   protected int e;

   public void a() {
   }

   public abstract PathPoint a(Entity var1);

   public abstract PathPoint a(Entity var1, double var2, double var4, double var6);

   public void a(IBlockAccess paramIBlockAccess, Entity paramEntity) {
      this.a = paramIBlockAccess;
      this.b.c();
      this.c = MathHelper.d(paramEntity.width + 1.0F);
      this.d = MathHelper.d(paramEntity.length + 1.0F);
      this.e = MathHelper.d(paramEntity.width + 1.0F);
   }

   protected PathPoint a(int paramInt1, int paramInt2, int paramInt3) {
      int i = PathPoint.a(paramInt1, paramInt2, paramInt3);
      PathPoint localPathPoint = (PathPoint)this.b.get(i);
      if (localPathPoint == null) {
         localPathPoint = new PathPoint(paramInt1, paramInt2, paramInt3);
         this.b.a(i, localPathPoint);
      }

      return localPathPoint;
   }

   public abstract int a(PathPoint[] var1, Entity var2, PathPoint var3, PathPoint var4, float var5);
}
