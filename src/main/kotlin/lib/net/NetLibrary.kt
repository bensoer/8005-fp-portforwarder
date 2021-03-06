package lib.net

import lib.net.rw.IReadWritableChannel
import tools.Logger
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.charset.Charset


/**
 * Created by bensoer on 28/02/16.
 */

/**
 * NetLibrary is a helper library class with a number fo generic static methods for helping create SocketChannels
 * ServerSocketChannels and read/write/transfer data between these given channels
 */
class NetLibrary{

    /** Allows for Static Access **/
    companion object{
        /**
         * lowest port number on a typical machine.
         */
        val MIN_PORT:Int = 0x0000

        /**
         * highest port number on a typical machine.
         */
        val MAX_PORT:Int = 0xFFFF


        private var UDP_BIND_PORT = 6000;
        /**
         * createClientSocket creates a SocketChannel from the passed in hostNAme and portNumber parameters.
         * After which it then attempts with this channel to connect to the host passed. If the connection
         * fails this method returns null. On success, the SocketChannel is returned
         */
        fun createClientSocket(hostName:String, portNumber:Int): SocketChannel? {
            Logger.log("NetLibrary - Attemping to connect to $hostName on port $portNumber");

            var channel:SocketChannel? = null;
            try{

                val address = InetSocketAddress(hostName, portNumber);
                channel = SocketChannel.open(address);

                while(!channel.finishConnect()){
                    //we wait boys
                }

                return channel;

            }catch(uhe: UnknownHostException){
                channel?.close();
                Logger.log("NetLibrary - Unable To resolve Host Of: $hostName");
                uhe.printStackTrace();
                return null;

            }catch(ioe: IOException){
                channel?.close();
                Logger.log("NetLibrary - Unable To Access IO");
                ioe.printStackTrace();
                return null;
            }
        }

        /**
         * createClientSocket creates a SocketChannel using the passed in InetSocketAddress. It then attempts
         * to connect to the host the passed in address referres to. If the connection fails, this method returns
         * null. If it is successful, this method will return the SocketChannel
         */
        fun createClientSocket(address:InetSocketAddress): SocketChannel? {
            Logger.log("NetLibrary - Attemping to TCP connect to ${address.hostString} on port ${address.port}");
            var channel: SocketChannel? = null;
            try{

                channel = SocketChannel.open(address);
                while(!channel!!.finishConnect()){
                    //we wait boys
                }
                return channel;

            }catch(uhe: UnknownHostException){
                channel?.close();
                Logger.log("NetLibrary - Unable To resolve Host Of: ${address.hostName}");
                //uhe.printStackTrace();
                return null;

            }catch(ioe: IOException){
                channel?.close();
                Logger.log("NetLibrary - Unable To Access IO: ${address.hostName}, ${address.port}");
                //ioe.printStackTrace();
                return null;
            }
        }

        fun createUDPClientSocket(address:InetSocketAddress): DatagramChannel? {
            Logger.log("NetLibrary - Attempting to UDP connect to ${address.hostString} on port ${address.port}");

            var channel: DatagramChannel? = null;
            try{
                channel = DatagramChannel.open();
                channel.socket().bind(InetSocketAddress(UDP_BIND_PORT));
                UDP_BIND_PORT--;

                channel.connect(address);

                while(!channel.isConnected){
                    //wait for it
                }

                return channel;
            }catch(uhe: UnknownHostException){
                channel?.close();
                Logger.log("NetLibrary - Unable To resolve UDP Host Of: ${address.hostName}");
                uhe.printStackTrace();
                return null;

            }catch(ioe: IOException){
                channel?.close();
                Logger.log("NetLibrary - UDP Unable To Access IO");
                ioe.printStackTrace();
                return null;
            }

        }

        fun createUDPServerSocket(portNumber: Int): DatagramChannel?{
            Logger.log("NetLibrary - Attempting to Create A  UDP Server Socket on port $portNumber");

            var channel:DatagramChannel? = null;
            try{
                //create an address of here
                val localAddress = InetSocketAddress(portNumber); //should be wildcard bound now ?
                //create a channel
                channel = DatagramChannel.open();

                //enable reuse of address
                val socketOption = StandardSocketOptions.SO_REUSEADDR;
                channel.setOption(socketOption, true);

                //bind the address
                channel.bind(localAddress);

                //return the address
                return channel;

            }catch(ioe: IOException){
                channel?.close();
                Logger.log("NetLibrary - UDP Unable To Access IO");
                ioe.printStackTrace();
                return null;
            }


        }

        /**
         * createServerSocket creates a ServerSocketChannel using the passed in port number to bind to. If the
         * port is unable to be bound to, the function will return null. If it is successful it will return the
         * ServerSocketChannel
         */
        fun createServerSocket(portNumber: Int): ServerSocketChannel? {
            Logger.log("NetLibrary - Attempting to Create A TCP Server Socket on port $portNumber");

            var channel:ServerSocketChannel? = null;
            try{

                //create an address of here
                val localAddress = InetSocketAddress(portNumber); //should be wildcard bound now ?
                //create a channel
                channel = ServerSocketChannel.open();

                //enable reuse of address
                val socketOption = StandardSocketOptions.SO_REUSEADDR;
                channel.setOption(socketOption, true);

                //bind the address
                channel.bind(localAddress);

                //return the address
                return channel;

            }catch(ioe: IOException){
                channel?.close();
                Logger.log("NetLibrary - Unable To Access IO");
                ioe.printStackTrace();
                return null;
            }
        }

        /**
         * readFromSocket is a helper method that reads data from the passed in SocketChannel using the passed
         * in ByteBuffer. The ByteBuffer is responsible for specifying how much to read from the channel before
         * returning it. The read function will hang until data is read from the channel. After reading from the
         * channel readFromSocket will return a SocketRead data object containing the ByteBuffer in read mode, and
         * the number of bytes that were read from the channel
         */
        fun readFromSocket(channel: IReadWritableChannel, buffer: ByteBuffer): SocketRead{

            //read in all to fill up the buffer ?
            val bytesRead:Int = channel.read(buffer);

            //flip it cuz why not
            buffer.flip();

            //convert it to a string for this example ?
            /*val bytes: ByteArray = buffer.array();
            val string:String = String(bytes, Charset.forName("UTF-8"));

            buffer.clear();
            return string;*/

            return SocketRead(buffer, bytesRead, channel.getSourceAddress());
        }

        /**
         * writeToSocket writes the content from the passed in ByteBuffer into the passed in SocketChannel.
         * The method will block until all data in the buffer has been written. Note this method assumes the passed
         * in ByteBuffer is in read mode
         */
        fun writeToSocket(channel: IReadWritableChannel, buffer: ByteBuffer){

            //buffer.flip();
            while(buffer.hasRemaining()){
                channel.write(buffer);
            }
        }

        /**
         * transferDataFromChannels is a helper method that transfers data from the passed in sourceChannel
         * to the destinationChannel. The method supplies its own ByteBuffer with 1024 bytes of space in which
         * it will read from the sourceChannel until empty or the ByteBuffer is full and then write it all to the
         * destinationChannel. This will only happen once ina  single call to the transferDataFromChannels method
         */
        fun transferDataFromChannels(sourceChannel: IReadWritableChannel, destinationChannel: IReadWritableChannel):Int{
            Logger.log("NetLibrary - Transfering Data From Channels")

            var buffer:ByteBuffer = ByteBuffer.allocate(1024);
            val readOut = NetLibrary.readFromSocket(sourceChannel,buffer);

            Logger.log("NetLibrary - Read In From Source Stream. Go This Data");
            println(readOut);

            //Logger.log("NetLibrary - Now Sending it To ${remoteAddress!!.}")
            NetLibrary.writeToSocket(destinationChannel, readOut.data);

            return readOut.bytesRead;

        }
    }

}

