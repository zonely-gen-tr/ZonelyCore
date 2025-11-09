package dev.zonely.whiteeffect.reflection.acessors;

import java.lang.reflect.Constructor;

public class ConstructorAccessor<T> {
   private final Constructor<T> handle;

   public ConstructorAccessor(Constructor<T> constructor) {
      this(constructor, false);
   }

   public ConstructorAccessor(Constructor<T> constructor, boolean forceAccess) {
      this.handle = constructor;
      if (forceAccess) {
         constructor.setAccessible(true);
      }

   }

   public T newInstance(Object... args) {
      try {
         return this.handle.newInstance(args);
      } catch (ReflectiveOperationException var3) {
         throw new RuntimeException("Cannot invoke constructor.", var3);
      }
   }

   public boolean hasConstructor(Object target) {
      return target != null && this.handle.getDeclaringClass().equals(target.getClass());
   }

   public Constructor<T> getHandle() {
      return this.handle;
   }

   public String toString() {
      return "ConstructorAccessor[class=" + this.handle.getDeclaringClass().getName() + ", params=" + this.handle.getParameterTypes().toString() + "]";
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (obj instanceof ConstructorAccessor) {
         ConstructorAccessor<?> other = (ConstructorAccessor)obj;
         return other.handle.equals(this.handle);
      } else {
         return false;
      }
   }

   public int hashCode() {
      return this.handle.hashCode();
   }
}
