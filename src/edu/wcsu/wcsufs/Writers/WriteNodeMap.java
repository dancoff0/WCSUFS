package edu.wcsu.wcsufs.Writers;

import edu.wcsu.wcsufs.FSDataStructures.FSConstants;
import edu.wcsu.wcsufs.FSDataStructures.NodeMap;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.BitSet;

public class WriteNodeMap
{
  public static boolean write( RandomAccessFile file, NodeMap nodeMap ) throws IOException
  {
    BitSet map = nodeMap.getNodeMap();

    byte[] data = new byte[ map.size() / 8 ];
    int remainingBytes = FSConstants.BLOCK_SIZE - data.length;
    //System.out.println( "WriteNodeMap: data.length = " + data.length );

    int byteNumber = data.length - 1;
    int bitNumber  = 0;
    for( int i = 0; i < map.size(); i++ )
    {
      if( map.get( i ) )
      {
        data[byteNumber] |= 1 << bitNumber;
      }

      bitNumber = (bitNumber + 1) % 8;
      if( (i + 1) % 8 == 0 ) byteNumber--;
    }
    //byte[] data = nodeMap.getNodeMap().toByteArray();
    /*
    System.out.println( "For nodeMap: length = " + nodeMap.getNodeMap().length() + ", and size = " + nodeMap.getNodeMap().size() );
    */
    file.write( data, 0, data.length );
    //for( int j = 0; j < data.length; j++ )
    //{
    //  System.out.format( "data[ %d ] = 0x%x\n", j, data[j] );
    //}

    // Now "zero out" any remaining bytes.
    byte[] padBytes = new byte[remainingBytes];
    file.write( padBytes, 0, remainingBytes );

    return true;
  }
}
