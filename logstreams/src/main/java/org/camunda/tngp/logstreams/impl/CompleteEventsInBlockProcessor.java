package org.camunda.tngp.logstreams.impl;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.spi.ReadResultProcessor;

import java.nio.ByteBuffer;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.getFragmentLength;
import static org.camunda.tngp.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class CompleteEventsInBlockProcessor implements ReadResultProcessor
{

    protected final MutableDirectBuffer directBuffer = new UnsafeBuffer(0, 0);

    @Override
    public int process(ByteBuffer byteBuffer, int readResult)
    {
        // if buffer is not full, we don't need to check the blocks
        // since we assume only complete events are written into log storage
        // and truncation can only happen if block was full read
        if (byteBuffer.position() < byteBuffer.capacity())
        {
            return readResult;
        }

        int remainingBytes = readResult;
        int position = byteBuffer.position() - readResult;
        directBuffer.wrap(byteBuffer);

        while (remainingBytes > 0)
        {
            final int messageLength = getFragmentLength(directBuffer, position);
            if (messageLength <= remainingBytes)
            {
                // logged event was completely read into the block
                // go to next event
                remainingBytes -= messageLength;
                position += messageLength;

                //check if next message length is available
                if (remainingBytes < SIZE_OF_INT)
                {
                    // clean remaining bytes
                    readResult = readResult - remainingBytes;
                    remainingBytes = 0;
                }
            }
            else
            {
                // position is more then remainingBytes, means block is more then half full
                // makes sense to write an block index so the next event can fit in the next block
                if (position >= remainingBytes)
                {
                    // remove event which does not fit and was not read completely
                    readResult = readResult - remainingBytes;
                    remainingBytes = 0;
                }
                else
                {
                    return (int) OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
                }
            }
        }
        byteBuffer.position(readResult);
        byteBuffer.limit(readResult);
        return readResult;
    }
}
