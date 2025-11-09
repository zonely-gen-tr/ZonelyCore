package dev.zonely.whiteeffect.reflection;

import dev.zonely.whiteeffect.reflection.acessors.ConstructorAccessor;
import dev.zonely.whiteeffect.reflection.acessors.FieldAccessor;
import dev.zonely.whiteeffect.reflection.acessors.MethodAccessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

@SuppressWarnings({ "rawtypes" })
public class Accessors {

   private Accessors() {
   }

   public static void setAccessible(Field field) {
      try {
         if (!field.isAccessible()) {
            field.setAccessible(true);
         }
      } catch (Throwable ignored) { }

      try {
         int mods = field.getModifiers();
         if ((mods & Modifier.FINAL) != 0) {
            try {
               Field modifiersField = Field.class.getDeclaredField("modifiers");
               if (!modifiersField.isAccessible()) modifiersField.setAccessible(true);
               modifiersField.setInt(field, mods & ~Modifier.FINAL);
            } catch (NoSuchFieldException nsfe) {
            } catch (Throwable ignored) {
            }
         }
      } catch (Throwable ignored) { }
   }

   public static void setFieldValue(Field field, Object target, Object value) {
      new FieldAccessor<>(field, true).set(target, value);
   }

   public static Object getFieldValue(Field field, Object target) {
      return new FieldAccessor<>(field, true).get(target);
   }

   public static FieldAccessor<Object> getField(Class clazz, int index) {
      return getField(clazz, index, null);
   }

   public static FieldAccessor<Object> getField(Class clazz, String fieldName) {
      return getField(clazz, fieldName, null);
   }

   public static <T> FieldAccessor<T> getField(Class clazz, int index, Class<T> fieldType) {
      return getField(clazz, null, index, fieldType);
   }

   public static <T> FieldAccessor<T> getField(Class clazz, String fieldName, Class<T> fieldType) {
      return getField(clazz, fieldName, 0, fieldType);
   }

   public static <T> FieldAccessor<T> getField(Class clazz, String fieldName, int index, Class<T> fieldType) {
      int indexCopy = index;
      for (final Field field : clazz.getDeclaredFields()) {
         if ((fieldName == null || fieldName.equals(field.getName()))
               && (fieldType == null || fieldType.equals(field.getType())) && index-- == 0) {
            return new FieldAccessor<>(field, true);
         }
      }

      String message = " with index " + indexCopy;
      if (fieldName != null) {
         message += " and name " + fieldName;
      }
      if (fieldType != null) {
         message += " and type " + fieldType;
      }

      throw new IllegalArgumentException("Cannot find field " + message);
   }

   public static MethodAccessor getMethod(Class clazz, String methodName) {
      return getMethod(clazz, null, methodName, (Class[]) null);
   }

   public static MethodAccessor getMethod(Class clazz, int index) {
      return getMethod(clazz, null, index, (Class[]) null);
   }

   public static MethodAccessor getMethod(Class clazz, String methodName, Class... parameters) {
      return getMethod(clazz, null, methodName, parameters);
   }

   public static MethodAccessor getMethod(Class clazz, int index, Class... parameters) {
      return getMethod(clazz, null, index, parameters);
   }

   public static MethodAccessor getMethod(Class clazz, Class returnType, String methodName, Class... parameters) {
      return getMethod(clazz, 0, returnType, methodName, parameters);
   }

   public static MethodAccessor getMethod(Class clazz, Class returnType, int index, Class... parameters) {
      return getMethod(clazz, index, returnType, null, parameters);
   }

   public static MethodAccessor getMethod(Class clazz, int index, Class returnType, String methodName,
         Class... parameters) {
      int indexCopy = index;
      for (final Method method : clazz.getMethods()) {
         if ((methodName == null || method.getName().equals(methodName))
               && (returnType == null || method.getReturnType().equals(returnType)) && (parameters == null || Arrays
                     .equals(method.getParameterTypes(), parameters))
               && index-- == 0) {
            return new MethodAccessor(method, true);
         }
      }

      String message = " with index " + indexCopy;
      if (methodName != null) {
         message += " and name " + methodName;
      }
      if (returnType != null) {
         message += " and returntype " + returnType;
      }
      if (parameters != null && parameters.length > 0) {
         message += " and parameters " + Arrays.asList(parameters);
      }
      throw new IllegalArgumentException("Cannot find method " + message);
   }

   public static <T> ConstructorAccessor<T> getConstructor(Class<T> clazz, int index) {
      return getConstructor(clazz, index, (Class[]) null);
   }

   public static <T> ConstructorAccessor<T> getConstructor(Class<T> clazz, Class... parameters) {
      return getConstructor(clazz, 0, parameters);
   }

   @SuppressWarnings("unchecked")
   public static <T> ConstructorAccessor<T> getConstructor(Class<T> clazz, int index, Class... parameters) {
      int indexCopy = index;
      for (final Constructor<?> constructor : clazz.getDeclaredConstructors()) {
         if ((parameters == null || Arrays.equals(constructor.getParameterTypes(), parameters)) && index-- == 0) {
            return new ConstructorAccessor<>((Constructor<T>) constructor, true);
         }
      }

      throw new IllegalArgumentException("Cannot find constructor for class " + clazz + " with index " + indexCopy);
   }
}
