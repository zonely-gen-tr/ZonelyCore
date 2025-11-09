package dev.zonely.whiteeffect.reflection.acessors;

import java.lang.reflect.Method;

public class MethodAccessor {
   private final Method handle;

   public MethodAccessor(Method method) {
      this(method, false);
   }

   public MethodAccessor(Method method, boolean forceAccess) {
      this.handle = method;
      if (forceAccess) {
         method.setAccessible(true);
      }

   }

   public Object invoke(Object target, Object... args) {
      try {
         return this.handle.invoke(target, args);
      } catch (ReflectiveOperationException var4) {
         throw new RuntimeException("Cannot invoke method.", var4);
      }
   }

   public boolean hasMethod(Object target) {
      return target != null && this.handle.getDeclaringClass().equals(target.getClass());
   }

   public Method getHandle() {
      return this.handle;
   }

   public String toString() {
      return "MethodAccessor[class=" + this.handle.getDeclaringClass().getName() + ", name=" + this.handle.getName() + ", params=" + this.handle.getParameterTypes().toString() + "]";
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (obj instanceof MethodAccessor) {
         MethodAccessor other = (MethodAccessor)obj;
         return other.handle.equals(this.handle);
      } else {
         return false;
      }
   }

   public int hashCode() {
      return this.handle.hashCode();
   }
}
