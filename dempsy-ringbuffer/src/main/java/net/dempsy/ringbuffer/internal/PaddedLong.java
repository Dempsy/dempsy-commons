package net.dempsy.ringbuffer.internal;

class LhsPadding {
   protected long p1, p2, p3, p4, p5, p6, p7;
}

class Value extends LhsPadding {
   public long value;
}

public final class PaddedLong extends Value {
   protected long p9, p10, p11, p12, p13, p14, p15;

   public PaddedLong(final long value) {
      this.value = value;
   }

   public long get() {
      return value;
   }

   public void set(final long value) {
      this.value = value;
   }
}
