
package env.java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.*;


/**
 * A pair of channels that implements a unidirectional pipe.
 *
 * <p> A pipe consists of a pair of channels: A writable {@link
 * Pipe.SinkChannel </code>sink<code>} channel and a readable {@link
 * Pipe.SourceChannel </code>source<code>} channel.  Once some bytes are
 * written to the sink channel they can be read from source channel in exactly
 * the order in which they were written.
 *
 * <p> Whether or not a thread writing bytes to a pipe will block until another
 * thread reads those bytes, or some previously-written bytes, from the pipe is
 * system-dependent and therefore unspecified.  Many pipe implementations will
 * buffer up to a certain number of bytes between the sink and source channels,
 * but such buffering should not be assumed.  </p>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public class Pipe {

    public static class SourceChannel extends SelectableChannel
    {
        /**
         * Constructs a new instance of this class.
         */
        protected SourceChannel() {
        }

        /**
         * Returns an operation set identifying this channel's supported
         * operations.
         *
         * <p> Pipe-source channels only support reading, so this method
         * returns {@link SelectionKey#OP_READ}.  </p>
         *
         * @return  The valid-operation set
         */
        public final int validOps() {
            return SelectionKey.OP_READ;
        }

		public void close() throws IOException {
			// TODO Auto-generated method stub
			
		}

		public int read(ByteBuffer rdummy) throws IOException {
			// TODO Auto-generated method stub
			return 0;
		}

    }

    public static class SinkChannel extends SelectableChannel
    {
        /**
         * Initializes a new instance of this class.
         */
        protected SinkChannel() {
        }

        /**
         * Returns an operation set identifying this channel's supported
         * operations.
         *
         * <p> Pipe-sink channels only support writing, so this method returns
         * {@link SelectionKey#OP_WRITE}.  </p>
         *
         * @return  The valid-operation set
         */
        public final int validOps() {
            return SelectionKey.OP_WRITE;
        }

		public void close() throws IOException {
			// TODO Auto-generated method stub
			
		}

		public int write(ByteBuffer wdummy) throws IOException {
			// TODO Auto-generated method stub
			System.out.println(wdummy);
			return wdummy.remaining();
		}

    }

    /**
     * Initializes a new instance of this class.
     */
    protected Pipe() { }

    /**
     * Returns this pipe's source channel.  </p>
     *
     * @return  This pipe's source channel
     */
    public SourceChannel source() {
    	return new SourceChannel();
    }

    /**
     * Returns this pipe's sink channel.  </p>
     *
     * @return  This pipe's sink channel
     */
    public SinkChannel sink() {
    	return new SinkChannel();
    }

    /**
     * Opens a pipe.
     *
     * <p> The new pipe is created by invoking the {@link
     * java.nio.channels.spi.SelectorProvider#openPipe openPipe} method of the
     * system-wide default {@link java.nio.channels.spi.SelectorProvider}
     * object.  </p>
     *
     * @return  A new pipe
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public static Pipe open() throws IOException {
        //return SelectorProvider.provider().openPipe();
    	return new Pipe();
    }

}