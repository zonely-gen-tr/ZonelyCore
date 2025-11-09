package dev.zonely.whiteeffect.reflection.acessors;

import dev.zonely.whiteeffect.reflection.Accessors;

import java.lang.reflect.Field;

public class FieldAccessor<TField> {
   private final Field handle;

   public FieldAccessor(Field field) {
      this(field, false);
   }

   public FieldAccessor(Field field, boolean forceAccess) {
      this.handle = field;
      if (forceAccess) {
         Accessors.setAccessible(field);
      }

   }

   public TField get(Object target) {
      try {
         return (TField) handle.get(target);
      } catch (ReflectiveOperationException ex) {
         throw new RuntimeException("Cannot access field.", ex);
      }
   }

   public void set(Object target, TField value) {
      try {
         this.handle.set(target, value);
      } catch (ReflectiveOperationException var4) {
         throw new RuntimeException("Cannot access field.", var4);
      }
   }

   public boolean hasField(Object target) {
      return target != null && this.handle.getDeclaringClass().equals(target.getClass());
   }

   public Field getHandle() {
      return this.handle;
   }

   public String toString() {
      return "FieldAccessor[class=" + this.handle.getDeclaringClass().getName() + ", name=" + this.handle.getName() + ", type=" + this.handle.getType() + "]";
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (obj instanceof FieldAccessor) {
         FieldAccessor<?> other = (FieldAccessor)obj;
         return other.handle.equals(this.handle);
      } else {
         return false;
      }
   }

   public int hashCode() {
      return this.handle.hashCode();
   }
}
