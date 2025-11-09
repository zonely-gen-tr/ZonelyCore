package dev.zonely.whiteeffect.plugin.config;

import dev.zonely.whiteeffect.plugin.WPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class FileUtils {
   private final WPlugin plugin;

   public FileUtils(WPlugin plugin) {
      this.plugin = plugin;
   }

   public void deleteFile(File file) {
      if (file.exists()) {
         if (file.isDirectory()) {
            Arrays.stream(file.listFiles()).forEach(this::deleteFile);
         }

         file.delete();
      }
   }

   public void copyFiles(File in, File out, String... ignore) {
      List<String> list = Arrays.asList(ignore);
      if (in.isDirectory()) {
         if (!out.exists()) {
            out.mkdirs();
         }

         File[] var5 = in.listFiles();
         int var6 = var5.length;

         for(int var7 = 0; var7 < var6; ++var7) {
            File file = var5[var7];
            if (!list.contains(file.getName())) {
               this.copyFiles(file, new File(out, file.getName()));
            }
         }
      } else {
         try {
            this.copyFile(new FileInputStream(in), out);
         } catch (IOException var9) {
            this.plugin.getLogger().log(Level.WARNING, "Unexpected error while copying file \"" + out.getName() + "\": ", var9);
         }
      }

   }

   public void copyFile(InputStream input, File out) {
      FileOutputStream ou = null;

      try {
         ou = new FileOutputStream(out);
         byte[] buff = new byte[1024];

         int len;
         while((len = input.read(buff)) > 0) {
            ou.write(buff, 0, len);
         }
      } catch (IOException var14) {
         this.plugin.getLogger().log(Level.WARNING, "Unexpected error while copying file \"" + out.getName() + "\": ", var14);
      } finally {
         try {
            if (ou != null) {
               ou.close();
            }

            if (input != null) {
               input.close();
            }
         } catch (IOException var13) {
         }

      }

   }
}
