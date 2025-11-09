package dev.zonely.whiteeffect.nms.v1_8_R3.utils.pathfinding;

import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockCobbleWall;
import net.minecraft.server.v1_8_R3.BlockDoor;
import net.minecraft.server.v1_8_R3.BlockFence;
import net.minecraft.server.v1_8_R3.BlockFenceGate;
import net.minecraft.server.v1_8_R3.BlockMinecartTrackAbstract;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.IBlockAccess;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.PathPoint;
import net.minecraft.server.v1_8_R3.BlockPosition.MutableBlockPosition;

public class PlayerPathfinderNormal extends PlayerPathfinderAbstract {
   private boolean f;
   private boolean g;
   private boolean h;
   private boolean i;
   private boolean j;

   public static int a(IBlockAccess paramIBlockAccess, Entity paramEntity, int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6, boolean paramBoolean1, boolean paramBoolean2, boolean paramBoolean3) {
      boolean k = false;
      BlockPosition localBlockPosition = new BlockPosition(paramEntity);
      MutableBlockPosition localMutableBlockPosition = new MutableBlockPosition();

      for(int m = paramInt1; m < paramInt1 + paramInt4; ++m) {
         for(int n = paramInt2; n < paramInt2 + paramInt5; ++n) {
            for(int i1 = paramInt3; i1 < paramInt3 + paramInt6; ++i1) {
               localMutableBlockPosition.c(m, n, i1);
               Block localBlock = paramIBlockAccess.getType(localMutableBlockPosition).getBlock();
               if (localBlock.getMaterial() != Material.AIR) {
                  if (localBlock != Blocks.TRAPDOOR && localBlock != Blocks.IRON_TRAPDOOR) {
                     if (localBlock != Blocks.FLOWING_WATER && localBlock != Blocks.WATER) {
                        if (!paramBoolean3 && localBlock instanceof BlockDoor && localBlock.getMaterial() == Material.WOOD) {
                           return 0;
                        }
                     } else {
                        if (paramBoolean1) {
                           return -1;
                        }

                        k = true;
                     }
                  } else {
                     k = true;
                  }

                  if (paramEntity.world.getType(localMutableBlockPosition).getBlock() instanceof BlockMinecartTrackAbstract) {
                     if (!(paramEntity.world.getType(localBlockPosition).getBlock() instanceof BlockMinecartTrackAbstract) && !(paramEntity.world.getType(localBlockPosition.down()).getBlock() instanceof BlockMinecartTrackAbstract)) {
                        return -3;
                     }
                  } else if (!localBlock.b(paramIBlockAccess, localMutableBlockPosition) && (!paramBoolean2 || !(localBlock instanceof BlockDoor) || localBlock.getMaterial() != Material.WOOD)) {
                     if (localBlock instanceof BlockFence || localBlock instanceof BlockFenceGate || localBlock instanceof BlockCobbleWall) {
                        return -3;
                     }

                     if (localBlock == Blocks.TRAPDOOR || localBlock == Blocks.IRON_TRAPDOOR) {
                        return -4;
                     }

                     Material localMaterial = localBlock.getMaterial();
                     if (localMaterial != Material.LAVA) {
                        return 0;
                     }

                     if (!paramEntity.ab()) {
                        return -2;
                     }
                  }
               }
            }
         }
      }

      return k ? 2 : 1;
   }

   public void a() {
      super.a();
      this.h = this.j;
   }

   public void a(boolean paramBoolean) {
      this.f = paramBoolean;
   }

   public PathPoint a(Entity paramEntity) {
      int k;
      if (this.i && paramEntity.V()) {
         k = (int)paramEntity.getBoundingBox().b;
         MutableBlockPosition localMutableBlockPosition = new MutableBlockPosition(MathHelper.floor(paramEntity.locX), k, MathHelper.floor(paramEntity.locZ));

         for(Block localBlock = this.a.getType(localMutableBlockPosition).getBlock(); localBlock == Blocks.FLOWING_WATER || localBlock == Blocks.WATER; localBlock = this.a.getType(localMutableBlockPosition).getBlock()) {
            ++k;
            localMutableBlockPosition.c(MathHelper.floor(paramEntity.locX), k, MathHelper.floor(paramEntity.locZ));
         }

         this.h = false;
      } else {
         k = MathHelper.floor(paramEntity.getBoundingBox().b + 0.5D);
      }

      return this.a(MathHelper.floor(paramEntity.getBoundingBox().a), k, MathHelper.floor(paramEntity.getBoundingBox().c));
   }

   public PathPoint a(Entity paramEntity, double paramDouble1, double paramDouble2, double paramDouble3) {
      return this.a(MathHelper.floor(paramDouble1 - (double)(paramEntity.width / 2.0F)), MathHelper.floor(paramDouble2), MathHelper.floor(paramDouble3 - (double)(paramEntity.width / 2.0F)));
   }

   private int a(Entity paramEntity, int paramInt1, int paramInt2, int paramInt3) {
      return a(this.a, paramEntity, paramInt1, paramInt2, paramInt3, this.c, this.d, this.e, this.h, this.g, this.f);
   }

   private PathPoint a(Entity paramEntity, int paramInt1, int paramInt2, int paramInt3, int paramInt4) {
      PathPoint localPathPoint = null;
      int k = this.a(paramEntity, paramInt1, paramInt2, paramInt3);
      if (k == 2) {
         return this.a(paramInt1, paramInt2, paramInt3);
      } else {
         if (k == 1) {
            localPathPoint = this.a(paramInt1, paramInt2, paramInt3);
         }

         if (localPathPoint == null && paramInt4 > 0 && k != -3 && k != -4 && this.a(paramEntity, paramInt1, paramInt2 + paramInt4, paramInt3) == 1) {
            localPathPoint = this.a(paramInt1, paramInt2 + paramInt4, paramInt3);
            paramInt2 += paramInt4;
         }

         if (localPathPoint != null) {
            int m = 0;

            int n;
            for(n = 0; paramInt2 > 0; localPathPoint = this.a(paramInt1, paramInt2, paramInt3)) {
               n = this.a(paramEntity, paramInt1, paramInt2 - 1, paramInt3);
               if (this.h && n == -1) {
                  return null;
               }

               if (n != 1) {
                  break;
               }

               if (m++ >= paramEntity.aE()) {
                  return null;
               }

               --paramInt2;
               if (paramInt2 <= 0) {
                  return null;
               }
            }

            if (n == -2) {
               return null;
            }
         }

         return localPathPoint;
      }
   }

   public void a(IBlockAccess paramIBlockAccess, Entity paramEntity) {
      super.a(paramIBlockAccess, paramEntity);
      this.j = this.h;
   }

   public int a(PathPoint[] paramArrayOfPathPoint, Entity paramEntity, PathPoint paramPathPoint1, PathPoint paramPathPoint2, float paramFloat) {
      int k = 0;
      int m = 0;
      if (this.a(paramEntity, paramPathPoint1.a, paramPathPoint1.b + 1, paramPathPoint1.c) == 1) {
         m = 1;
      }

      PathPoint localPathPoint1 = this.a(paramEntity, paramPathPoint1.a, paramPathPoint1.b, paramPathPoint1.c + 1, m);
      PathPoint localPathPoint2 = this.a(paramEntity, paramPathPoint1.a - 1, paramPathPoint1.b, paramPathPoint1.c, m);
      PathPoint localPathPoint3 = this.a(paramEntity, paramPathPoint1.a + 1, paramPathPoint1.b, paramPathPoint1.c, m);
      PathPoint localPathPoint4 = this.a(paramEntity, paramPathPoint1.a, paramPathPoint1.b, paramPathPoint1.c - 1, m);
      if (localPathPoint1 != null && !localPathPoint1.i && localPathPoint1.a(paramPathPoint2) < paramFloat) {
         paramArrayOfPathPoint[k++] = localPathPoint1;
      }

      if (localPathPoint2 != null && !localPathPoint2.i && localPathPoint2.a(paramPathPoint2) < paramFloat) {
         paramArrayOfPathPoint[k++] = localPathPoint2;
      }

      if (localPathPoint3 != null && !localPathPoint3.i && localPathPoint3.a(paramPathPoint2) < paramFloat) {
         paramArrayOfPathPoint[k++] = localPathPoint3;
      }

      if (localPathPoint4 != null && !localPathPoint4.i && localPathPoint4.a(paramPathPoint2) < paramFloat) {
         paramArrayOfPathPoint[k++] = localPathPoint4;
      }

      return k;
   }

   public boolean b() {
      return this.f;
   }

   public void b(boolean paramBoolean) {
      this.g = paramBoolean;
   }

   public void c(boolean paramBoolean) {
      this.h = paramBoolean;
   }

   public boolean d() {
      return this.i;
   }

   public void d(boolean paramBoolean) {
      this.i = paramBoolean;
   }

   public boolean e() {
      return this.h;
   }
}
