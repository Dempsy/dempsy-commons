package net.dempsy.serialization.kryo;

import java.util.UUID;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.DefaultSerializers.LongSerializer;

public class KryoTestUtils {
    public static com.esotericsoftware.kryo.Serializer<UUID> uuidSerializer = new com.esotericsoftware.kryo.Serializer<UUID>(true, true) {
        LongSerializer longSerializer = new LongSerializer();

        {
            longSerializer.setImmutable(true);
            longSerializer.setAcceptsNull(false);
        }

        @Override
        public void write(final Kryo kryo, final Output output, final UUID uuid) {
            final long mostSigBits = uuid.getMostSignificantBits();
            final long leastSigBits = uuid.getLeastSignificantBits();
            longSerializer.write(kryo, output, mostSigBits);
            longSerializer.write(kryo, output, leastSigBits);
        }

        @Override
        public UUID read(final Kryo kryo, final Input input, final Class<? extends UUID> type) {
            final long mostSigBits = longSerializer.read(kryo, input, long.class);
            final long leastSigBits = longSerializer.read(kryo, input, long.class);
            return new UUID(mostSigBits, leastSigBits);
        }
    };

    public static KryoOptimizer defaultMock3Optimizer = new KryoOptimizer() {
        @Override
        public void preRegister(final Kryo kryo) {}

        @Override
        public void postRegister(final Kryo kryo) {
            kryo.register(UUID.class, uuidSerializer);
        }
    };

}
