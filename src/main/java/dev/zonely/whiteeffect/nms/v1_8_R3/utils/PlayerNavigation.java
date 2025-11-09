package dev.zonely.whiteeffect.nms.v1_8_R3.utils;

import dev.zonely.whiteeffect.nms.v1_8_R3.entity.EntityNPCPlayer;
import dev.zonely.whiteeffect.nms.v1_8_R3.utils.pathfinding.PlayerPathfinder;
import dev.zonely.whiteeffect.nms.v1_8_R3.utils.pathfinding.PlayerPathfinderNormal;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.v1_8_R3.AttributeInstance;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.ChunkCache;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityInsentient;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.IBlockAccess;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.NavigationAbstract;
import net.minecraft.server.v1_8_R3.PathEntity;
import net.minecraft.server.v1_8_R3.PathPoint;
import net.minecraft.server.v1_8_R3.Pathfinder;
import net.minecraft.server.v1_8_R3.Vec3D;
import net.minecraft.server.v1_8_R3.World;

public class PlayerNavigation extends NavigationAbstract {
   private final AttributeInstance a;
   private final PlayerPathfinder j;
   private final PlayerPathfinderNormal s;
   protected EntityNPCPlayer b;
   protected World c;
   protected PathEntity d;
   protected double e;
   private int f;
   private boolean fb;
   private int g;
   private Vec3D h = new Vec3D(0.0D, 0.0D, 0.0D);
   private float i = 1.0F;

   public PlayerNavigation(EntityNPCPlayer entityinsentient, World world) {
      super(getDummyInsentient(entityinsentient, world), world);
      this.b = entityinsentient;
      this.c = world;
      this.a = entityinsentient.getAttributeInstance(GenericAttributes.FOLLOW_RANGE);
      this.a.setValue(24.0D);
      this.s = new PlayerPathfinderNormal();
      this.s.a(true);
      this.j = new PlayerPathfinder(this.s);
   }

   private static EntityInsentient getDummyInsentient(EntityNPCPlayer from, World world) {
      return new EntityInsentient(world) {
      };
   }

   protected Pathfinder a() {
      return null;
   }

   public PathEntity a(BlockPosition paramBlockPosition) {
      if (!this.b()) {
         return null;
      } else {
         float f1 = this.i();
         this.c.methodProfiler.a("pathfind");
         BlockPosition localBlockPosition = new BlockPosition(this.b);
         int k = (int)(f1 + 8.0F);
         ChunkCache localChunkCache = new ChunkCache(this.c, localBlockPosition.a(-k, -k, -k), localBlockPosition.a(k, k, k), 0);
         PathEntity localPathEntity = this.j.a((IBlockAccess)localChunkCache, (Entity)this.b, (BlockPosition)paramBlockPosition, f1);
         this.c.methodProfiler.b();
         return localPathEntity;
      }
   }

   public void a(boolean paramBoolean) {
      this.s.c(paramBoolean);
   }

   public void a(double paramDouble) {
      this.e = paramDouble;
   }

   public boolean a(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4) {
      PathEntity localPathEntity = this.a((double)MathHelper.floor(paramDouble1), (double)((int)paramDouble2), (double)MathHelper.floor(paramDouble3));
      return this.a(localPathEntity, paramDouble4);
   }

   public PathEntity a(Entity paramEntity) {
      if (!this.b()) {
         return null;
      } else {
         float f1 = this.i();
         this.c.methodProfiler.a("pathfind");
         BlockPosition localBlockPosition = (new BlockPosition(this.b)).up();
         int k = (int)(f1 + 16.0F);
         ChunkCache localChunkCache = new ChunkCache(this.c, localBlockPosition.a(-k, -k, -k), localBlockPosition.a(k, k, k), 0);
         PathEntity localPathEntity = this.j.a((IBlockAccess)localChunkCache, (Entity)this.b, (Entity)paramEntity, f1);
         this.c.methodProfiler.b();
         return localPathEntity;
      }
   }

   public boolean a(Entity paramEntity, double paramDouble) {
      PathEntity localPathEntity = this.a(paramEntity);
      return localPathEntity != null ? this.a(localPathEntity, paramDouble) : false;
   }

   public void a(float paramFloat) {
      this.i = paramFloat;
   }

   private boolean a(int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6, Vec3D paramVec3D, double paramDouble1, double paramDouble2) {
      int i = paramInt1 - paramInt4 / 2;
      int j = paramInt3 - paramInt6 / 2;
      if (!this.b(i, paramInt2, j, paramInt4, paramInt5, paramInt6, paramVec3D, paramDouble1, paramDouble2)) {
         return false;
      } else {
         for(int k = i; k < i + paramInt4; ++k) {
            for(int m = j; m < j + paramInt6; ++m) {
               double d1 = (double)k + 0.5D - paramVec3D.a;
               double d2 = (double)m + 0.5D - paramVec3D.c;
               if (d1 * paramDouble1 + d2 * paramDouble2 >= 0.0D) {
                  Block localBlock = this.c.getType(new BlockPosition(k, paramInt2 - 1, m)).getBlock();
                  Material localMaterial = localBlock.getMaterial();
                  if (localMaterial == Material.AIR) {
                     return false;
                  }

                  if (localMaterial == Material.WATER && !this.b.V()) {
                     return false;
                  }

                  if (localMaterial == Material.LAVA) {
                     return false;
                  }
               }
            }
         }

         return true;
      }
   }

   public boolean a(PathEntity paramPathEntity, double paramDouble) {
      if (paramPathEntity == null) {
         this.d = null;
         return false;
      } else {
         if (!paramPathEntity.a(this.d)) {
            this.d = paramPathEntity;
         }

         this.d();
         if (this.d.d() == 0) {
            return false;
         } else {
            this.e = paramDouble;
            Vec3D localVec3D = this.c();
            this.g = this.f;
            this.h = localVec3D;
            return true;
         }
      }
   }

   protected void a(Vec3D paramVec3D) {
      if (this.f - this.g > 100) {
         if (paramVec3D.distanceSquared(this.h) < 2.25D) {
            this.n();
         }

         this.g = this.f;
         this.h = paramVec3D;
      }

   }

   protected boolean a(Vec3D paramVec3D1, Vec3D paramVec3D2, int paramInt1, int paramInt2, int paramInt3) {
      int i = MathHelper.floor(paramVec3D1.a);
      int j = MathHelper.floor(paramVec3D1.c);
      double d1 = paramVec3D2.a - paramVec3D1.a;
      double d2 = paramVec3D2.c - paramVec3D1.c;
      double d3 = d1 * d1 + d2 * d2;
      if (d3 < 1.0E-8D) {
         return false;
      } else {
         double d4 = 1.0D / Math.sqrt(d3);
         d1 *= d4;
         d2 *= d4;
         paramInt1 += 2;
         paramInt3 += 2;
         if (!this.a(i, (int)paramVec3D1.b, j, paramInt1, paramInt2, paramInt3, paramVec3D1, d1, d2)) {
            return false;
         } else {
            paramInt1 -= 2;
            paramInt3 -= 2;
            double d5 = 1.0D / Math.abs(d1);
            double d6 = 1.0D / Math.abs(d2);
            double d7 = (double)(i * 1) - paramVec3D1.a;
            double d8 = (double)(j * 1) - paramVec3D1.c;
            if (d1 >= 0.0D) {
               ++d7;
            }

            if (d2 >= 0.0D) {
               ++d8;
            }

            d7 /= d1;
            d8 /= d2;
            int k = d1 < 0.0D ? -1 : 1;
            int m = d2 < 0.0D ? -1 : 1;
            int n = MathHelper.floor(paramVec3D2.a);
            int i1 = MathHelper.floor(paramVec3D2.c);
            int i2 = n - i;
            int i3 = i1 - j;

            do {
               if (i2 * k <= 0 && i3 * m <= 0) {
                  return true;
               }

               if (d7 < d8) {
                  d7 += d5;
                  i += k;
                  i2 = n - i;
               } else {
                  d8 += d6;
                  j += m;
                  i3 = i1 - j;
               }
            } while(this.a(i, (int)paramVec3D1.b, j, paramInt1, paramInt2, paramInt3, paramVec3D1, d1, d2));

            return false;
         }
      }
   }

   protected boolean b() {
      return this.b.onGround || this.h() && this.o() || this.b.au();
   }

   public void b(boolean paramBoolean) {
      this.s.b(paramBoolean);
   }

   private boolean b(int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6, Vec3D paramVec3D, double paramDouble1, double paramDouble2) {
      Iterator var12 = BlockPosition.a(new BlockPosition(paramInt1, paramInt2, paramInt3), new BlockPosition(paramInt1 + paramInt4 - 1, paramInt2 + paramInt5 - 1, paramInt3 + paramInt6 - 1)).iterator();

      while(var12.hasNext()) {
         BlockPosition localBlockPosition = (BlockPosition)var12.next();
         double d1 = (double)localBlockPosition.getX() + 0.5D - paramVec3D.a;
         double d2 = (double)localBlockPosition.getZ() + 0.5D - paramVec3D.c;
         if (d1 * paramDouble1 + d2 * paramDouble2 >= 0.0D) {
            Block localBlock = this.c.getType(localBlockPosition).getBlock();
            if (!localBlock.b(this.c, localBlockPosition)) {
               return false;
            }
         }
      }

      return true;
   }

   protected Vec3D c() {
      return new Vec3D(this.b.locX, (double)this.p(), this.b.locZ);
   }

   public void c(boolean paramBoolean) {
      this.s.a(paramBoolean);
   }

   protected void d() {
      super.d();
      if (this.fb) {
         if (this.c.i(new BlockPosition(MathHelper.floor(this.b.locX), (int)(this.b.getBoundingBox().b + 0.5D), MathHelper.floor(this.b.locZ)))) {
            return;
         }

         for(int i = 0; i < this.d.d(); ++i) {
            PathPoint localPathPoint = this.d.a(i);
            if (this.c.i(new BlockPosition(localPathPoint.a, localPathPoint.b, localPathPoint.c))) {
               this.d.b(i - 1);
               return;
            }
         }
      }

   }

   public void d(boolean paramBoolean) {
      this.s.d(paramBoolean);
   }

   public boolean e() {
      return this.s.e();
   }

   public void e(boolean paramBoolean) {
      this.fb = paramBoolean;
   }

   public boolean g() {
      return this.s.b();
   }

   public boolean h() {
      return this.s.d();
   }

   public float i() {
      return (float)this.a.getValue();
   }

   public PathEntity j() {
      return this.d;
   }

   public void k() {
      ++this.f;
      if (!this.m()) {
         Vec3D localVec3D;
         if (this.b()) {
            this.l();
         } else if (this.d != null && this.d.e() < this.d.d()) {
            localVec3D = this.c();
            Vec3D localObject = this.d.a(this.b, this.d.e());
            if (localVec3D.b > localObject.b && !this.b.onGround && MathHelper.floor(localVec3D.a) == MathHelper.floor(localObject.a) && MathHelper.floor(localVec3D.c) == MathHelper.floor(localObject.c)) {
               this.d.c(this.d.e() + 1);
            }
         }

         if (!this.m()) {
            localVec3D = this.d.a(this.b);
            if (localVec3D != null) {
               AxisAlignedBB localObject = (new AxisAlignedBB(localVec3D.a, localVec3D.b, localVec3D.c, localVec3D.a, localVec3D.b, localVec3D.c)).grow(0.5D, 0.5D, 0.5D);
               List<AxisAlignedBB> localList = this.c.getCubes(this.b, localObject.a(0.0D, -1.0D, 0.0D));
               double d1 = -1.0D;
               localObject = localObject.c(0.0D, 1.0D, 0.0D);

               AxisAlignedBB localAxisAlignedBB;
               for(Iterator var6 = localList.iterator(); var6.hasNext(); d1 = localAxisAlignedBB.b(localObject, d1)) {
                  localAxisAlignedBB = (AxisAlignedBB)var6.next();
               }

               this.b.getControllerMove().a(localVec3D.a, localVec3D.b + d1, localVec3D.c, this.e);
            }
         }
      }
   }

   protected void l() {
      Vec3D localVec3D1 = this.c();
      int k = this.d.d();

      for(int m = this.d.e(); m < this.d.d(); ++m) {
         if (this.d.a(m).b != (int)localVec3D1.b) {
            k = m;
            break;
         }
      }

      float f1 = this.b.width * this.b.width * this.i;

      int n;
      for(n = this.d.e(); n < k; ++n) {
         Vec3D localVec3D2 = this.d.a(this.b, n);
         if (localVec3D1.distanceSquared(localVec3D2) < (double)f1) {
            this.d.c(n + 1);
         }
      }

      n = MathHelper.f(this.b.width);
      int i1 = (int)this.b.length + 1;

      for(int i3 = k - 1; i3 >= this.d.e(); --i3) {
         if (this.a(localVec3D1, this.d.a(this.b, i3), n, i1, n)) {
            this.d.c(i3);
            break;
         }
      }

      this.a(localVec3D1);
   }

   public boolean m() {
      return this.d == null || this.d.b();
   }

   public void n() {
      this.d = null;
   }

   protected boolean o() {
      return this.b.V() || this.b.ab();
   }

   private int p() {
      if (this.b.V() && this.h()) {
         int i = (int)this.b.getBoundingBox().b;
         Block localBlock = this.c.getType(new BlockPosition(MathHelper.floor(this.b.locX), i, MathHelper.floor(this.b.locZ))).getBlock();
         int j = 0;

         do {
            if (localBlock != Blocks.FLOWING_WATER && localBlock != Blocks.WATER) {
               return i;
            }

            ++i;
            localBlock = this.c.getType(new BlockPosition(MathHelper.floor(this.b.locX), i, MathHelper.floor(this.b.locZ))).getBlock();
            ++j;
         } while(j <= 16);

         return (int)this.b.getBoundingBox().b;
      } else {
         return (int)(this.b.getBoundingBox().b + 0.5D);
      }
   }

   public void setRange(float pathfindingRange) {
      this.a.setValue((double)pathfindingRange);
   }
}
