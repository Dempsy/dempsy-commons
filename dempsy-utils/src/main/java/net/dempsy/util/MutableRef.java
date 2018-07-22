package net.dempsy.util;

public class MutableRef<T> {
   public T ref;

   public MutableRef(final T ref) {
      this.ref = ref;
   }

   public MutableRef() {
      this(null);
   }
}
