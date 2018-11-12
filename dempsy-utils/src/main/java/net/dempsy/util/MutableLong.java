package net.dempsy.util;

import java.util.concurrent.atomic.AtomicLong;

public class MutableLong {
   public int val;

   AtomicLong l;

   public MutableLong(final int init) {
      this.val = init;
   }

   public long getAndIncrement() {
      final long ret = val;
      val++;
      return ret;
   }
}
