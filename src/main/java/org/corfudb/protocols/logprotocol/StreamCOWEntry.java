package org.corfudb.protocols.logprotocol;


import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.corfudb.runtime.CorfuRuntime;

import java.util.UUID;

/**
 * Created by mwei on 2/18/16.
 */
@ToString(callSuper = true)
@NoArgsConstructor
public class StreamCOWEntry extends LogEntry {

    @Getter
    UUID originalStream;

    @Getter
    long followUntil;

    public StreamCOWEntry(UUID originalStream, long followUntil) {
        super(LogEntryType.STREAM_COW);
        this.originalStream = originalStream;
        this.followUntil = followUntil;
    }

    /**
     * Serialize the message into the given bytebuffer.
     *
     * @param buffer The buffer to serialize to.
     */
    @Override
    public void serialize(ByteBuf buffer) {
        super.serialize(buffer);
        buffer.writeLong(originalStream.getMostSignificantBits());
        buffer.writeLong(originalStream.getLeastSignificantBits());
        buffer.writeLong(followUntil);
    }

    /**
     * Parse the rest of the message from the buffer. Classes that extend CorfuMsg
     * should parse their fields in this method.
     *
     * @param buffer
     */
    @Override
    @SuppressWarnings("unchecked")
    public void deserializeBuffer(ByteBuf buffer, CorfuRuntime rt) {
        super.deserializeBuffer(buffer, rt);
        this.originalStream = new UUID(buffer.readLong(), buffer.readLong());
        this.followUntil = buffer.readLong();
    }
}
