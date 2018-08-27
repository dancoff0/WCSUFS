package edu.wcsu.wcsufs.Readers;

import edu.wcsu.wcsufs.FSDataStructures.NodeMap;
import edu.wcsu.wcsufs.FSDataStructures.NodeMapType;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.BitSet;

public class ReadNodeMap
{
  public static NodeMap read( RandomAccessFile inputFile, NodeMapType type, int numberOfBits ) throws IOException
  {
    // Make up a new NodeMap with the required type and size
    NodeMap nodeMap = new NodeMap( type, numberOfBits );
    BitSet map = nodeMap.getNodeMap();

    // Make up a data array
    // Make sure to round the number of bites up to a multiple of 8 before computing
    // the number of bytes.
    int numberOfDataBytes = ( 8 + numberOfBits - 1 ) / 8;
    //System.out.println( "For " + numberOfBits + ", numberofDataBytes = " + numberOfDataBytes );
    byte[] data = new byte[ numberOfDataBytes ];

    // Read in the data
    inputFile.read( data, 0, data.length );

    // Now loop over the data and fill in the corresponding bite
    int byteNumber = data.length - 1;
    int bitNumber  = 0;
    for( int i = 0; i < numberOfBits; i++ )
    {
      // Check if the bit is set in the data
      if( (data[byteNumber] & 1 << bitNumber) != 0  )
      {
        map.set( i );
      }

      bitNumber = (bitNumber + 1) % 8;
      if( (i + 1) % 8 == 0 ) byteNumber--;
    }

    // That's it
    return nodeMap;

  }
}
