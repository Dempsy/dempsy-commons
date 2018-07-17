package net.dempsy.util;

public interface QuietCloseable extends AutoCloseable {

   @Override
   public void close();

}
