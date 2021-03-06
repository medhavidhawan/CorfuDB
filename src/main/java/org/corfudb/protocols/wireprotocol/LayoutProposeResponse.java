package org.corfudb.protocols.wireprotocol;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.corfudb.runtime.view.Layout;

/**
 * {@link org.corfudb.infrastructure.LayoutServer} response in second phase of paxos.
 * {@link org.corfudb.infrastructure.LayoutServer} will reject the proposal if the last accepted prepare
 * was not for this propose.
 *
 * Created by mdhawan on 10/24/16.
 */
@Data
@AllArgsConstructor
public class LayoutProposeResponse implements ICorfuPayload<LayoutProposeResponse> {
    private long rank;

    public LayoutProposeResponse(ByteBuf buf) {
        rank = ICorfuPayload.fromBuffer(buf, Long.class);
    }

    @Override
    public void doSerialize(ByteBuf buf) {
        ICorfuPayload.serialize(buf, rank);
    }
}